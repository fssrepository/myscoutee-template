import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
import {stageChatFixture} from '../chat/fixture.mjs';
import {enter} from './assert.mjs';
const prefix='qa-palette-'+randomUUID(),result={point:14,samples:[]};
export async function localRows(page,remove,variants=null){return page.evaluate(async({prefix,remove,variants})=>{
 const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('myscoutee-template_demo_db');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
 try{return await new Promise((resolve,reject)=>{const tx=db.transaction('tables','readwrite'),store=tx.objectStore('tables'),r=store.get('notifications');let remaining;
 r.onsuccess=()=>{const t=r.result,user='100000000000000000000001',base=t.byId['template-welcome-1'];if(!base)throw Error('Local notification baseline missing');
 if(remove){for(const id of t.ids.filter(id=>id.startsWith(prefix)))delete t.byId[id];t.ids=t.ids.filter(id=>!id.startsWith(prefix));t.idsByRecipientUserId[user]=t.idsByRecipientUserId[user].filter(id=>!id.startsWith(prefix));}
 else for(const [index,variant] of (variants??[{read:false},{read:true}]).entries()){const read=variant.read??false,id=prefix+'-'+index;t.byId[id]={...base,id,payload:{...base.payload,notification_title_key:'',notification_message_key:'',senderName:read?'QA Context':''},title:variant.title??(read?'QA palette read':'QA palette unread'),senderName:read?'QA Context':null,createdAtIso:variant.createdAtIso??new Date().toISOString(),readAtIso:read?new Date().toISOString():null};t.ids.push(id);t.idsByRecipientUserId[user].push(id);}
 remaining=t.ids.filter(id=>id.startsWith(prefix)).length;store.put(t,'notifications');};
 tx.oncomplete=()=>resolve({remaining});tx.onerror=()=>reject(tx.error);});}finally{db.close();}
},{prefix,remove,variants});}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
try{for(const local of (process.argv.includes('--local-only')?[true]:[false,true])){
 let fixture,browser,page,staged=false;
 try{
 if(!local){fixture=await stageChatFixture();await fixture.query(`(()=>{const s=db.userNotifications.findOne({_id:'template-welcome-1'});if(!s)throw Error('Notification baseline missing');for(const read of [false,true]){const id=${JSON.stringify(prefix)}+(read?'-read':'-unread');db.userNotifications.insertOne({...s,_id:id,syncKey:id,payload:{...s.payload,notification_title_key:'',notification_message_key:'',senderName:read?'QA Context':''},recipientUserId:${JSON.stringify(fixture.ids[0])},title:read?'QA palette read':'QA palette unread',senderName:read?'QA Context':null,createdDate:new Date().toISOString(),readAtIso:read?new Date().toISOString():null});}return {staged:true};})()`);}
 browser=await launchChromeBrowser({baseUrl:local?'http://127.0.0.1:4200':'https://127.0.0.1'});page=await browser.newPage();await page.connection.send('Page.bringToFront');await enter(page,{id:fixture?.ids[0]});
 if(local){await localRows(page,false);staged=true;await page.navigate('/home');await page.waitForSelector('.user-selector-menu');}
 await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');await page.clickVisible('.navigator-header-actions [data-guide-item="notifications"]');
 const host='app-notification-center-popup';await page.waitForSelector(host+' single-row article');
 await page.clickVisible(host+' [data-guide-control="notification-bucket"] .app-menu__trigger');await page.clickVisible('[data-guide-item="notification-bucket:all"]');
 await page.waitFor('both owned notification variants',()=>page.evaluate(selector=>document.querySelector(selector)?.innerText.includes('QA palette read')&&document.querySelector(selector)?.innerText.includes('QA palette unread'),host));
 for(const width of [1280,390]){await page.setViewport({width,height:844});
 const sample=await page.evaluate(({host,width,local})=>({mode:local?'local':'http',width,rows:[...document.querySelectorAll(host+' single-row article')].filter(n=>n.innerText.includes('QA palette')).map(n=>{const a=n.querySelector('.ui-single-row__avatar');return {text:n.innerText,classes:n.className,background:getComputedStyle(n).backgroundImage,backgroundColor:getComputedStyle(n).backgroundColor,avatar:a?{text:a.innerText,color:getComputedStyle(a).color,background:getComputedStyle(a).backgroundImage}:null};})}),{host,width,local});
 result.samples.push(sample);assert.equal(sample.rows.length,2);const read=sample.rows.find(r=>r.text.includes('QA palette read')),unread=sample.rows.find(r=>r.text.includes('QA palette unread'));assert.match(read.classes,/tone-muted/);assert.match(unread.classes,/tone-neutral/);assert.ok(read.avatar?.text.includes('QC'),JSON.stringify(read));assert.ok(unread.avatar?.text.includes('person'));
 }
 await page.clickVisible(host+' .ui-popup__close');
 }finally{
 if(local&&staged&&page)result.localCleanup=await localRows(page,true);
 if(page)await page.close();if(browser)await browser.close();
 if(fixture){await fixture.query(`(()=>{db.userNotifications.deleteMany({_id:{$regex:'^'+${JSON.stringify(prefix)}}});return {remaining:db.userNotifications.countDocuments({_id:{$regex:'^'+${JSON.stringify(prefix)}}})};})()`);result.actorCleanup=await fixture.close();}
 }
}result.status='PASS';}catch(error){result.status='FAIL';result.error=error.stack;process.exitCode=1;}
await writeFile('.e2e-artifacts/functional/template-regression/notification-palettes'+(process.argv.includes('--local-only')?'-local':'')+'.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));

}
