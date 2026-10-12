import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile);
const backend=process.env.E2E_BACKEND_CONTAINER;
if(backend)assert.equal(backend,'myscoutee-template-dev-profilemanagement');
let stopped=false,localNetworkStart=0;
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
assert(process.env.E2E_MANAGED_BROWSER_MANIFEST,'Use the versioned visible DEV checkpoint');
const browser=await launchChromeBrowser({baseUrl:'https://localhost'}),page=await browser.newPage();
const evidence={checkedAt:new Date().toISOString(),checks:[]};
const requests=new Map();
page.connection.on('Network.requestWillBeSent',e=>requests.set(e.requestId,{url:e.request.url,start:e.timestamp}));
page.connection.on('Network.responseReceived',e=>{const r=requests.get(e.requestId);if(r){r.status=e.response.status;r.elapsedMs=Math.round((e.timestamp-r.start)*1000);}});
const avatar='.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])';
async function menu(){await page.waitForSelector(avatar);await page.clickVisible(avatar);}
async function logout(){
 await menu();await page.clickVisible('[data-guide-item="settings"]');await page.clickVisible('[data-guide-item="logout"]');
 await page.waitForSelector('.dialog-panel');await page.clickVisible('.dialog-panel [data-guide-item="confirm"]');await page.waitForUrlPath('/entry');
}
async function login(role,id){
 await page.waitForSelector('[data-guide-item="explore"]:not([disabled])');
 await page.clickVisible('[data-guide-item="explore"]');
 await page.waitFor('consent or selector',async()=>await page.isSelectorVisible('app-document-viewer [role="dialog"]')||await page.isSelectorVisible('.demo-user-item'));
 if(await page.isSelectorVisible('app-document-viewer [role="dialog"]')) await page.clickByText('app-document-viewer button','Accept and continue');
 await page.waitForSelector('.demo-user-item');
 if(role!=='member'){await page.clickVisible('[data-guide-control="selector-role"] .app-menu__trigger');await page.clickVisible(`[data-guide-item="${role}"]`);}
 await page.clickVisible(`.demo-user-item[data-user-id="${id}"]`);
 await page.waitForUrlPath(role==='member'?'/home':'/'+role,30000);await page.waitForSelector(avatar);
}
async function profile(role){
 await page.navigate(role==='member'?'/home':'/'+role);await menu();
 await page.evaluate(()=>{
  window.loadingParity={ring:false,start:performance.now(),complete:null};
  window.loadingParityObserver=new MutationObserver(()=>{
   if(document.querySelector('.profile-editor-loading app-indicator[kind="spinner-ring"]'))window.loadingParity.ring=true;
   if(document.querySelector('app-profile-editor app-form-flow')&&window.loadingParity.complete===null)window.loadingParity.complete=performance.now();
  });window.loadingParityObserver.observe(document.body,{subtree:true,childList:true});
 });
 await page.clickVisible('[data-guide-field="navigation-profile"]');await page.waitForSelector('app-profile-editor app-form-flow');
 const result=await page.evaluate(()=>{window.loadingParityObserver.disconnect();const e=window.loadingParity;return{ring:e.ring,elapsedMs:Math.round(e.complete-e.start),title:document.querySelector('app-profile-editor .ui-popup__title')?.textContent};});
 assert.equal(result.ring,true,role+' must paint native lazy profile spinner');evidence.checks.push({surface:role+' profile',...result});
 await page.evaluate(()=>{
  window.saveRingSeen=false;window.saveObserver=new MutationObserver(()=>{
   if(document.querySelector('app-profile-editor [data-guide-item="profile-save"] .app-indicator--action-ring'))window.saveRingSeen=true;
  });window.saveObserver.observe(document.body,{childList:true,subtree:true,attributes:true});
 });
 await page.clickVisible('app-profile-editor [data-guide-item="profile-save"]');
 await page.waitForSelectorHidden('app-profile-editor');
 const saveRing=await page.evaluate(()=>{window.saveObserver.disconnect();return window.saveRingSeen;});
 assert.equal(saveRing,true,role+' profile save must show native action ring');
 evidence.checks.push({surface:role+' profile save',saveRing});
}
try{
 await page.setCacheDisabled(true);await page.connection.send('Network.setBypassServiceWorker',{bypass:true});await page.connection.send('Page.bringToFront');
 await page.navigate('/home');await page.waitFor('entry or workspace',async()=>await page.isSelectorVisible(avatar)||await page.isSelectorVisible('[data-guide-item="explore"]'));
 if(await page.isSelectorVisible('[data-guide-item="explore"]'))await login('member','100000000000000000000001');
 if(backend){
  await exec('docker',['stop','--time','15',backend]);stopped=true;
  await page.waitFor('automatic local failover',()=>page.evaluate(()=>!!localStorage.getItem('myscoutee-template.demo.failover.pair')),45000);
  await page.waitForSelector(avatar);localNetworkStart=page.network.length;evidence.mode='automatic frontend local';
 }else evidence.mode='http';
 await profile('member');
 await menu();await page.clickVisible('.user-menu-content [data-guide-item="chat"]');await page.waitForSelector('app-template-chat single-row article');await page.clickVisible('app-template-chat single-row article');
 await page.waitForSelector('.event-chat-popup-shell .chat-bubble');
 const indicator=await page.evaluate(()=>document.querySelector('.event-chat-popup-shell .smart-list__header-progress-shell .app-indicator--bar') ? 'bar' : null);
 assert.equal(indicator,'bar');evidence.checks.push({surface:'chat thread',indicator});
 await page.navigate('/home');await logout();
 for(const width of [320,393,768,1440]){
  await page.setViewport({width,height:950});
  const layout=await page.evaluate(()=>{
   const bounds=s=>{const r=document.querySelector(s).getBoundingClientRect();return{left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width};};
   return{brand:bounds('.brand'),login:bounds('.entry-auth-btn'),title:bounds('h1'),explore:bounds('[data-guide-item="explore"]'),scroll:document.documentElement.scrollWidth,width:innerWidth,exploreDisabled:document.querySelector('[data-guide-item="explore"]').disabled,background:getComputedStyle(document.querySelector('.landing')).backgroundColor};
  });
  assert.equal(layout.exploreDisabled,false,'Explore stays enabled offline');
  assert(layout.brand.right<=layout.login.left,'Logo and login must not overlap at '+width);
  assert(layout.explore.top>=layout.title.bottom+8,'Explore must sit below the actual title at '+width);
  assert(layout.explore.bottom<950&&layout.scroll<=width,'Landing must fit viewport');evidence.checks.push({surface:'landing',width,...layout});
 }
 await page.setViewport({width:1440,height:950});await login('operator','100000000000000000000004');
 if(!backend)await page.waitFor('background release check',()=>[...requests.values()].some(r=>r.url.endsWith('/operator/updates/releases')&&r.status===200),45000);
 const requestsBefore=[...requests.values()].filter(r=>r.url.includes('/operator/updates')).length;
 const start=Date.now();await page.clickVisible('.operator-actions [data-guide-item="updates"]');
 await page.waitForSelector('app-operator-update-popup .operator-action-popup__version',{timeoutMs:45000});
 evidence.checks.push({surface:'operator updates',preloaded:!backend,popupElapsedMs:Date.now()-start,requests:[...requests.values()].filter(r=>r.url.includes('/operator/updates')).map(({url,status,elapsedMs})=>({url,status,elapsedMs}))});
 assert.equal([...requests.values()].filter(r=>r.url.includes('/operator/updates')).length,requestsBefore,'Popup must reuse the native background request');
 await page.clickVisible('app-operator-update-popup [data-guide-control="close"]');
 await profile('operator');await logout();await login('admin','100000000000000000000003');await profile('admin');await logout();
 if(backend)assert.deepEqual(page.network.slice(localNetworkStart).filter(r=>new URL(r.url).pathname.startsWith('/api/')&&!new URL(r.url).pathname.startsWith('/api/runtime-status')),[],'Local actions must not call Java');
 assert.deepEqual(page.pageErrors,[]);evidence.errors=page.pageErrors;console.log(JSON.stringify(evidence,null,2));
}catch(error){console.error(error.stack);console.error(await page.evaluate(()=>document.body.innerText.slice(-1500)));console.error(page.pageErrors);console.error('Requests',[...requests.values()].map(({url,status,elapsedMs})=>({url,status,elapsedMs})).filter(r=>r.url.includes('/api/')));console.error('Path',await page.evaluate(()=>location.pathname));process.exitCode=1;}
finally{if(stopped)await exec('docker',['start',backend]);await writeFile(backend?'/tmp/msc122-loading-parity-local.json':'/tmp/msc122-loading-parity.json',JSON.stringify(evidence,null,2)+'\n');await writeFile('/tmp/msc122-loading-parity.png',await page.screenshot());await page.close();await browser.close();}
