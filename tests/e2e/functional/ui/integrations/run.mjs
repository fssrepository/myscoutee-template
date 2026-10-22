import { openProfileIntegrations, closeProfileEditor } from '../profile-editor/assert.mjs';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { launchChromeBrowser } from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageIntegrationFixture } from './fixture.mjs';
const local=process.argv.includes('--local'),fixture=local?null:await stageIntegrationFixture();
const baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost';
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const api='app-integration-settings-popup';
let key='',clientId=randomUUID();
async function keyRequest(route,method='GET'){
 const response=await fetch(baseUrl+'/api/integrations/v1/'+route,{method,headers:{Authorization:'Bearer '+key,'X-App-Session-Kind':'demo','X-MyScoutee-Client-Id':clientId}});
 return {status:response.status,data:response.ok?await response.json():null};
}
async function open(){await openProfileIntegrations(page);await page.waitForSelector(`${api} [data-guide-control="integration-generate"]`);}
async function close(){await page.clickVisible(`${api} > app-popup > .ui-popup .ui-popup__header .ui-popup__close`);await page.waitFor('API popup removed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')),15000);await closeProfileEditor(page);}
async function access(target,value){
 await page.clickVisible(`${target} [data-guide-item="permissions"]`);await page.fill('[data-guide-field="integration-access-editor"] input[type="range"]',value);
 await page.clickVisible('[data-guide-field="integration-access-apply"]');await page.waitForSelectorHidden('[data-guide-field="integration-access-editor"]');
}
try{
 await assertUi({page,local,actorId:fixture?.ids[0],actorName:fixture?.names[0],memberReady:async()=>{
  await open();
  assert.equal(await page.evaluate(()=>document.querySelectorAll('app-integration-settings-popup [data-guide-field="affiliate-link"]').length),0);
  if(!local)assert.equal(page.network.filter(r=>new URL(r.url).pathname==='/api/integrations/settings').length,1,'One settings load includes API and MCP');
  await page.clickVisible('[data-guide-item="generate-integration-token"]');await page.clickVisible('app-dialog [data-guide-item="cancel"]');await page.waitForSelectorHidden('.dialog');
  assert.equal(await page.evaluate(()=>document.querySelectorAll('[data-guide-field="integration-token-row"]').length),0);
  await page.clickVisible('[data-guide-item="generate-integration-token"]');await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForSelector('[data-guide-field="integration-token-row"]');
  key=await page.evaluate(()=>document.querySelector('[data-guide-field="integration-token-row"] app-copy-link input').value);
  assert.ok(key.startsWith('msc_')&&key.length>40,'Creation reveals a one-time API key');
  if(!local)assert.equal((await keyRequest('chats')).status,403,'Write does not read private conversations');
  await access('[data-guide-field="integration-token-access"]',3);
  await page.clickVisible('[data-guide-field="integration-access-save"]');await page.waitFor('API popup removed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')),15000);
  await open();
  const masked=await page.evaluate(()=>document.querySelector('[data-guide-field="integration-token-row"] app-copy-link input').value);
  assert(masked.endsWith('…')&&masked.length<key.length,'Reload does not reveal the stored secret: '+JSON.stringify({length:masked.length,masked:masked.endsWith('…'),same:masked===key}));
  if(!local){assert.equal((await keyRequest('connect','POST')).data.profileId,fixture.ids[0]);assert.equal((await keyRequest('chats')).status,200);}
  await close();await page.navigate('/home');await page.waitForText(fixture?.names[0]??'Alex Morgan');await open();
  await page.clickVisible('[data-guide-field="integration-token-access"] [data-guide-item="permissions"]');
  assert.equal(await page.evaluate(()=>document.querySelector('[data-guide-field="integration-access-editor"] input').value),'3','Read access survives reload');
  await page.clickVisible('app-integration-access-button app-popup .ui-popup__close');
  await page.clickVisible('[data-guide-field="mcp-advanced"] [data-guide-item="advanced"]');
  await page.clickVisible('[data-guide-field="mcp-generate"] [data-guide-item="create"]');await page.waitForSelector('[data-guide-field="mcp-client-secret"] input');
  assert(await page.evaluate(()=>document.querySelector('[data-guide-field="mcp-client-secret"] input').value.length>20),'MCP creation reveals the secret once');
  await page.clickVisible('app-mcp-connections app-popup .ui-popup__close');await close();await open();
  await page.clickVisible('[data-guide-field="mcp-advanced"] [data-guide-item="advanced"]');
  await page.waitForSelector('[data-guide-field="mcp-client-id"] input');assert(!await page.isSelectorVisible('[data-guide-field="mcp-client-secret"]'));
  await page.clickVisible('[data-guide-field="mcp-client-revoke"] button');await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForSelectorHidden('[data-guide-field="mcp-client-id"]');
  await page.clickVisible('app-mcp-connections app-popup .ui-popup__close');
  await page.clickVisible('[data-guide-field="integration-token-row"] .integration-token-actions button');await page.clickVisible('app-dialog [data-guide-item="confirm"]');
  await page.waitForSelectorHidden('[data-guide-field="integration-token-row"]');
  if(!local)assert.equal((await keyRequest('connect','POST')).status,401,'Revocation denies the previously bound client');
  await close();
 }});
 console.log('PASS '+(local?'local':'HTTP')+' API create cancel/confirm, one-time reveal, masked reload, staged read permissions, persisted reload, MCP create/revoke and API revocation');
}catch(error){console.error(error.stack);console.error(JSON.stringify({pageErrors:page.pageErrors,failedRoutes:page.network.filter(r=>r.status>=400).map(r=>({url:r.url,status:r.status}))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
