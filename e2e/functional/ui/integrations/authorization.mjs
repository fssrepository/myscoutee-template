import { openProfileIntegrations, closeProfileEditor } from '../profile-editor/assert.mjs';
import assert from 'node:assert/strict';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { createServer } from 'node:http';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi, logEntryNavigationDiagnostic } from '../entry/assert.mjs';
import { stageIntegrationFixture } from './fixture.mjs';
const baseUrl=process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=await stageIntegrationFixture();
let callback=null,clientId='';
const server=createServer((request,response)=>{
 if(request.url.startsWith('/callback'))callback=new URL(request.url,'http://127.0.0.1');
 response.writeHead(200,{'Content-Type':'text/html','Cache-Control':'no-store'});
 response.end('<!doctype html><title>Template OAuth test callback</title><p>Callback received.</p>');
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const redirectUri=`http://127.0.0.1:${server.address().port}/callback`;
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const verifier=randomBytes(32).toString('base64url');
const parameters=new URLSearchParams({redirect_uri:redirectUri,scope:'myscoutee:api',response_type:'code',code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',state:randomUUID()});
async function open(){await openProfileIntegrations(page);await page.waitForSelector('[data-guide-field="mcp-main-access"] button');}
async function save(){await page.clickVisible('[data-guide-field="integration-access-save"]');await page.waitFor('API removed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')));}
async function access(target){await page.clickVisible(`${target} [data-guide-item="permissions"]`);await page.fill('[data-guide-field="integration-access-editor"] input[type="range"]',3);await page.clickVisible('[data-guide-field="integration-access-apply"]');await page.waitForSelectorHidden('[data-guide-field="integration-access-editor"]');}
async function logout(){await closeProfileEditor(page);await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');await page.clickVisible('[data-guide-item="settings"]');await page.clickVisible('[data-guide-item="logout"]');await page.clickVisible('.dialog-panel [data-guide-item="confirm"]');await page.waitForUrlPath('/entry');}
async function api(token,path,method='GET'){const response=await fetch(`${baseUrl}/api/integrations/v1/${path}`,{method,headers:{Authorization:'Bearer '+token}});return {status:response.status,data:response.ok?await response.json():null};}
try{
 const metadataResponse=await fetch(baseUrl+'/api/mcp/oauth/metadata');assert.equal(metadataResponse.status,200);const metadata=await metadataResponse.json();
 const resourceResponse=await fetch(baseUrl+'/api/mcp/oauth/resource');assert.equal(resourceResponse.status,200);const resource=await resourceResponse.json();parameters.set('resource',resource.resource);
 const registration=await fetch(baseUrl+'/api/mcp/oauth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({client_name:'Template browser QA '+randomUUID(),redirect_uris:[redirectUri],token_endpoint_auth_method:'none',scope:'myscoutee:api'})});
 assert.equal(registration.status,201);const client=await registration.json();clientId=client.client_id;parameters.set('client_id',clientId);
 await assertUi({page,local:false,actorId:fixture.ids[0],actorName:fixture.names[0],memberReady:async()=>{
  const url='/mcp/authorize?'+parameters;
  await page.navigate(url);await page.waitForText(client.client_name);await page.waitForText(fixture.names[0]);
  assert.equal(await page.evaluate(()=>document.querySelector('[data-guide-item="approve"]').disabled),true,'Blocked MCP cannot be approved');
  await page.clickVisible('[data-guide-item="cancel"]');await page.waitFor('refusal callback',async()=>callback!==null);
  assert.equal(callback.searchParams.get('error'),'access_denied');assert.equal(callback.searchParams.get('state'),parameters.get('state'));assert.equal(callback.searchParams.get('code'),null);
  const owner=JSON.stringify(fixture.ids[0]);
  assert.equal(await fixture.query(`db.integrationTokens.countDocuments({ownerUserId:${owner},oauthClientId:${JSON.stringify(clientId)}})`),0,'Cancel creates no connection');
  await page.navigate('/home');await page.waitForText(fixture.names[0]);await open();await access('[data-guide-field="mcp-main-access"]');await save();
  await logout();callback=null;
  await page.navigate(url);await page.waitFor('MCP login redirect ready',()=>page.evaluate(()=>location.pathname.endsWith('/entry')&&new URL(location.href).searchParams.has('redirect')));
  const retained=new URL(new URL(await page.evaluate(()=>location.href)).searchParams.get('redirect'),baseUrl);
  assert.equal(retained.pathname,'/mcp/authorize');assert.deepEqual([...retained.searchParams],[...parameters],'Login retains every authorization parameter');
  await page.clickVisible('[data-guide-item="explore"]:not([disabled])');await page.waitForSelector(`.demo-user-item[data-user-id="${fixture.ids[0]}"]`);await page.clickVisible(`.demo-user-item[data-user-id="${fixture.ids[0]}"]`);
  await page.waitForUrlPath('/mcp/authorize');await page.waitForText(client.client_name);await page.waitForText(fixture.names[0]);
  await page.clickVisible('[data-guide-item="approve"]');await page.waitFor('approved callback',async()=>callback!==null);
  assert.equal(callback.searchParams.get('state'),parameters.get('state'));assert.equal(callback.searchParams.get('iss'),metadata.issuer);assert.ok(callback.searchParams.get('code'));
  const exchange=async()=>fetch(baseUrl+'/api/mcp/oauth/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:clientId,code:callback.searchParams.get('code'),redirect_uri:redirectUri,resource:parameters.get('resource'),code_verifier:verifier})});
  const response=await exchange();assert.equal(response.status,200);const grant=await response.json();
  assert.equal((await api(grant.access_token,'connect','POST')).data.profileId,fixture.ids[0]);assert.equal((await exchange()).status,400,'The browser-approved authorization code is single use');
  await page.navigate('/home');await page.waitForText(fixture.names[0]);await open();await page.waitForText(client.client_name);
  await access('[data-guide-field="mcp-connection"] [data-guide-field="mcp-client-access"]');await save();
  assert.equal((await api(grant.access_token,'chats')).status,200,'Saved UI permissions authorize the OAuth client');
  await open();await page.clickVisible('[data-guide-field="mcp-connection-actions"] button:last-child');await page.clickVisible('app-dialog [data-guide-item="confirm"]');await page.waitForSelectorHidden('[data-guide-field="mcp-connection"]');
  assert.equal((await api(grant.access_token,'connect','POST')).status,401,'UI revocation terminates OAuth access');
  await page.clickVisible('app-integration-settings-popup > app-popup .ui-popup__header .ui-popup__close');await page.waitFor('API removed',()=>page.evaluate(()=>!document.querySelector('app-integration-settings-popup')));await closeProfileEditor(page);
 }});
 console.log('PASS HTTP browser MCP: blocked approval, cancel without grant, login preserves request, explicit approval, PKCE code exchange, one-use code, saved client access and revocation');
}catch(error){console.error(error.stack);await logEntryNavigationDiagnostic(page);console.error(JSON.stringify({entry:await page.evaluate(()=>({path:location.pathname,queryKeys:[...new URL(location.href).searchParams.keys()],text:document.body.innerText.slice(0,2500)})),pageErrors:page.pageErrors,failedRoutes:page.network.filter(r=>r.status>=400).map(r=>({path:new URL(r.url).pathname,status:r.status}))}));process.exitCode=1;}
finally{
 await page.close();await browser.close();await new Promise(resolve=>server.close(resolve));
 try{if(clientId)await fixture.query(`db.mcpOAuthClients.deleteOne({_id:${JSON.stringify(clientId)}})`);}
 finally{console.log('Owned fixture cleanup',await fixture.close());}
}
