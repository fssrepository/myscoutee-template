import assert from 'node:assert/strict';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
const local=process.argv.includes('--local'), fixture=local?null:await stageChatFixture();
const browser=await launchChromeBrowser({baseUrl:local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost'}),page=await browser.newPage();
const actorId=fixture?.ids[0]??'100000000000000000000001',name='New Template Member';
try{
 if(local){
  await page.navigate('/entry');await page.clickVisible('[data-guide-item="explore"]:not([disabled])');await page.clickByText('app-document-viewer button','Accept and continue');
  await page.waitForSelector(`.demo-user-item[data-user-id="${actorId}"]`);
  await page.evaluate(async id=>{
   const db=await new Promise((resolve,reject)=>{const req=indexedDB.open('myscoutee-template_demo_db');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});
   try{await new Promise((resolve,reject)=>{const tx=db.transaction('tables','readwrite'),store=tx.objectStore('tables'),req=store.get('users');req.onsuccess=()=>{const table=req.result;table.byId[id]={...table.byId[id],profileStatus:'onboarding',name:'Pending Template Member',deletedAtIso:new Date().toISOString()};store.put(table,'users');};tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});}finally{db.close();}
  },actorId);
 }
 async function completeOnboarding(){
  await page.waitForSelector('app-profile-editor [data-control-id="name"] input');
  await page.waitForUrlPath('/entry');
  await page.fill('app-profile-editor [data-control-id="name"] input','');
  assert.equal(await page.evaluate(()=>document.querySelector('app-profile-editor [data-guide-item="profile-save"]').disabled),true,'Empty profile cannot finish onboarding');
  // Reload before completing: the retained session must reopen the same minimal form.
  await page.navigate('/entry');await page.waitForSelector('app-profile-editor [data-control-id="name"] input');
  await page.fill('app-profile-editor [data-control-id="name"] input',name);
  await page.clickVisible('app-profile-editor [data-guide-item="profile-save"]');
  await page.waitForUrlPath('/home',30000);await page.waitForText(name);
 }
 await assertUi({page,local,actorId,actorName:local?name:fixture.names[0],consentExpected:!local,
  profileSelected:local?completeOnboarding:undefined,
  memberReady:local?undefined:async()=>{
   // Native HTTP demo selection omits onboarding profiles. Keep a real owned session,
   // then stage the same persisted state as a new provider account before reload.
   await fixture.query(`(()=>{db.users.updateOne({userId:${JSON.stringify(actorId)}},{$set:{profileStatus:'onboarding',name:'Pending Template Member',deletedAtIso:new Date().toISOString(),accountPurgePendingCounterTracked:true}});db.notificationRuleConfigs.updateOne({ruleKey:'account-purge',baseGroupId:null},{$inc:{'runState.pendingCount':1}});return true;})()`);
   await page.navigate('/entry');await completeOnboarding();
  }
 });
 if(fixture){const saved=await fixture.query(`db.users.findOne({userId:${JSON.stringify(actorId)}},{name:1,profileStatus:1,deletedAtIso:1,accountPurgePendingCounterTracked:1})`);assert.equal(saved.name,name);assert.equal(saved.profileStatus,'public');assert(!saved.deletedAtIso);assert.equal(saved.accountPurgePendingCounterTracked,false);}
 console.log('PASS '+(local?'local':'HTTP')+' minimal onboarding, empty-name rejection, pending reload, explicit save, member reload and logout');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.consoleMessages.filter(m=>m.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
