import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import { assertGuideTour } from '../guide/assert.mjs';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageIntegrationFixture } from './fixture.mjs';
const local=process.argv.includes('--local'),fixture=local?null:await stageIntegrationFixture();
const browser=await launchChromeBrowser({baseUrl:local?'http://127.0.0.1:4200':process.env.E2E_SERVER_BASE_URL??'http://localhost'}),page=await browser.newPage();
try{
 await assertUi({page,local,actorId:fixture?.ids[0],actorName:fixture?.names[0],memberReady:async()=>{
  await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');await page.clickVisible('.settings-menu .app-menu__trigger');await page.clickVisible('[data-guide-item="api"]');await page.waitForSelector('[data-guide-field="mcp-main-access"] button');
  if(process.env.E2E_SCREENSHOT_DIR){
   await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/api-desktop.png',await page.screenshot());
   await page.setViewport({width:390,height:844});await writeFile(process.env.E2E_SCREENSHOT_DIR+'/api-mobile.png',await page.screenshot());
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'API popup fits mobile');await page.setViewport({width:1440,height:950});
  }
  await assertGuideTour(page,['API destination','Separate client access','Create client access','What an AI client can do','Connect through the URL','Approved AI clients','Manual client setup','Integration help','Close integrations','Main MCP access']);
  await page.clickVisible('[data-guide-control="integration-help"]');await page.waitForSelector('[data-guide-field="integration-help-levels"]');
  await assertGuideTour(page,['Enable and connect','Four permission packs','Account permissions','Manual client credentials','Close help']);
  await page.clickVisible('app-integration-settings-popup > app-popup:last-child .ui-popup__close');
  await page.clickVisible('[data-guide-field="mcp-main-access"] [data-guide-item="permissions"]');await page.waitForSelector('[data-guide-field="integration-access-editor"]');
  await assertGuideTour(page,['Four access levels','What this pack permits','Discard this selection']);
  await page.clickVisible('app-integration-access-button app-popup .ui-popup__close');
  await page.clickVisible('[data-guide-field="mcp-advanced"] [data-guide-item="advanced"]');
  await assertGuideTour(page,['Manual client keys','Create client','Close']);
  await page.clickVisible('app-mcp-connections app-popup .ui-popup__close');
  await page.clickVisible('app-integration-settings-popup > app-popup .ui-popup__header .ui-popup__close');
  await page.waitFor('API removed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')));
 }});
 console.log('PASS '+(local?'local':'HTTP')+' API, permission, connection help and manual MCP guides with only retained controls');
}catch(error){console.error(error.stack);console.error(await page.evaluate(()=>document.body.innerText.slice(0,2500)));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
