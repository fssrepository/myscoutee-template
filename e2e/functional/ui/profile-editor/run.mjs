import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
import { assertProfileEditor } from './assert.mjs';
const local=process.argv.includes('--local');
const fixture=local?null:await stageChatFixture();
if(fixture) await fixture.query(`db.users.updateOne({userId:${JSON.stringify(fixture.ids[0])}},{$set:{gender:'woman',profileFormVersion:7}})`);
const browser=await launchChromeBrowser({baseUrl:local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost'});
const page=await browser.newPage();
try {
 const actorId=fixture?.ids[0]??'100000000000000000000001';
 const readHidden=fixture?()=>fixture.query(`(()=>{const u=db.users.findOne({userId:${JSON.stringify(actorId)}});return {email:u.email,city:u.city,gender:u.gender,locationCoordinates:u.locationCoordinates,partitionKey:u.partitionKey,profileStatus:u.profileStatus,operator:u.operator,profileFormVersion:u.profileFormVersion};})()`):()=>page.evaluate(async()=>{
  const db=await new Promise((resolve,reject)=>{const req=indexedDB.open('myscoutee-template_demo_db');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});
  try{return await new Promise((resolve,reject)=>{const req=db.transaction('tables').objectStore('tables').get('users');req.onsuccess=()=>{const u=req.result.byId['100000000000000000000001'];resolve({city:u.city,gender:u.gender,locationCoordinates:u.locationCoordinates,partitionKey:u.partitionKey,profileStatus:u.profileStatus,operator:u.operator,profileFormVersion:u.profileFormVersion});};req.onerror=()=>reject(req.error);});}finally{db.close();}
 });
 await assertUi({page,local,actorId,actorName:fixture?.names[0]??'Alex Morgan',memberReady:async()=>console.log(JSON.stringify(await assertProfileEditor({page,local,readHidden})))});
 console.log('PASS '+(local?'local':'HTTP')+' minimal profile and avatar persistence');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.consoleMessages.filter(message=>message.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally {
 await page.close();await browser.close();
 if(fixture){
  // Only this test's UUID-owned media prefix. Reuse the seed bootstrap's existing mc alias.
  const owner=fixture.ids[0];assert.match(owner,/^qa-template-chat-[0-9a-f-]+-a$/);
  const minio=process.env.E2E_MINIO_CONTAINER??process.env.E2E_MONGO_CONTAINER.replace(/mongodb$/,'minio');
  assert.match(minio,/^myscoutee-template-[a-z0-9-]*minio$/);
  const [media,data]=await Promise.allSettled([
   promisify(execFile)('docker',['exec',minio,'sh','-c','mc rm --recursive --force "${MINIO_SEED_ALIAS:-myscoutee}/${MEDIA_BUCKET}/public/demo/images/$1/profile/" >/dev/null','cleanup-owned-profile',owner]),
   fixture.close()
  ]);
  assert.equal(media.status,'fulfilled',media.reason?.message);assert.equal(data.status,'fulfilled',data.reason?.message);
  assert.deepEqual(data.value,{usersRemaining:0,chatsRemaining:0,messagesRemaining:0});console.log('Owned profile fixture and media cleaned');
 }
}
