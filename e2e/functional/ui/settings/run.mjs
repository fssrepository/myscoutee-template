import assert from 'node:assert/strict';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
const local=process.argv.includes('--local');
const fixture=local?null:await stageChatFixture();
const browser=await launchChromeBrowser({baseUrl:local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost'});
const page=await browser.newPage();
const actorId=fixture?.ids[0]??'100000000000000000000001';
async function openMenu(){await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');await page.clickVisible('.settings-menu .app-menu__trigger');}
try {
 await assertUi({page,local,actorId,actorName:fixture?.names[0]??'Alex Morgan',endsSession:true,memberReady:async()=>{
  for(const [kind,title] of [['privacy','Privacy information'],['terms','Template demonstration terms']]){
   await openMenu();await page.clickVisible(`[data-guide-item="${kind}"]`);await page.waitForSelector('app-profile-settings-popups .document-viewer-section-body');await page.waitForText(title);
   await page.clickVisible('app-profile-settings-popups .ui-popup__close');
  }
  await openMenu();await page.clickVisible('[data-guide-item="delete-account"]');await page.waitForText('Delete account?');
  await page.clickVisible('app-dialog [data-guide-item="cancel"]');await page.waitForSelectorHidden('.dialog');
  assert.equal(new URL(await page.evaluate(()=>location.href)).pathname,'/home','Cancel leaves the account active');
  await page.clickVisible('.settings-menu .app-menu__trigger');await page.clickVisible('[data-guide-item="delete-account"]');
  await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForUrlPath('/entry',30000);
 }});
 const stored=fixture?await fixture.query(`db.users.findOne({userId:${JSON.stringify(actorId)}},{profileStatus:1,status:1,previousProfileStatus:1,deletedAtIso:1,accountPurgePendingCounterTracked:1})`):await page.evaluate(async()=>{
  const db=await new Promise((resolve,reject)=>{const req=indexedDB.open('myscoutee-template_demo_db');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});
  try{return await new Promise((resolve,reject)=>{const req=db.transaction('tables').objectStore('tables').get('users');req.onsuccess=()=>resolve(req.result.byId['100000000000000000000001']);req.onerror=()=>reject(req.error);});}finally{db.close();}
 });
 assert.equal(stored.profileStatus,'deleted');assert.equal(stored.previousProfileStatus,'public');assert.ok(Date.parse(stored.deletedAtIso));
 if(!local){assert.equal(stored.status,'D');assert.equal(stored.accountPurgePendingCounterTracked,true);}
 // Re-enter the same retained account: neither selecting it nor cancelling restores it.
 async function selectDeleted(){
  await page.navigate('/entry');
  if(local)await page.clickVisible('.entry-actions .primary');else await page.clickByText('.entry-actions button','Explore');
  await page.clickVisible(`.demo-user-item[data-user-id="${actorId}"]`);await page.waitForText('Reactivate account?');
 }
 await selectDeleted();await page.clickVisible('app-dialog [data-guide-item="cancel"]');await page.waitForSelectorHidden('.dialog');
 assert.equal(new URL(await page.evaluate(()=>location.href)).pathname,'/entry');
 await selectDeleted();await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForUrlPath('/home',30000);
 await page.navigate('/home');await page.waitForText(fixture?.names[0]??'Alex Morgan');
 if(fixture){
  const restored=await fixture.query(`db.users.findOne({userId:${JSON.stringify(actorId)}},{profileStatus:1,status:1,deletedAtIso:1,accountPurgePendingCounterTracked:1})`);
  assert.equal(restored.profileStatus,'public');assert.equal(restored.status,'A');assert(!restored.deletedAtIso);assert.equal(restored.accountPurgePendingCounterTracked,false);
 }
 await openMenu();await page.clickVisible('[data-guide-item="logout"]');await page.waitForUrlPath('/entry');
 assert.deepEqual(page.pageErrors,[]);assert(!page.network.some(request=>request.status>=400));
 console.log('PASS '+(local?'local':'HTTP')+' privacy/terms, delete cancel/confirm, reactivation cancel/confirm, restored reload and purge tracking');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.consoleMessages.filter(message=>message.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
