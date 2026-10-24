import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import {mkdir,writeFile,rm} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {stageChatFixture} from '../chat/fixture.mjs';
import {nativeDemoSessions} from '@fssrepository/myscoutee-e2e/shared/native-demo-sessions.mjs';
const expectedVersion = JSON.parse(readFileSync(new URL('../../../../../frontend/package.json', import.meta.url), 'utf8')).version;
export async function stageOperatorConfigurationFixture(baseUrl){
 const fixture=await stageChatFixture();
 await fixture.query(`db.users.updateOne({userId:${JSON.stringify(fixture.ids[0])}},{$set:{operator:true}})`);
 const snapshot=await fixture.query(`EJSON.stringify({configuration:db.deploymentConfigurations.findOne({_id:'deployment-configuration'}),settings:db.appSettings.findOne({settingsKey:'global'}),email:db.operatorEmailConfigurations.findOne({_id:'global'})},{relaxed:false})`);
 const backup='.e2e-artifacts/fixtures/operator-configuration-'+randomUUID()+'.json';await mkdir('.e2e-artifacts/fixtures',{recursive:true,mode:0o700});await writeFile(backup,snapshot,{mode:0o600});
 const headers={'Content-Type':'application/json','X-App-Session-Kind':'demo','X-Demo-User-Id':fixture.ids[0]};
 const sessions=nativeDemoSessions(baseUrl);
 try {
 Object.assign(headers,await sessions.headersForRequest('GET',baseUrl+'/api/operator/configuration',{headers}));
 const started=performance.now();
 const get=await fetch(baseUrl+'/api/operator/configuration',{headers});assert.equal(get.status,200);const before=await get.json();
 const readDurationMs=Math.round(performance.now()-started);
 const publicFirebase=before.firebase.publicConfiguration;
 const restore={adminEmails:before.adminEmails,privacyContact:{dataControllerName:before.privacyContact.dataControllerName,privacyContactEmail:before.privacyContact.privacyContactEmail},socialLinks:before.socialLinks,
  branding:{productName:before.branding.productName,logoUrl:before.branding.logoUrl,logoCharacterIndex:before.branding.logoCharacterIndex},integration:before.integration,
  firebase:{projectId:before.firebase.projectId,...Object.fromEntries(['apiKey','authDomain','storageBucket','messagingSenderId','appId','measurementId','vapidKey'].map(k=>[k,publicFirebase[k]??''])),authenticationCredential:'',messagingCredential:''}};
 return {...fixture,before,readDurationMs,async readEmail(){
  const response=await fetch(baseUrl+'/api/operator/email-configuration',{headers});assert.equal(response.status,200);return response.json();
 },async saveEmail(request){
  const response=await fetch(baseUrl+'/api/operator/email-configuration',{method:'PUT',headers,body:JSON.stringify(request)});
  assert.equal(response.status,200);return response.json();
 },async assertEmailCredentialMasked(credential){
  const state=await this.readEmail();assert(state.credentialConfigured);assert(!JSON.stringify(state).includes(credential));assert(!('credential' in state));
  const stored=JSON.parse(await fixture.query(`JSON.stringify(db.operatorEmailConfigurations.findOne({_id:'global'}))`));
  assert(stored.credential);assert(!JSON.stringify(stored).includes(credential),'Only encrypted provider credentials are persisted');
 },async setAdministratorEmails(adminEmails){
  const response=await fetch(baseUrl+'/api/operator/configuration',{method:'PUT',headers,body:JSON.stringify({...restore,adminEmails})});
  assert.equal(response.status,200,'Configure isolated fixture administrators through the existing owner');
 },async assertAuthority(){
  const memberHeaders={'Content-Type':'application/json','X-App-Session-Kind':'demo','X-Demo-User-Id':fixture.ids[1]};
  Object.assign(memberHeaders,await sessions.headersForRequest('GET',baseUrl+'/api/operator/configuration',{headers:memberHeaders}));
  const member=await fetch(baseUrl+'/api/operator/configuration',{headers:memberHeaders});
  assert.equal(member.status,403,'A member cannot read operator configuration');
  const update=await fetch(baseUrl+'/api/operator/configuration',{method:'PUT',headers:memberHeaders,body:JSON.stringify(restore)});
  assert.equal(update.status,403,'A member cannot write operator configuration');
  const publicResponse=await fetch(baseUrl+'/api/deployment/configuration');
  assert.equal(publicResponse.status,200);const publicSettings=await publicResponse.json();
  assert(!('adminEmails' in publicSettings));assert(!('firebase' in publicSettings));
  assert(!('authenticationCredential' in before.firebase));assert(!('messagingCredential' in before.firebase));
  const tls=await fetch(baseUrl+'/api/operator/configuration/tls',{headers});assert.equal(tls.status,200);
  assert.equal((await tls.json()).capability,'BACKEND_UNAVAILABLE','Demo cannot change host TLS');
  const updateStatus=await fetch(baseUrl+'/api/operator/updates',{headers});assert.equal(updateStatus.status,200);
  const overview=await updateStatus.json();assert.equal(overview.enabled,false);assert.equal(overview.currentVersion,expectedVersion);
  for(const route of ['/api/operator/configuration/tls','/api/operator/updates','/api/operator/email-configuration']){
   const denied=await fetch(baseUrl+route,{headers:memberHeaders});assert.equal(denied.status,403,'Operator-only host status');
  }
 },async close(){
  try{
   // Restore through the owning endpoint first so its in-memory settings/public caches agree.
   const response=await fetch(baseUrl+'/api/operator/configuration',{method:'PUT',headers,body:JSON.stringify(restore)});assert.equal(response.status,200,'Restore the original operator settings through their owner');
  }finally{
   try{await fixture.query(`(()=>{const original=EJSON.parse(${JSON.stringify(snapshot)},{relaxed:false});db.deploymentConfigurations.replaceOne({_id:original.configuration._id},original.configuration);db.appSettings.replaceOne({_id:original.settings._id},original.settings);if(original.email)db.operatorEmailConfigurations.replaceOne({_id:'global'},original.email,{upsert:true});else db.operatorEmailConfigurations.deleteOne({_id:'global'});return {restored:true};})()`);}
   finally{try{await sessions.close();}finally{await fixture.close();}}
  }
  await rm(backup);return {restored:true};
 }};
 } catch(error) {
  try { await sessions.close(); } finally { await fixture.close(); await rm(backup); }
  throw error;
 }
}
