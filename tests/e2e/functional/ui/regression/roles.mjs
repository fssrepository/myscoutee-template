import assert from 'node:assert/strict';
import {createHash,randomBytes,randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {stageIntegrationFixture} from '../integrations/fixture.mjs';
import {stageAdminFixture} from '../admin/fixture.mjs';
import {enter} from './assert.mjs';

const local=process.argv.includes('--local');
const baseUrl=local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):(process.env.E2E_SERVER_BASE_URL??'http://localhost');
const rolesOnly=process.argv.includes('--roles-only');
const gatesOnly=process.argv.includes('--gates-only');
const selectedStatus=process.argv.find(value=>value.startsWith('--status='))?.slice(9);
assert.ok(!selectedStatus||['blocked','inactive','deleted','onboarding'].includes(selectedStatus),'Unknown profile status');
assert.ok(!(rolesOnly&&gatesOnly),'Select one checkpoint');
const selectedRole=process.argv.find(value=>value.startsWith('--role='))?.slice(7);
const fromPrivacy=process.argv.includes('--from-privacy');
const fromLogin=process.argv.includes('--from-login');
assert.ok(!(fromPrivacy||fromLogin)||selectedRole,'A focused continuation must select its role');
assert.ok(!selectedRole||['member','admin','operator'].includes(selectedRole),'Unknown role');
assert.ok(local||selectedRole||gatesOnly,'Select one HTTP role so its existing fixture owner can be used');
assert.ok(process.env.E2E_MANAGED_BROWSER_MANIFEST,'Use the visible managed checkpoint launcher');
assert.ok(!local||rolesOnly,'Local routing checks do not exercise HTTP refusal gates');
const fixture=local?null:selectedRole==='admin'?await stageAdminFixture(baseUrl):await stageIntegrationFixture();
const actorId=fixture?.adminId??fixture?.ids[0];
const actorName=fixture?.adminName??fixture?.names[0];
const browser=await launchChromeBrowser({baseUrl});
const result={status:'FAIL',mode:local?'local':'http',roles:[],checks:[],gates:[],continuation:fromLogin?'required-login':fromPrivacy?'privacy':null};
if(local) result.oauthProvider='N/A: the existing local adapter does not implement authorization or consent';
async function waitForConsent(page){
  await page.waitForSelector(local?'.authorization-error[role="alert"]':'[data-guide-field="mcp-authorize-profile"]');
}
let clientId='';
function assertRoute(actual,expected){
  const value=new URL(actual,baseUrl), reference=new URL(expected,baseUrl);
  assert.equal(value.pathname,reference.pathname);
  assert.deepEqual([...value.searchParams],[...reference.searchParams]);
}
async function logout(page,role){
  await page.navigate(role==='member'?'/home':'/'+role);
  await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('[data-guide-item="settings"]');
  await page.clickVisible('[data-guide-item="logout"]');
  await page.clickVisible('.dialog-panel [data-guide-item="confirm"]');
  await page.waitForUrlPath('/entry');
}
try {
  const resource=local?{resource:baseUrl+'/mcp/private'}:await (await fetch(baseUrl+'/api/mcp/oauth/resource')).json();
  const redirect='http://127.0.0.1:18996/callback';
  let client={client_id:'qa-local-route-'+randomUUID()};
  if(!local){
    const registered=await fetch(baseUrl+'/api/mcp/oauth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({client_name:'Template role route '+randomUUID(),redirect_uris:[redirect],token_endpoint_auth_method:'none',scope:'myscoutee:api'})});
    assert.equal(registered.status,201);
    client=await registered.json();clientId=client.client_id;
  }
  const verifier=randomBytes(32).toString('base64url');
  const query=new URLSearchParams({client_id:client.client_id,redirect_uri:redirect,scope:'myscoutee:api',resource:resource.resource,response_type:'code',code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',state:'role-route & '+randomUUID()});
  const route='/mcp/authorize?'+query;
  for(const role of gatesOnly?[]:selectedRole?[selectedRole]:['member','admin','operator']) {
    const id=local?({member:'100000000000000000000001',admin:'100000000000000000000003',operator:'100000000000000000000004'})[role]:actorId;
    console.log('Point 23 '+result.mode+': '+role);
    if(!local)await fixture.query(`db.users.updateOne({userId:${JSON.stringify(id)}},{$set:{hostTier:${JSON.stringify(role==='admin'?'Admin':'')},operator:${role==='operator'},profileStatus:'public'}})`);
    const page=await browser.newPage();
    try {
      await page.connection.send('Page.bringToFront');
      await enter(page,{id,role});
      if(!fromPrivacy&&!fromLogin){
      await page.navigate(role==='member'?'/admin':'/home');
      await page.waitForUrlPath(role==='member'?'/home':'/'+role);
      await page.waitForSelector('.user-selector-menu');
      if(!local)await page.waitFor('persisted privacy consent',async()=>await fixture.query(`db.privacyConsents.countDocuments({userId:${JSON.stringify(id)}})`)>0);
      await page.navigate(route);
      await waitForConsent(page);
      const actual=await page.evaluate(()=>({path:location.pathname,query:location.search,profile:document.querySelector('[data-guide-field="mcp-authorize-profile"]')?.innerText,client:document.querySelector('[data-guide-field="mcp-authorize-client"]')?.innerText}));
      assert.equal(actual.path,'/mcp/authorize');assert.deepEqual([...new URLSearchParams(actual.query)],[...query]);
      if(!local){assert.ok(actual.profile.includes(actorName));assert.ok(actual.client.includes(client.client_name));}
      result.checks.push({role,authenticatedRoute:true,queryPreserved:true,profileMatched:local?'N/A':true,roleHomeRedirect:true});
      }
      if(!fromLogin&&!local){
      await fixture.query(`db.privacyConsents.deleteMany({userId:${JSON.stringify(actorId)}})`);
      await page.navigate(route);
      await page.waitForUrlPath('/entry');
      assertRoute(await page.evaluate(()=>new URL(location.href).searchParams.get('redirect')),route);
      // Entry's existing authenticated-session path synchronizes the browser's
      // accepted choices itself, then returns to the requested authorization.
      await page.waitForSelector('[data-guide-field="mcp-authorize-profile"]');
      assertRoute(await page.evaluate(()=>location.pathname+location.search),route);
      assert.ok(await fixture.query(`db.privacyConsents.countDocuments({userId:${JSON.stringify(actorId)}})`)>0);
      result.checks.push({role,httpPrivacyGate:true,persistedPrivacyConsent:true,privacyReturn:true});
      }
      await logout(page,role);
      await page.navigate(route);
      await page.waitFor('signed-out gate',()=>page.evaluate(()=>location.pathname==='/entry'));
      assertRoute(await page.evaluate(()=>new URL(location.href).searchParams.get('redirect')),route);
      await enter(page,{id,role,entryPath:route,targetPath:'/mcp/authorize'});
      await waitForConsent(page);
      assertRoute(await page.evaluate(()=>location.pathname+location.search),route);
      if(!local)assert.ok(await fixture.query(`db.privacyConsents.countDocuments({userId:${JSON.stringify(id)}})`)>0);
      result.checks.push({role,requiredLoginReturn:true,queryPreserved:true});
      result.roles.push({role,status:'PASS'});
      console.log('Point 23 '+result.mode+' '+role+': PASS');
    } finally {await page.close();}
  }
  if(!rolesOnly){
    for(const profileStatus of selectedStatus?[selectedStatus]:['blocked','inactive','deleted','onboarding']) {
      console.log('Point 24 HTTP: '+profileStatus);
      await fixture.query(`db.users.updateOne({userId:${JSON.stringify(actorId)}},{$set:{hostTier:'',operator:false,profileStatus:'public'}})`);
      const page=await browser.newPage();
      try {
      await page.connection.send('Page.bringToFront');
      await enter(page,{id:actorId});
      await fixture.query(`db.users.updateOne({userId:${JSON.stringify(actorId)}},{$set:{profileStatus:${JSON.stringify(profileStatus)}}})`);
      await page.navigate(route);
      await page.waitFor('account refusal gate',()=>page.evaluate(()=>location.pathname==='/entry'));
      assertRoute(await page.evaluate(()=>new URL(location.href).searchParams.get('redirect')),route);
      assert.equal(await page.isSelectorVisible('[data-guide-field="mcp-authorize-profile"]'),false);
      assert.equal(await fixture.query(`db.users.findOne({userId:${JSON.stringify(actorId)}}).profileStatus`),profileStatus);
      result.gates.push({profileStatus,denied:true,queryPreserved:true});
      console.log('Point 24 HTTP '+profileStatus+': PASS');
      } finally {await page.close();}
    }
  }
  result.status='PASS';
} catch(error){result.error=error.stack;process.exitCode=1;}
finally {
  await browser.close();
  if(clientId)await fixture.query(`db.mcpOAuthClients.deleteOne({_id:${JSON.stringify(clientId)}})`);
  result.cleanup=fixture?await fixture.close():{serverFixture:'none'};
  await mkdir('.e2e-artifacts/functional/template-regression',{recursive:true});
  await writeFile('.e2e-artifacts/functional/template-regression/'+result.mode+(gatesOnly?'-refusal-gates':'-roles')+(selectedStatus?'-'+selectedStatus:'')+(selectedRole?'-'+selectedRole:'')+(fromLogin?'-login-retest':'')+'.json',JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result));
}
