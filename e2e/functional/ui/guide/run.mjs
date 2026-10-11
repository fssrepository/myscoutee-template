import assert from 'node:assert/strict';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
const local=process.argv.includes('--local'),fixture=local?null:await stageChatFixture();
const browser=await launchChromeBrowser({baseUrl:local?'http://127.0.0.1:4200':process.env.E2E_SERVER_BASE_URL??'http://localhost'}),page=await browser.newPage();
async function tour(expected){
 await page.clickVisible('app-explanation-launcher .explanation-guide-menu button');
 await page.waitForSelector('.explanation-guide-overlay__progress');
 const found=[];
 for(let index=0;index<expected.length;index++){
  const state=await page.evaluate(()=>({title:document.querySelector('.explanation-guide-overlay__card h3')?.textContent?.trim(),progress:document.querySelector('.explanation-guide-overlay__progress')?.textContent?.trim()}));
  assert.equal(state.progress,`${index+1} / ${expected.length}`,'Only rendered controls belong in the native tour');found.push(state.title);
  await page.clickVisible('.explanation-guide-overlay__next [data-guide-item="next"]');
  if(index<expected.length-1)await page.waitFor('next guide step',()=>page.evaluate(expected=>document.querySelector('.explanation-guide-overlay__progress')?.textContent?.trim()===expected,`${index+2} / ${expected.length}`));
 }
 assert.deepEqual(found.sort(),[...expected].sort());
 await page.waitForSelectorHidden('.explanation-guide-overlay__card');
}
try{
 await page.navigate('/entry');await page.waitForText('v1.0.0');await tour(['Enter the application']);
 await assertUi({page,local,actorId:fixture?.ids[0],actorName:fixture?.names[0],memberReady:async()=>{
  await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');
  await tour(['Edit profile','Example cards','Chat']);
  await page.clickVisible('[data-guide-field="navigation-profile"]');await page.waitForSelector('app-profile-editor [data-control-id="name"] input');
  await tour(['Save profile','Profile picture','Display name']);
  await page.clickVisible('app-profile-editor .ui-popup__close');
  await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('.member-navigation [data-guide-item="chat"]');await page.waitForSelector('app-template-chat single-row article');
  await tour(['Conversations']);
  await page.clickVisible('app-template-chat single-row article');await page.waitForSelector('.chat-composer');
  await tour(['Back to conversations','Write a message']);
 }});
 console.log('PASS '+(local?'local':'HTTP')+' landing, navigation, profile, conversation list and message composer guides');
}catch(error){console.error(error.stack);console.error(await page.evaluate(()=>document.body.innerText.slice(0,2500)));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
