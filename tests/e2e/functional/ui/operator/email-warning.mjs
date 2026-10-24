import assert from 'node:assert/strict';
import {readFile,writeFile,rm,mkdir} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {stageOperatorConfigurationFixture} from './fixture.mjs';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';

// Reuse the native provider receiver and scheduler fixture pattern; never send real mail.
assert.equal(process.env.E2E_EMAIL_PROVIDER_STUB,'true','Start the documented local provider receiver and select its endpoint first.');
const baseUrl=process.env.E2E_SERVER_BASE_URL??'https://127.0.0.1';
const capture=process.env.E2E_EMAIL_CAPTURE??'.e2e-artifacts/functional/email/received.jsonl';
const confirmationTransport=process.env.E2E_EMAIL_UNSUBSCRIBE_TRANSPORT??'browser';
assert(['browser','http'].includes(confirmationTransport));
const fixture=await stageOperatorConfigurationFixture(baseUrl);
const email=`template-warning-${randomUUID()}@example.invalid`;
const recipientHash=createHash('sha256').update(email).digest('hex');
const before=await fixture.query("EJSON.stringify(db.notificationRuleConfigs.findOne({ruleKey:'account-deletion-warning'}),{relaxed:false})");
assert.notEqual(before,'null','The native warning rule must be seeded.');
const backup='.e2e-artifacts/fixtures/email-warning-'+randomUUID()+'.json';
await mkdir('.e2e-artifacts/fixtures',{recursive:true,mode:0o700});await writeFile(backup,before,{mode:0o600});
let browser,page;
const wait=async(label,check)=>{const deadline=Date.now()+45000;while(Date.now()<deadline){const value=await check();if(value)return value;await new Promise(r=>setTimeout(r,1000));}throw Error('Timed out: '+label);};
try{
 await fixture.assertAuthority();
 const config=await fixture.readEmail();assert.equal(config.enabled,false);
 await fixture.query(`(()=>{
  db.users.updateOne({userId:${JSON.stringify(fixture.ids[1])}},{$set:{profileStatus:'deleted',deletedAtIso:new Date(Date.now()-26*86400000).toISOString(),email:${JSON.stringify(email)}},$unset:{deletionWarningFor:''}});
  db.notificationRuleConfigs.updateOne({ruleKey:'account-deletion-warning'},{$set:{enabled:true,runState:{},'timing.mode':'interval','timing.intervalSeconds':1,'timing.intervalMinutes':1,'timing.intervalAmount':1,'timing.intervalUnit':'seconds',scheduleSlots:[]}});
  return {staged:true};
 })()`);
 const configured=await fixture.saveEmail({expectedRevision:config.revision,enabled:true,providerId:'sendgrid',fromEmail:'template-qa@example.invalid',fromName:'Template QA',credential:'template-qa-provider-key',clearCredential:false});
 await fixture.assertEmailCredentialMasked('template-qa-provider-key');
 await wait('durable warning receipt',async()=>JSON.parse(await fixture.query(`JSON.stringify(db.accountDeletionWarnings.findOne({userId:${JSON.stringify(fixture.ids[1])},status:'sent'}))`)));
 const rows=(await readFile(capture,'utf8')).trim().split('\n').filter(Boolean).map(line=>JSON.parse(line)).filter(row=>row.payload.personalizations[0].to[0].email===email);
 assert.equal(rows.length,1,'One message per deletion episode');
 const plain=rows[0].payload.content.find(part=>part.type==='text/plain').value;
 const html=rows[0].payload.content.find(part=>part.type==='text/html').value;
 assert(plain.includes('QA Template Receiver'));assert(!plain.includes('{{'));assert(html.includes('Template QA'));
 const link=plain.match(/https?:\/\/[^\s]+\/api\/public\/email\/unsubscribe#token=[A-Za-z0-9_-]+/)?.[0];assert(link,'Delivered email includes the public unsubscribe URL');
 const token=new URLSearchParams(new URL(link).hash.slice(1)).get('token');
 const endpoint=baseUrl+'/api/public/email/unsubscribe';
 const post=(path,value)=>fetch(endpoint+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:value})});
 let response=await post('/status',token);assert.equal(response.status,200);assert.equal((await response.json()).unsubscribed,false);
 if(confirmationTransport==='browser'){
  browser=await launchChromeBrowser({baseUrl});page=await browser.newPage();
  await page.navigate(link);await page.waitFor('confirmation ready',()=>page.evaluate(()=>document.querySelector('#confirm')?.disabled===false));
  assert.equal(JSON.parse(await fixture.query(`JSON.stringify(db.emailUnsubscriptions.countDocuments({_id:${JSON.stringify(recipientHash)}}))`)),0,'Opening the link does not unsubscribe');
  await page.clickVisible('#confirm');await page.waitForText('You have unsubscribed');
  await page.navigate(link);await page.waitForText('You have unsubscribed');
 }else{
  // Split-service contract qualification reuses the same fixture/owner requests.
  // The already-qualified browser interaction is not replayed for a transport check.
  const landing=await fetch(endpoint);assert.equal(landing.status,200);assert((await landing.text()).includes('id="confirm"'));
  assert.equal(JSON.parse(await fixture.query(`JSON.stringify(db.emailUnsubscriptions.countDocuments({_id:${JSON.stringify(recipientHash)}}))`)),0,'Reading confirmation does not unsubscribe');
  const confirmed=await post('',token);assert.equal(confirmed.status,200);
 }
 response=await post('/status',token);assert.equal((await response.json()).unsubscribed,true);
 response=await post('',token);assert.equal(response.status,200,'Repeated confirmation is idempotent');
 response=await post('',token.slice(0,-6)+'abcdef');assert.equal(response.status,400,'Tampered tokens are rejected');
 // A new deletion episode still obeys the durable recipient suppression.
 await fixture.query(`db.users.updateOne({userId:${JSON.stringify(fixture.ids[1])}},{$set:{deletedAtIso:new Date(Date.now()-25*86400000).toISOString()},$unset:{deletionWarningFor:''}})`);
 await wait('suppressed subsequent warning',async()=>JSON.parse(await fixture.query(`JSON.stringify(db.accountDeletionWarnings.findOne({userId:${JSON.stringify(fixture.ids[1])},status:'unsubscribed'}))`)));
 const received=(await readFile(capture,'utf8')).trim().split('\n').filter(Boolean).map(line=>JSON.parse(line)).filter(row=>row.payload.personalizations[0].to[0].email===email);
 assert.equal(received.length,1,'Unsubscribed recipients do not generate another provider request');
 const current=await fixture.readEmail();await fixture.saveEmail({...current,expectedRevision:current.revision,enabled:false,credential:'',clearCredential:false});
 const index=JSON.parse(await fixture.query("JSON.stringify(db.accountDeletionWarnings.getIndexes().map(i=>i.name))"));
 for(const name of ['account_warning_retry_due','account_warning_stale_claim','account_warning_user'])assert(index.includes(name));
 console.log(`PASS warning scheduler → local provider → anonymous ${confirmationTransport} confirmation/status → suppressed next episode; encrypted/masked credentials and cleanup index.`);
}finally{
 if(page)await page.close();if(browser)await browser.close();
 try{
  await fixture.query(`(()=>{db.notificationRuleConfigs.replaceOne({_id:EJSON.parse(${JSON.stringify(before)},{relaxed:false})._id},EJSON.parse(${JSON.stringify(before)},{relaxed:false}));db.accountDeletionWarnings.deleteMany({userId:{$in:${JSON.stringify(fixture.ids)}}});db.emailUnsubscriptions.deleteOne({_id:${JSON.stringify(recipientHash)}});return {restored:true};})()`);
 }finally{await fixture.close();await rm(backup);}
}
