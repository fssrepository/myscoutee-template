import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
import {assertGuideTour} from '../guide/assert.mjs';
import {assertUi} from '../entry/assert.mjs';
import {stageOperatorConfigurationFixture} from './fixture.mjs';
const local=process.argv.includes('--local'),baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=local?null:await stageOperatorConfigurationFixture(baseUrl);
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const popup='app-operator-configuration-popup';
const product=`${popup} [data-palette="violet"] input[type="text"]`;
const controller='[data-control-id="operator-configuration-data-controller-name"] input';
const privacyEmail='[data-control-id="operator-configuration-privacy-contact-email"] input';
const admin='[data-control-id="operator-configuration-admin-email-list"] textarea';
const apiUrl=`${popup} [data-palette="blue"] app-link-input input`;
async function open(){await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');await page.clickVisible('.settings-menu .app-menu__trigger');await page.clickVisible('[data-guide-item="configuration"]');await page.waitForSelector(product);await page.waitFor('configuration loaded',()=>page.evaluate(selector=>document.querySelector(selector)?.disabled===false,product));}
async function close(){await page.clickVisible(`${popup} > app-popup .ui-popup__header .ui-popup__close`);await page.waitFor('configuration removed',()=>page.evaluate(()=>!document.querySelector('app-operator-configuration-popup')));}
async function saved(){await page.waitForSelector(`${popup} .operator-action-popup__result.is-success`);}
async function value(selector){await page.waitForSelector(selector);return page.evaluate(s=>document.querySelector(s).value,selector);}
try{
 if(fixture)await fixture.assertAuthority();
 await assertUi({page,local,role:'operator',actorId:fixture?.ids[0]??'100000000000000000000004',actorName:fixture?.names[0]??'Template Operator',memberReady:async()=>{
  await open();assert.equal(await page.evaluate(()=>document.querySelectorAll('app-operator-configuration-popup .operator-action-popup__configuration-group').length),7);
  if(process.env.E2E_SCREENSHOT_DIR){await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/configuration-desktop.png',await page.screenshot());await page.setViewport({width:390,height:844});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/configuration-mobile.png',await page.screenshot());assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.setViewport({width:1440,height:950});}
  await assertGuideTour(page,['Branding','Administrators','Privacy contact','Firebase','API address','Social links','Close']);
  const originalName=await value(product);
  await page.fill(product,'Unsaved name');await close();await open();assert.equal(await value(product),originalName,'Closing discards unsaved name');
  await page.fill(product,'Template QA project');await page.clickVisible('[data-guide-item="operator-save-branding"]');await saved();
  await close();assert.equal(await page.evaluate(()=>document.querySelector('.member-header .brand').textContent.trim()),'Template QA project');
  await page.navigate('/operator');await page.waitForText(fixture?.names[0]??'Template Operator');await open();assert.equal(await value(product),'Template QA project');
  await page.fill(controller,'Template QA Controller');assert.equal(await page.evaluate(()=>document.querySelector('[data-guide-item="operator-save-privacy-contact"]').disabled),true,'Incomplete privacy contact blocks save');
  await page.fill(privacyEmail,'Privacy@Example.test');await page.clickVisible('[data-guide-item="operator-save-privacy-contact"]');await saved();
  await page.fill(admin,'First@Example.test, first@example.test\nsecond@example.test');await page.clickVisible('[data-guide-item="operator-save-admin-emails"]');await saved();
  await page.fill(apiUrl,'https://api.example.test/api/integrations/v1/');await page.clickVisible('[data-guide-item="operator-save-integration"]');await saved();
  await close();await open();assert.equal(await value(controller),'Template QA Controller');assert.equal(await value(privacyEmail),'privacy@example.test');assert.equal(await value(admin),'first@example.test, second@example.test');assert.equal(await value(apiUrl),'https://api.example.test/api/integrations/v1');
  assert.equal(await page.evaluate(()=>document.querySelector('[data-guide-item="operator-activate-firebase"]')?.disabled??true),true,'Unverified Firebase cannot be activated');
  assert.equal(await page.evaluate(()=>document.querySelectorAll('app-operator-configuration-popup textarea[autocomplete="new-password"]').length),2,'Both Firebase credential inputs are retained');
  const toggle=`${popup} .operator-action-popup__on-off-toggle`;
  if(local){
   await page.clickVisible(toggle);
   await page.fill('.operator-action-popup__tls-grid input[type="text"]','app.example.test');
   await page.fill('.operator-action-popup__tls-grid input[type="email"]','operator@example.test');
   await page.clickVisible('[data-guide-item="operator-save-tls"]');
   await page.waitFor('local TLS saved',()=>page.evaluate(()=>document.querySelector('.operator-action-popup__tls-header')?.parentElement.innerText.includes('Certificate configured')));
   await close();await open();assert.equal(await value('.operator-action-popup__tls-grid input[type="text"]'),'app.example.test');
   await assertGuideTour(page,['Branding','Administrators','Privacy contact','Firebase','API address','Social links','Close','TLS']);
   await page.clickVisible(toggle);await page.clickVisible('[data-guide-item="operator-save-tls"]');
   await page.waitFor('TLS disabled',()=>page.evaluate(()=>!document.querySelector('[data-guide-item="operator-save-tls"]')));
  }else{
   assert.equal(await page.evaluate(selector=>document.querySelector(selector).disabled,toggle),true,'Unconfigured host TLS cannot be mutated through demo');
  }
  // Return the project name before the common logout/entry checks; the fixture restores all server fields.
  await page.fill(product,originalName);await page.clickVisible('[data-guide-item="operator-save-branding"]');await saved();await close();
 }});
 if(fixture)console.log(JSON.stringify({operatorReadMs:fixture.readDurationMs}));
 console.log('PASS '+(local?'local':'HTTP')+' operator configuration: isolated operator login, seven retained sections, discard/save/reload, branding propagation, privacy validation, normalized admin emails/API URL, unverified Firebase activation denied');
}catch(error){console.error(error.stack);console.error(JSON.stringify({pageErrors:page.pageErrors,failedRoutes:page.network.filter(r=>r.status>=400).map(r=>({path:new URL(r.url).pathname,status:r.status}))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned configuration fixture cleanup',await fixture.close());}
