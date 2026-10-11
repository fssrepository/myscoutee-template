import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
import {assertUi} from '../entry/assert.mjs';
import {openConversation} from '../chat/assert.mjs';
import {stageAdminFixture} from './fixture.mjs';

const local=process.argv.includes('--local');
const baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=local?null:await stageAdminFixture(baseUrl);
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
async function settings(){
 await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');
 await page.clickVisible('.settings-menu .app-menu__trigger');
 await page.waitForSelector('[data-guide-item="api"]');
 for(const id of ['reports','feedback-review','support','feedback']){
  assert.equal(await page.isSelectorVisible(`[data-guide-item="${id}"]`),false,'Omitted menu item: '+id);
 }
 assert(await page.isSelectorVisible('[data-guide-item="privacy"]'),'Common privacy settings remain');
 await page.clickVisible('.menu-close');
}
async function emptyAdmin(){
 assert.equal(await page.evaluate(()=>document.querySelectorAll('app-template-chat,app-showcase,app-admin-reports-popup,app-admin-feedback-popup,app-admin-chat-review-popup,.member-navigation').length),0,'Admin has no product pages or moderation popups');
}
try{
 await assertUi({page,local,actorId:fixture?.ids[1]??'100000000000000000000001',actorName:fixture?.names[1]??'Alex Morgan',memberReady:async()=>{
  await settings();
  await openConversation(page,fixture?.names[0]??'Sam Taylor');
  const text='Minimal member chat '+Date.now();
  await page.fillVisible('.chat-composer textarea',text);await page.clickVisible('.chat-composer .send');await page.waitForText(text);
  assert.equal(await page.isSelectorVisible('button[aria-label="Report user"]'),false,'Chat does not offer reporting');
  await page.navigate('/home?page=chat');await page.waitForText(fixture?.names[0]??'Sam Taylor');
  await page.clickByText('single-row article',fixture?.names[0]??'Sam Taylor');await page.waitForText(text);
 }});
 await assertUi({page,local,role:'admin',actorId:fixture?.adminId??'100000000000000000000003',actorName:fixture?.adminName??'Template Admin',consentExpected:false,memberReady:async()=>{
  await emptyAdmin();await settings();
  for(const query of ['page=chat','notificationTarget=reports','notificationTarget=feedback']){
   await page.navigate('/admin?'+query);await page.waitForSelector('.avatar-menu');await emptyAdmin();
  }
  assert(!page.network.some(request=>/\/api\/admin\/(dashboard|reports|feedback)(?:[/?]|$)/.test(new URL(request.url).pathname)),'The minimal shell never requests a moderation dashboard');
  const before=await page.performanceMetrics(),started=performance.now();
  await new Promise(resolve=>setTimeout(resolve,5000));
  const after=await page.performanceMetrics();
  console.log('Idle renderer sample',JSON.stringify({seconds:(performance.now()-started)/1000,taskSeconds:after.TaskDuration-before.TaskDuration,scriptSeconds:after.ScriptDuration-before.ScriptDuration,scope:'current page renderer tasks; not total browser/host CPU or a matched benchmark'}));
  if(process.env.E2E_SCREENSHOT_DIR){await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/admin-empty.png',await page.screenshot());}
 }});
 console.log('PASS '+(local?'local':'HTTP')+' empty admin shell, omitted menus/reporting, retained API entry and member chat persistence');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.pageErrors,console:page.consoleMessages.filter(x=>x.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
