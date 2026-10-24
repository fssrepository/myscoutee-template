import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {stageChatFixture} from '../chat/fixture.mjs';
import {openConversation,trackChatTransport} from '../chat/assert.mjs';
import {trackCallMedia,callDiagnostics} from '../chat/call.mjs';
import {enter} from './assert.mjs';

assert(process.env.E2E_MANAGED_BROWSER_MANIFEST,'Use three visible managed sessions');
const fixture=await stageChatFixture(), browsers=[], pages=[];
const result={status:'FAIL',checks:[],fault:'A native setRemoteDescription call receives invalid SDP; no application state is patched.'};
try{
  await fixture.query(`db.users.updateOne({userId:${JSON.stringify(fixture.ids[1])}},{$set:{images:[]}})`);
  for(let i=0;i<3;i++){
    const browser=await launchChromeBrowser({baseUrl:process.env.E2E_SERVER_BASE_URL??'https://127.0.0.1',fakeMediaStream:true});browsers.push(browser);
    const page=await browser.newPage();pages.push(page);
    await page.connection.send('Page.bringToFront');
    await trackChatTransport(page);await trackCallMedia(page);
    await page.addInitScript(`(()=>{const native=RTCPeerConnection.prototype.setRemoteDescription;RTCPeerConnection.prototype.setRemoteDescription=function(description){if(window.__qaInvalidOffer&&description.type==='offer'){window.__qaInvalidOffer=false;window.__qaRejectedNativeOffer=true;return native.call(this,{type:'offer',sdp:'invalid SDP'});}return native.call(this,description);};})();`);
    if(i===1)await page.connection.send('Emulation.setUserAgentOverride',{userAgent:await page.evaluate(()=>navigator.userAgent),acceptLanguage:'hu-HU,hu'});
    await enter(page,{id:fixture.ids[i===0?0:1]});
    await openConversation(page,fixture.names[i===0?1:0]);
  }
  await pages[0].connection.send('Page.bringToFront');
  await pages[0].waitFor('call available',()=>pages[0].evaluate(()=>document.querySelector('[data-guide-control="chat-call"]')?.disabled===false));
  await pages[0].clickVisible('[data-guide-control="chat-call"]');
  for(const page of pages.slice(1))await page.waitForSelector('[data-call-state="incoming"]');
  await pages[1].clickVisible('[data-call-accept]');
  for(const page of pages.slice(0,2)){
    await page.waitForSelector('[data-call-state="active"]',{timeoutMs:45000});
    const state=await page.evaluate(()=>({mute:document.querySelector('[data-call-mute]')?.getAttribute('aria-pressed'),camera:document.querySelector('[data-call-camera]')?.getAttribute('aria-pressed'),tracks:window.__qaCallTracks.map(t=>({kind:t.kind,enabled:t.enabled,state:t.readyState}))}));
    assert.equal(state.mute,'true');assert.equal(state.camera,'true');
    assert(state.tracks.some(t=>t.kind==='audio'&&t.state==='live'));
    assert(state.tracks.filter(t=>t.kind==='audio').every(t=>!t.enabled));
    assert(!state.tracks.some(t=>t.kind==='video'&&t.state==='live'));
    result.checks.push({initialMicrophoneOff:true,initialCameraOff:true});
  }
  // The third native session creates new authenticated handshakes. Reject one
  // at the browser SDP boundary to exercise the production per-peer error path.
  for(const page of pages)await page.evaluate(()=>{window.__qaInvalidOffer=true;});
  await pages[2].connection.send('Page.bringToFront');await pages[2].clickVisible('[data-call-accept]');
  for(const page of pages)await page.waitFor('failed peer from actual negotiation',()=>page.evaluate(()=>!!document.querySelector('[data-peer-state="failed"] .call-error')),45000);
  const a=pages[0];
  await a.connection.send('Page.bringToFront');
  for(const [width,height] of [[1280,900],[390,844]]){
    await a.setViewport({width,height});
    await new Promise(resolve=>setTimeout(resolve,500));
    const sample=await a.evaluate(()=>({width:innerWidth,height:innerHeight,
      avatars:[...document.querySelectorAll('.call-avatar')].map(n=>({type:n.tagName,image:n.tagName==='IMG',loaded:n.tagName!=='IMG'||n.complete&&n.naturalWidth>0,rect:n.getBoundingClientRect().toJSON()})),
      errors:[...document.querySelectorAll('[data-peer-state="failed"] .call-error')].map(n=>{const tile=n.closest('.call-tile'),avatar=tile.querySelector('.call-avatar'),title=n.querySelector('h4');return {text:n.innerText,rect:n.getBoundingClientRect().toJSON(),tile:tile.getBoundingClientRect().toJSON(),avatar:avatar?.getBoundingClientRect().toJSON(),frame:getComputedStyle(tile).boxShadow,whiteSpace:title?getComputedStyle(title).whiteSpace:null,overflow:title?getComputedStyle(title).textOverflow:null};})}));
    assert(sample.avatars.every(n=>Math.abs(n.rect.width-60)<1&&Math.abs(n.rect.height-60)<1&&n.loaded));
    assert(sample.avatars.some(n=>n.image)&&sample.avatars.some(n=>!n.image));
    for(const error of sample.errors){
      assert.match(error.frame,/239, 68, 68/);
      assert(error.rect.top>=error.tile.top&&error.rect.bottom<=error.tile.bottom);
      assert(error.rect.bottom<=error.avatar.top+1||error.rect.top>=error.avatar.bottom-1||error.rect.right<=error.avatar.left+1||error.rect.left>=error.avatar.right-1,'Peer error must not cover the avatar: '+JSON.stringify(error));
      assert.equal(error.overflow,'ellipsis');
      assert(!/chat\.call\.error\./.test(error.text),'The real error must be translated');
    }
    result.checks.push(sample);
    const shot=await a.connection.send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false,fromSurface:false});
    await writeFile('.e2e-artifacts/functional/template-regression/call-tiles-'+width+'.png',Buffer.from(shot.data,'base64'));
  }
  await a.setViewport({width:800,height:400});await new Promise(resolve=>setTimeout(resolve,500));
  const dots=await a.evaluate(()=>({dots:document.querySelector('.call-participants .smart-list__horizontal-dots')?.getBoundingClientRect().toJSON(),frames:[...document.querySelectorAll('.call-grid')].map(n=>n.getBoundingClientRect().toJSON())}));
  assert(dots.dots?.height>0,'Three native sessions must expose real pagination dots');
  assert(dots.frames.every(frame=>dots.dots.top>=frame.bottom-1),'Dots stay below the participant frame');result.checks.push({dots});
  result.status='PASS';
}catch(error){result.error=error.stack;process.exitCode=1;
  result.diagnostics=await Promise.all(pages.map(async page=>({call:await callDiagnostics(page),text:await page.evaluate(()=>document.body.innerText.slice(-1800))})));
}finally{
  for(const page of pages){
    try{if(await page.isSelectorVisible('[data-call-hangup]'))await page.clickVisible('[data-call-hangup]');}catch{}
    await page.close();
  }
  for(const browser of browsers)await browser.close();
  result.cleanup=await fixture.close();
  await writeFile('.e2e-artifacts/functional/template-regression/call-tiles.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
}
