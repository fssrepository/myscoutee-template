import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { launchChromeBrowser } from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { assertGuideTour } from '../guide/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
const local=process.argv.includes('--local');
const fixture=local?null:await stageChatFixture();
const actorId=fixture?.ids[0]??'100000000000000000000001';
if(fixture) await fixture.query(`(()=>{
 const id=${JSON.stringify(actorId)};
 const source=db.userNotifications.findOne({_id:'template-welcome-1'});
 if(!source)throw Error('Template notification seed missing');
 db.userNotifications.insertOne({...source,_id:'qa-welcome:'+id,syncKey:'qa-welcome:'+id,recipientUserId:id});
 db.users.updateOne({userId:id},{$set:{'activities.notifications':1}});
})()`);
const browser=await launchChromeBrowser({baseUrl:local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost'});
const page=await browser.newPage();
const popup='app-notification-center-popup';
async function navigatorAction(id){await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');await page.clickVisible(`.navigator-header-actions [data-guide-item="${id}"]`);}
async function openNotifications(){await navigatorAction('notifications');await page.waitForSelector(popup+' single-row article');}
async function openGuide(expected){await page.clickVisible('app-explanation-launcher .explanation-guide-menu button');await page.waitForSelector('.explanation-guide-overlay__card');await page.waitForText(expected);}
async function closeGuide(){await page.clickVisible('.explanation-guide-overlay__close');await page.waitForSelectorHidden('.explanation-guide-overlay__card');}
async function userState(){
 if(fixture)return fixture.query(`db.users.findOne({userId:${JSON.stringify(actorId)}},{activities:1,notificationPreferences:1})`);
 return page.evaluate(async actor=>{
  const db=await new Promise((res,rej)=>{const r=indexedDB.open('myscoutee-template_demo_db');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error);});
  try{return await new Promise((res,rej)=>{const r=db.transaction('tables').objectStore('tables').get('users');r.onsuccess=()=>res(r.result.byId[actor]);r.onerror=()=>rej(r.error);});}finally{db.close();}
 },actorId);
}
try{
 await assertUi({page,local,actorId,actorName:fixture?.names[0]??'Alex Morgan',memberReady:async()=>{
  if(process.env.E2E_SCREENSHOT_DIR){
   await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});
   await page.setViewport({width:390,height:844});
   await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]');
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile member header must not overflow');
   await writeFile(join(process.env.E2E_SCREENSHOT_DIR,'member-mobile.png'),await page.screenshot());
   await openGuide('Your menu');
   await writeFile(join(process.env.E2E_SCREENSHOT_DIR,'guide-mobile.png'),await page.screenshot());
   await closeGuide();await page.setViewport({width:1440,height:950});
  }
  await assertGuideTour(page,['Your menu']);
  await openNotifications();await page.waitForText('Welcome to Templates');
  if(process.env.E2E_SCREENSHOT_DIR){
   await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});
   assert(await page.evaluate(()=>document.querySelector('app-notification-center-popup .ui-popup__panel').getBoundingClientRect().height>innerHeight*.85),'Native full-height desktop popup geometry is present');
   await writeFile(join(process.env.E2E_SCREENSHOT_DIR,'notifications-desktop.png'),await page.screenshot());
   await page.setViewport({width:390,height:844});
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile notifications must not overflow');
   await writeFile(join(process.env.E2E_SCREENSHOT_DIR,'notifications-mobile.png'),await page.screenshot());
   await page.setViewport({width:1440,height:950});
  }
  await openGuide('Notification alerts');await closeGuide();
  await page.clickVisible(popup+' single-row .ui-single-row__menu-trigger');
  await page.clickVisible('[data-guide-item="markNotificationRead"]');
  await page.waitForText('No notifications');
  await page.waitFor('stored unread count cleared',async()=>Number((await userState()).activities.notifications)===0);
  await page.clickVisible(popup+' [data-guide-control="notification-bucket"] .app-menu__trigger');
  await page.clickVisible('[data-guide-item="notification-bucket:all"]');await page.waitForText('Welcome to Templates');
  await page.clickVisible('[data-guide-item="notification-attention-toggle"]');
  await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForSelectorHidden('.dialog');
  assert.equal((await userState()).notificationPreferences.muted,true);
  await page.clickVisible(popup+' .ui-popup__close');
  await page.navigate('/home');await page.waitForText(fixture?.names[0]??'Alex Morgan');
  assert.equal((await userState()).notificationPreferences.muted,true);
  await navigatorAction('notifications');
  await page.clickVisible(popup+' [data-guide-control="notification-bucket"] .app-menu__trigger');
  await page.clickVisible('[data-guide-item="notification-bucket:new"]');await page.waitForText('No notifications');
  await page.clickVisible('[data-guide-item="notification-attention-toggle"]');await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForSelectorHidden('.dialog');
  assert.equal((await userState()).notificationPreferences.muted,false);
  await page.clickVisible(popup+' .ui-popup__close');
  // The guide preference is owned by the native service and must survive a reload.
  await navigatorAction('explanations');await page.clickVisible('.user-menu-close-btn');
  await page.waitForSelectorHidden('app-explanation-launcher .explanation-guide-menu button');
  await page.navigate('/home');await page.waitForText(fixture?.names[0]??'Alex Morgan');
  assert.equal(await page.isSelectorVisible('app-explanation-launcher .explanation-guide-menu button'),false);
  await navigatorAction('explanations');await page.clickVisible('.user-menu-close-btn');await openGuide('Your menu');await closeGuide();
  if(fixture){
   await fixture.query(`(()=>{
    const actor=${JSON.stringify(actorId)},peer=${JSON.stringify(fixture.ids[1])},chat=${JSON.stringify(fixture.chatId)};
    const id='qa-notification-message:'+actor,now=new Date().toISOString();
    db.chatMessages.insertOne({_id:id,id,chatId:chat,sender:'QA Template Receiver',senderAvatar:{id:peer,initials:'QR'},text:'Notification target message',time:'12:00',sentAtIso:now,readBy:[],messageSenderUserIds:[peer],messageReadByUserIds:[],attachments:[],reactions:[]});
    db.userNotifications.insertOne({_id:'qa-chat-notification:'+actor,syncKey:'qa-chat-notification:'+actor,recipientUserId:actor,kind:'chat-message',category:'chat',title:'QA Template Receiver',message:'Notification target message',sourceType:'chat',sourceId:chat,payload:{chatId:chat,messageId:id},createdDate:now,updatedDate:now,revision:1,readAtIso:null});
    db.users.updateOne({userId:actor},{$set:{'activities.notifications':1}});
    db.chats.updateOne({id:chat,ownerUserId:actor},{$set:{unread:1,lastMessage:'Notification target message',lastSenderId:peer}});
   })()`);
   const start=Date.now();
   await page.waitFor('realtime badge while popup closed',()=>page.isSelectorVisible('.notification-attention-menu'),45000);
   console.log('Realtime badge refreshed after '+(Date.now()-start)+' ms at the native 30-second interval');
   await openNotifications();await page.waitForText('Notification target message');
   await page.clickVisible(popup+' single-row .ui-single-row__menu-trigger');await page.clickVisible('[data-guide-item="openNotificationChat"]');
   await page.waitForSelector('.chat-compose-box');await page.waitForText('Notification target message');
   await page.waitFor('chat marks its notification read',async()=>Number((await userState()).activities.notifications)===0);
   const record=await fixture.query(`db.userNotifications.findOne({_id:${JSON.stringify('qa-chat-notification:'+actorId)}},{readAtIso:1})`);assert.ok(record.readAtIso);
  }
  const before=page.network.filter(x=>new URL(x.url).pathname.includes('/notifications/sync')).length;
  assert.equal(await page.isSelectorVisible(popup),false);
  console.log(JSON.stringify({mode:local?'local':'http',notificationsSyncRequests:before,guideRequests:page.network.filter(x=>new URL(x.url).pathname.startsWith('/api/')&&new URL(x.url).pathname.includes('/explanation/')).length}));
 }});
 console.log('PASS '+(local?'local':'HTTP')+' notification read/mute persistence, realtime/deep link where HTTP, contextual guide and preference reload');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.consoleMessages.filter(m=>m.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,4000)),buttons:await page.evaluate(()=>Array.from(document.querySelectorAll('button')).map(b=>({text:b.innerText,cls:b.className,guide:b.getAttribute('data-guide-item'),label:b.getAttribute('aria-label')})))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
