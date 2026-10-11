import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { assertGuideTour } from '../guide/assert.mjs';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
const local=process.argv.includes('--local'), fixture=local?null:await stageChatFixture();
const baseUrl=local?'http://127.0.0.1:4200':process.env.E2E_SERVER_BASE_URL??'http://localhost';
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const key=`myscoutee-template.${local?'demo':'http'}.location.tracking-enabled.v1`,point={latitude:52.6,longitude:13.5};
const actorId=fixture?.ids[0]??'100000000000000000000001';
await page.connection.send('Page.addScriptToEvaluateOnNewDocument',{source:`if(localStorage.getItem(${JSON.stringify(key)})===null)localStorage.setItem(${JSON.stringify(key)},'false');`});
await page.setGeolocation(point);
await page.setGeolocationPermission('denied');
async function open(){await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');await page.clickVisible('.settings-menu .app-menu__trigger');await page.clickVisible('[data-guide-item="app-setup"]');await page.waitForSelector('app-setup-popup .ui-popup');await page.waitFor('permission controls ready',()=>page.evaluate(()=>!document.querySelector('app-setup-popup [data-guide-item="location"]')?.disabled));}
async function storedPoint(){
 if(fixture)return fixture.query(`db.users.findOne({userId:${JSON.stringify(actorId)}}).locationCoordinates`);
 return page.evaluate(async actorId=>{
  const db=await new Promise((resolve,reject)=>{const req=indexedDB.open('myscoutee-template_demo_db');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});
  try{return await new Promise((resolve,reject)=>{const req=db.transaction('tables').objectStore('tables').get('users');req.onsuccess=()=>resolve(req.result.byId[actorId].locationCoordinates);req.onerror=()=>reject(req.error);});}finally{db.close();}
 },actorId);
}
try{
 await assertUi({page,local,actorId,actorName:fixture?.names[0]??'Alex Morgan',memberReady:async()=>{
  await open();
  const guideTitles=['Device-specific instructions','Location','Notifications on this device','Apply the selected permissions'];
  if(await page.isSelectorVisible('app-setup-popup [data-guide-item="install"]'))guideTitles.push('Install a home-screen shortcut');
  await assertGuideTour(page,guideTitles);
  if(process.env.E2E_SCREENSHOT_DIR){
   await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/setup-desktop.png',await page.screenshot());
   await page.setViewport({width:390,height:844});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/setup-mobile.png',await page.screenshot());
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Setup fits a mobile viewport');await page.setViewport({width:1440,height:950});
  }
  assert.equal(await page.evaluate(()=>document.querySelector('app-setup-popup [data-guide-item="notifications"]').disabled),true,'Unconfigured push cannot request permission');
  await page.clickVisible('app-setup-popup .ui-popup__header [data-guide-field="help"]');await page.waitForSelector('.app-setup-help');
  assert.equal(await page.evaluate(()=>document.querySelectorAll('.app-setup-help-card').length),6);
  await page.clickVisible('app-setup-popup app-popup:has(.app-setup-help) .ui-popup__close');
  await page.waitForSelectorHidden('.app-setup-help');assert(await page.isSelectorVisible('.app-setup-content'));
  await page.setGeolocationPermission('granted');
  await page.clickVisible('app-setup-popup [data-guide-item="location"]');
  await page.clickVisible('app-setup-popup [data-guide-item="allow"]');
  await page.waitFor('location saved',async()=>JSON.stringify(await storedPoint())===JSON.stringify(point),30000);
  assert.equal(await page.evaluate(key=>localStorage.getItem(key),key),'true');
  await page.clickVisible('app-setup-popup .ui-popup__close');await page.navigate('/home');await page.waitForText(fixture?.names[0]??'Alex Morgan');
  assert.deepEqual(await storedPoint(),point);
  await open();await page.clickVisible('app-setup-popup [data-guide-item="location"]');await page.clickVisible('app-setup-popup [data-guide-item="allow"]');
  await page.waitFor('location tracking disabled',()=>page.evaluate(key=>localStorage.getItem(key)==='false',key));
  await page.clickVisible('app-setup-popup .ui-popup__close');
  await page.setGeolocation({latitude:52.8,longitude:13.8});
  await page.navigate('/home');await page.waitForText(fixture?.names[0]??'Alex Morgan');
  assert.deepEqual(await storedPoint(),point,'Disabling tracking retains the saved point and does not write a later sample');
 }});
 console.log('PASS '+(local?'local':'HTTP')+' device setup, nested help, disabled unconfigured push, location save/reload and tracking disable');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.consoleMessages.filter(m=>m.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2700))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
