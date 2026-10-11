import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
import {assertUi} from '../entry/assert.mjs';
import {assertGuideTour} from '../guide/assert.mjs';
import {openConversation} from '../chat/assert.mjs';
import {stageAdminFixture} from './fixture.mjs';
import {nativeDemoSessions} from '../../../shared/native-demo-sessions.mjs';
const local=process.argv.includes('--local'),baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=local?null:await stageAdminFixture(baseUrl);
const member=fixture?.ids[1]??'100000000000000000000001',memberName=fixture?.names[1]??'Alex Morgan';
const target=fixture?.ids[0]??'100000000000000000000002',targetName=fixture?.names[0]??'Sam Taylor';
const admin=fixture?.adminId??'100000000000000000000003',adminName=fixture?.adminName??'Template Admin';
const sessions=local?null:nativeDemoSessions(baseUrl);
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const subject='QA feedback '+Date.now();
async function setting(id){await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');await page.clickVisible('.settings-menu .app-menu__trigger');await page.clickVisible(`[data-guide-item="${id}"]`);}
async function close(selector){await page.clickVisible(selector+' app-popup .ui-popup__header .ui-popup__close');}
async function confirm(){await page.clickVisible('app-dialog [data-guide-item="confirm"]');}
try{
 let messageId='template-message-2';
 if(fixture){const url=baseUrl+`/api/activities/chats/${encodeURIComponent(fixture.chatId)}/messages?userId=${target}`;
  const headers=await sessions.headersForRequest('POST',url,{headers:{'X-App-Session-Kind':'demo','X-Demo-User-Id':target}});
  const response=await fetch(url,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({text:'QA report evidence',attachments:[]})});assert.equal(response.status,200);messageId=(await response.json()).id;
 }
 await assertUi({page,local,actorId:member,actorName:memberName,memberReady:async()=>{
  await setting('feedback');await page.waitForSelector('app-profile-feedback-popup');
  await page.fillVisible('app-profile-feedback-popup input[placeholder="Short summary"]',subject);
  await page.fillVisible('app-profile-feedback-popup textarea','The template feedback workflow should persist this message.');
  await page.clickVisible('[data-guide-item="feedback-submit"]');await page.waitForSelector('app-profile-feedback-popup .support-success-card');
  await close('app-profile-settings-popups');
  await openConversation(page,targetName);
  await page.waitForSelector(`[data-chat-message-id="${messageId}"] button[aria-label="Report user"]`);
  await page.clickVisible(`[data-chat-message-id="${messageId}"] button[aria-label="Report user"]`);
  await page.fillVisible('app-profile-report-user-popup textarea:not([readonly])','Please review this saved chat message.');
  await page.clickVisible('[data-guide-item="report-user-submit"]');await page.waitForSelector('app-profile-report-user-popup .support-success-card');
  await close('app-profile-settings-popups');
  await page.navigate('/home?page=chat');await page.waitForSelector('app-template-chat');await page.waitForText(targetName);
  console.log('PASS member feedback/report and chat reload');
 }});
 await assertUi({page,local,role:'admin',actorId:admin,actorName:adminName,consentExpected:false,memberReady:async()=>{
  await setting('reports');await page.waitForSelector('app-admin-reports-popup single-row');
  await assertGuideTour(page,['Reports','Close']);
  await page.setViewport({width:390,height:844});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Reports fit mobile');
  if(process.env.E2E_SCREENSHOT_DIR){await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/admin-reports-mobile.png',await page.screenshot());}
  await page.setViewport({width:1440,height:950});
  await page.clickByText('app-admin-reports-popup single-row article',targetName);await page.waitForText('Please review this saved chat message.');
  await assertGuideTour(page,['Review report','Close']);
  await page.clickByText('.report-detail button','Reported chat message');await page.waitForSelector('.admin-message.is-reported');
  await assertGuideTour(page,['Chat review','Close']);
  assert((await page.evaluate(()=>document.querySelector('.admin-message.is-reported')?.innerText)).includes(local?'Try switching':'QA report evidence'));
  await close('app-admin-chat-review-popup');
  await setting('reports');await page.clickByText('app-admin-reports-popup single-row article',targetName);
  await page.clickByText('.report-detail button','Warn user');await page.waitForSelector('app-admin-chat-review-popup textarea');
  await assertGuideTour(page,['Chat review','Warning text','Send warning','Close']);
  await page.fillVisible('app-admin-chat-review-popup textarea','QA warning: please keep the conversation respectful.');
  await page.clickVisible('[data-guide-item="send-warning"]');await page.waitForText('Warning message was sent');await close('app-admin-chat-review-popup');
  await setting('reports');await page.clickByText('app-admin-reports-popup single-row article',targetName);
  await page.clickByText('.report-detail button','Block');await confirm();await page.waitFor('block dialog closed',()=>page.evaluate(()=>!document.querySelector('.report-detail')));
  await page.clickVisible('app-admin-reports-popup [data-guide-item="blocked"]');await page.clickByText('app-admin-reports-popup single-row article',targetName);
  await page.clickByText('.report-detail button','Unblock');await confirm();await page.waitFor('unblock dialog closed',()=>page.evaluate(()=>!document.querySelector('.report-detail')));
  await page.clickVisible('app-admin-reports-popup [data-guide-item="unresolved"]');await page.clickByText('app-admin-reports-popup single-row article',targetName);
  const beforeResolve=page.network.filter(r=>new URL(r.url).pathname==='/api/admin/dashboard').length;
  await page.clickByText('.report-detail button','Resolve');await confirm();
  await page.waitFor('report resolved',()=>page.evaluate(()=>!document.querySelector('.report-detail')));
  assert.equal(page.network.filter(r=>new URL(r.url).pathname==='/api/admin/dashboard').length,beforeResolve,'Resolve uses returned state without another dashboard read');
  await page.clickVisible('app-admin-reports-popup [data-guide-item="resolved"]');await page.clickByText('app-admin-reports-popup single-row article',targetName);
  await page.clickByText('.report-detail button','Reopen');await confirm();await page.waitFor('reopen completed',()=>page.evaluate(()=>!document.querySelector('.report-detail')));
  await page.clickVisible('app-admin-reports-popup [data-guide-item="unresolved"]');await page.waitForSelector('app-admin-reports-popup single-row');
  if(process.env.E2E_SCREENSHOT_DIR){await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/admin-reports.png',await page.screenshot());}
  await close('app-admin-reports-popup');
  await setting('feedback-review');await page.waitForSelector('app-admin-feedback-popup single-row');
  await assertGuideTour(page,['Feedback','Filter','Close']);
  await page.clickByText('app-admin-feedback-popup single-row article',memberName);await page.waitForText(subject);
  await assertGuideTour(page,['Review feedback','Close']);
  await page.clickVisible('app-admin-feedback-popup app-popup:last-of-type .ui-popup__header .ui-popup__close');
  await page.clickVisible('app-admin-feedback-popup single-row .ui-single-row__menu-trigger');await page.clickVisible('[data-guide-item="markSolved"]');await confirm();
  await page.waitFor('feedback solved',()=>page.evaluate(()=>!document.querySelector('app-admin-feedback-popup single-row')));
  await page.clickVisible('[data-guide-item="review-status-filter"]');await page.clickVisible('[data-guide-item="review-status:resolved"]');
  await page.waitForSelector('app-admin-feedback-popup single-row');await page.clickByText('app-admin-feedback-popup single-row article',memberName);await page.waitForText(subject);
  await page.clickVisible('app-admin-feedback-popup app-popup:last-of-type .ui-popup__header .ui-popup__close');
  await close('app-admin-feedback-popup');
  await setting('support');await page.waitForSelector('app-template-chat');await page.waitForText(targetName);
  await page.clickByText('single-row .ui-single-row__title',targetName);await page.waitForText('QA warning: please keep the conversation respectful.');
  await page.fillVisible('.chat-composer textarea','QA support follow-up');await page.clickVisible('.chat-composer .send');await page.waitForText('QA support follow-up');
  await page.navigate('/admin?page=chat');await page.waitForSelector('app-template-chat');await page.clickByText('single-row .ui-single-row__title',targetName);await page.waitForText('QA support follow-up');
  await setting('api');await page.waitForSelector('app-integration-settings-popup [data-guide-control="integration-generate"]');
  await close('app-integration-settings-popup');
 }});
 console.log('PASS '+(local?'local':'HTTP')+' member feedback/chat report, admin evidence/warning/block/unblock/resolve/reopen/feedback resolution/support/API UI and session cleanup');
}catch(error){console.error(error.stack);console.error(JSON.stringify({url:await page.evaluate(()=>location.href),errors:page.pageErrors,console:page.consoleMessages.filter(x=>x.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,4500)),buttons:await page.evaluate(()=>[...document.querySelectorAll('button')].filter(x=>x.getBoundingClientRect().width).map(x=>({text:x.innerText,aria:x.getAttribute('aria-label'),item:x.getAttribute('data-guide-item')})))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(sessions)await sessions.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
