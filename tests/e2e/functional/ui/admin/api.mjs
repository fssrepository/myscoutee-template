import { openProfileIntegrations, closeProfileEditor } from '../profile-editor/assert.mjs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {assertUi} from '../entry/assert.mjs';
import {stageAdminFixture} from './fixture.mjs';
import {nativeDemoSessions} from '@fssrepository/myscoutee-e2e/shared/native-demo-sessions.mjs';

const local=process.argv.includes('--local');
const baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=local?null:await stageAdminFixture(baseUrl);
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const sessions=local?null:nativeDemoSessions(baseUrl);
const clientId=randomUUID();let key='';
const popup='app-integration-settings-popup';
async function open(){await openProfileIntegrations(page);await page.waitForSelector(popup+' [data-guide-control="integration-generate"]');}
async function close(){await page.clickVisible(popup+' > app-popup > .ui-popup .ui-popup__close');await page.waitFor('API closed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')));await closeProfileEditor(page);}
async function request(path){return fetch(baseUrl+path,{headers:{Authorization:'Bearer '+key,'X-App-Session-Kind':'demo','X-MyScoutee-Client-Id':clientId}});}
try{
 await assertUi({page,local,role:'admin',actorId:fixture?.adminId??'100000000000000000000003',actorName:fixture?.adminName??'Template Admin',memberReady:async()=>{
  await open();await page.waitFor('Admin API URL',()=>page.evaluate(()=>[...document.querySelectorAll('app-integration-settings-popup app-copy-link input')].some(input=>input.value.endsWith('/api/admin-client/v1'))));
  await page.clickVisible('[data-guide-item="generate-integration-token"]');await page.clickVisible('app-dialog [data-guide-item="confirm"]');
  await page.waitForSelector('[data-guide-field="integration-token-row"] app-copy-link input');
  key=await page.evaluate(()=>document.querySelector('[data-guide-field="integration-token-row"] app-copy-link input').value);
  assert(key.startsWith('msc_')&&key.length>40,'Administrator key is revealed once');
  if(!local){
   const overview=await request('/api/admin-client/v1/overview');assert.equal(overview.status,200);
   assert.equal(overview.headers.get('cache-control'),'no-store');const body=await overview.json();
   assert.equal(typeof body.attention.unreadMessages,'number');
   for(const field of ['unresolvedReports','unresolvedFeedback','supportCases'])assert(!(field in body.attention),'Omitted moderation field: '+field);
   assert(!('statistics' in body),'Unselected product analytics is absent');
   assert.equal((await request('/api/integrations/v1/chats')).status,401,'Admin API keys cannot become member API keys');
   const url=baseUrl+'/api/admin/client-api/settings';
   const headers=await sessions.headersForRequest('GET',url,{headers:{'X-App-Session-Kind':'demo','X-Demo-User-Id':fixture.ids[1]}});
   assert.equal((await fetch(url,{headers})).status,403,'A member cannot manage administrator keys');
  }
  await close();await open();
  assert(await page.evaluate(()=>document.querySelector('[data-guide-field="integration-token-row"] app-copy-link input').value.endsWith('…')),'Stored key is masked on reopen');
  await page.clickVisible('[data-guide-field="integration-token-access"] [data-guide-item="permissions"]');
  await page.fill('[data-guide-field="integration-access-editor"] input[type="range"]',1);await page.clickVisible('[data-guide-field="integration-access-apply"]');
  await page.clickVisible('[data-guide-field="integration-access-save"]');await page.waitFor('Saved API closed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')));
  if(!local)assert.equal((await request('/api/admin-client/v1/overview')).status,403,'Blocked grant takes effect on the next request');
  await open();await page.clickVisible('[data-guide-field="integration-token-row"] .integration-token-actions button');await page.clickVisible('app-dialog [data-guide-item="confirm"]');
  await page.waitForSelectorHidden('[data-guide-field="integration-token-row"]');
  if(!local)assert.equal((await request('/api/admin-client/v1/overview')).status,401,'Revocation invalidates the key');
  await close();
 }});
 console.log('PASS '+(local?'local':'HTTP')+' administrator API owner, create/mask/block/revoke and scope isolation');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.pageErrors,console:page.consoleMessages.filter(x=>x.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally{await page.close();await browser.close();if(sessions)await sessions.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
