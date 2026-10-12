import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
import {assertGuideTour} from '../guide/assert.mjs';
import {assertUi} from '../entry/assert.mjs';
import {stageOperatorConfigurationFixture} from './fixture.mjs';
const expectedVersion = JSON.parse(readFileSync(new URL('../../../../frontend/package.json', import.meta.url), 'utf8')).version;
const local=process.argv.includes('--local'),baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=local?null:await stageOperatorConfigurationFixture(baseUrl);
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const popup='app-operator-update-popup',check='[data-guide-item="operator-refresh-update"]';
async function loaded(){await page.waitForSelector(popup+' .operator-action-popup');await page.waitFor('update loaded',()=>page.evaluate(selector=>document.querySelector(selector)?.getAttribute('aria-busy')==='false',popup+' .operator-action-popup'));}
async function open(){await page.clickVisible('.operator-actions [data-guide-item="updates"]');await loaded();}
async function close(){await page.clickVisible(`${popup} > app-popup .ui-popup__header .ui-popup__close`);await page.waitFor('update popup closed',()=>page.evaluate(()=>!document.querySelector('app-operator-update-popup')));}
async function stageLocalRelease(){return page.evaluate(async()=>{
 const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('myscoutee-template_demo_db');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
 try {await new Promise((resolve,reject)=>{const tx=db.transaction('tables','readwrite'),store=tx.objectStore('tables'),r=store.get('deploymentConfiguration');
  r.onsuccess=()=>{const record=r.result;if(!record?.deploymentUpdate){tx.abort();return;}record.deploymentUpdate={...record.deploymentUpdate,availableVersion:'1.0.1',updateAvailable:true,progress:{...record.deploymentUpdate.progress,bytesTotal:1024}};store.put(record,'deploymentConfiguration');};
  tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(new Error('Missing owned local update state'));
 });}finally{db.close();}
 });}
try{
 if(fixture)await fixture.assertAuthority();
 await assertUi({page,local,role:'operator',actorId:fixture?.ids[0]??'100000000000000000000004',actorName:fixture?.names[0]??'Template Operator',memberReady:async()=>{
  await open();
  assert.equal(await page.evaluate(()=>document.querySelector('.operator-action-popup__version--current')?.textContent.trim()),expectedVersion);
  assert.equal(await page.isSelectorVisible('[data-guide-item="operator-apply-update"]'),false,'No invented release or install action');
  await assertGuideTour(page,['Updates','Check','Close']);
  if(process.env.E2E_SCREENSHOT_DIR){await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/updates-desktop.png',await page.screenshot());await page.setViewport({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await writeFile(process.env.E2E_SCREENSHOT_DIR+'/updates-mobile.png',await page.screenshot());await page.setViewport({width:1440,height:950});}
  await page.clickVisible(check);await loaded();await close();
  await page.navigate('/operator?notificationTarget=updates');await loaded();
  assert.equal(await page.evaluate(()=>new URL(location.href).searchParams.has('notificationTarget')),false,'Consume the notification target after opening its popup');
  await close();
  if(local){
   await stageLocalRelease();await page.navigate('/operator?notificationTarget=updates');await loaded();
   assert.equal(await page.evaluate(()=>document.querySelector('.operator-action-popup__version--available')?.textContent.trim()),'1.0.1');
   await assertGuideTour(page,['Updates','Check','Install','Close']);
   await page.clickVisible('[data-guide-item="operator-apply-update"]');
   // The existing PWA owner reloads the application after the simulated installation.
   await page.waitFor('completed update reload',()=>page.evaluate(()=>!document.querySelector('app-operator-update-popup')&&!!document.querySelector('app-member-page')));
   await open();assert.equal(await page.evaluate(()=>document.querySelector('.operator-action-popup__version--current')?.textContent.trim()),'1.0.1');
   assert.equal(await page.isSelectorVisible('[data-guide-item="operator-apply-update"]'),false);
   await close();
  }else{
   const reads=page.network.filter(r=>new URL(r.url).pathname==='/api/operator/updates');
   assert.equal(reads.length,4,'Login and the two full page loads preload once each; explicit Check adds one request, opening the cached popup adds none');
   assert(![...page.requests.values()].some(r=>r.method==='POST'&&new URL(r.url).pathname.startsWith('/api/operator/updates')),'Viewing updates never approves an installation');
  }
 }});
 console.log('PASS '+(local?'local':'HTTP')+' update UI: version, explicit check, guide, notification target, reload, no implicit installation'+(local?', simulated install persists and native PWA reload completes':''));
}catch(error){console.error(error.stack);console.error(JSON.stringify({pageErrors:page.pageErrors,failedRoutes:page.network.filter(r=>r.status>=400).map(r=>({path:new URL(r.url).pathname,status:r.status}))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned configuration fixture cleanup',await fixture.close());}
