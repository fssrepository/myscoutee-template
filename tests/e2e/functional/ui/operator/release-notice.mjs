import assert from 'node:assert/strict';
import {spawn, execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, mkdir, readFile, writeFile, copyFile, chmod, rm, open} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve, join} from 'node:path';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {enter} from '../regression/assert.mjs';
import {stageOperatorConfigurationFixture} from './fixture.mjs';

assert(process.env.E2E_MANAGED_BROWSER_MANIFEST,'Use the visible managed checkpoint launcher');
const run=promisify(execFile), root=process.cwd(), baseUrl=process.env.E2E_SERVER_BASE_URL??'https://127.0.0.1';
const catalog=resolve('.e2e-artifacts/template-regression-release/catalog');
const release=resolve('.e2e-artifacts/template-regression-release/release');
const index=JSON.parse(await readFile(join(catalog,'index.json'),'utf8'));
const definition=JSON.parse(await readFile(join(release,'NOTIFICATION-FIXTURE.json'),'utf8'));
assert.equal(definition.installable,false);
const version=definition.version;
const temporary=await mkdtemp(join(tmpdir(),'template-release-notice-'));
const publicFiles=join(temporary,'public');await mkdir(publicFiles,{mode:0o755});
const override=join(temporary,'compose.json');
const compose=['compose','-p','myscoutee-template-dev','-f',join(root,'server/docker-compose-dev.yml')];
const evidence={point:30,status:'FAIL',version};
let fixture,receiver,browser,page,replaced=false,log;
async function docker(args){return run('docker',args,{maxBuffer:1024*1024});}
async function healthy(){
  const end=Date.now()+180000;
  while(Date.now()<end){
    const result=await docker(['inspect','--format','{{.State.Health.Status}}','myscoutee-template-dev-profilemanagement']);
    if(result.stdout.trim()==='healthy')return;
    await new Promise(resolve=>setTimeout(resolve,2000));
  }
  throw Error('The existing monolith did not become healthy');
}
try{
  fixture=await stageOperatorConfigurationFixture(baseUrl);
  const email=await fixture.query(`db.users.findOne({userId:${JSON.stringify(fixture.ids[0])}}).email`);
  await run('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-days','1','-subj','/CN=release-simulator','-addext','subjectAltName=DNS:release-simulator','-keyout',join(temporary,'tls.key'),'-out',join(publicFiles,'tls.pem')]);
  await chmod(join(temporary,'tls.key'),0o600);
  await copyFile(join(release,'release-public.pem'),join(publicFiles,'release-public.pem'));
  await chmod(join(publicFiles,'release-public.pem'),0o644);
  await writeFile(override,JSON.stringify({services:{profilemanagement:{
    environment:{OPERATOR_USER:email,OPERATOR_UPDATE_ENABLED:'false',OPERATOR_UPDATE_INCLUDE_PRERELEASES:'true',
      OPERATOR_UPDATE_GITHUB_REPOSITORY:index.repository,OPERATOR_UPDATE_RELEASE_API_ORIGIN:index.origin,
      OPERATOR_UPDATE_RELEASE_DOWNLOAD_ORIGIN:index.origin,OPERATOR_UPDATE_TLS_CA_CERTIFICATE:'/qa-release/tls.pem',
      OPERATOR_UPDATE_RELEASE_PUBLIC_KEY_PATH:'/qa-release/release-public.pem'},
    extra_hosts:{'release-simulator':'host-gateway'},volumes:[publicFiles+':/qa-release:ro']
  }}}));
  log=await open('.e2e-artifacts/template-regression-release-server.log','a');
  receiver=spawn('python3',['tests/qa/categories/Operator/tools/operator-update-release-server.py','serve','--root',catalog,'--bind','0.0.0.0','--certificate',join(publicFiles,'tls.pem'),'--tls-key',join(temporary,'tls.key')],{stdio:['ignore',log.fd,log.fd]});
  await new Promise(resolve=>setTimeout(resolve,500));
  assert.equal(receiver.exitCode,null,'The owned catalog server must stay running');
  console.log('Point 30: replacing the stopped monolith owner with the release-fixture configuration');
  await docker([...compose,'stop','profilemanagement']);replaced=true;
  await docker([...compose,'-f',override,'up','-d','--no-build','--no-deps','profilemanagement']);
  await healthy();
  browser=await launchChromeBrowser({baseUrl});page=await browser.newPage();
  await page.connection.send('Page.bringToFront');
  await enter(page,{id:fixture.ids[0],role:'operator'});
  await page.clickVisible('.operator-actions [data-guide-item="updates"]');
  await page.waitFor('signed fixture discovered',()=>page.evaluate(version=>document.querySelector('.operator-action-popup__version--available')?.textContent.trim()===version,version),45000);
  evidence.signedDiscovery=true;
  await page.clickVisible('app-operator-update-popup > app-popup .ui-popup__header .ui-popup__close');
  await page.waitFor('persisted release notification',async()=>await fixture.query(`db.userNotifications.countDocuments({recipientUserId:${JSON.stringify(fixture.ids[0])},kind:'release-available',sourceId:${JSON.stringify(version)}})`)===1,45000);
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('.navigator-header-actions [data-guide-item="notifications"]');
  await page.waitFor('native release notification',()=>page.evaluate(version=>document.querySelector('app-notification-center-popup')?.innerText.includes(version),version));
  evidence.operatorNotification=true;
  const screenshot=await page.connection.send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false,fromSurface:false});
  await writeFile('.e2e-artifacts/functional/template-regression/release-notice.png',Buffer.from(screenshot.data,'base64'));
  assert(![...page.requests.values()].some(request=>request.method==='POST'&&new URL(request.url).pathname.startsWith('/api/operator/updates')),'Discovery must not approve an installation');
  evidence.installRequests=0;
  // Let the existing outbox owner finish this fixture before removing its rows.
  await page.waitFor('owned outbox drained',async()=>await fixture.query(`db.notificationOutbox.countDocuments({recipientUserIds:${JSON.stringify(fixture.ids[0])},status:{$in:['pending','running']}})`)===0,65000);
  evidence.status='PASS';
}catch(error){evidence.error=error.stack;process.exitCode=1;
  if(page)evidence.failure=await page.evaluate(()=>({path:location.pathname,text:document.body.innerText.slice(-3000)})).catch(()=>null);
}finally{
  if(page)await page.close();if(browser)await browser.close();
  try{
    if(replaced){
      console.log('Point 30: restoring the original stopped monolith owner');
      await docker([...compose,'-f',override,'stop','profilemanagement']);
      if(receiver){receiver.kill('SIGTERM');await new Promise(resolve=>receiver.once('exit',resolve));receiver=null;}
      await docker([...compose,'up','-d','--no-build','--no-deps','profilemanagement']);await healthy();
      evidence.originalRuntimeRestored=true;
    }
    if(fixture)evidence.cleanup=await fixture.close();
  }finally{
    if(receiver)receiver.kill('SIGTERM');if(log)await log.close();
    await rm(temporary,{recursive:true,force:true});
    await writeFile('.e2e-artifacts/functional/template-regression/release-notice.json',JSON.stringify(evidence,null,2)+'\n');
    console.log(JSON.stringify(evidence));
  }
}
