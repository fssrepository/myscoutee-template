import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
import {stageChatFixture} from '../../../ui/chat/fixture.mjs';
import {nativeDemoSessions} from '../../../../shared/native-demo-sessions.mjs';

const base=process.env.E2E_SERVER_BASE_URL;
assert(base && ['kvm','docker'].includes(process.env.E2E_MONGO_TRANSPORT));
const fixture=await stageChatFixture(), sessions=nativeDemoSessions(base);
const key=`private/documents/${fixture.ids[0]}/qa-private/owned.txt`,url='/api/media/private?key='+key;
const evidence={task:'12.4',target:process.env.E2E_MONGO_TRANSPORT==='kvm'?'template-kvm':'template-monolith',checks:{}};
function object(action){
 const code=`import sys,shlex,os,subprocess
sys.path.insert(0,'microservices/tools')
import workloads as w
key,action=sys.argv[1:]
assert key.startswith('private/documents/qa-template-chat-') and key.endswith('/qa-private/owned.txt')
script='''set -eu
export GOMEMLIMIT=48MiB GOMAXPROCS=1
mc alias set qa_private http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
trap 'mc alias remove qa_private >/dev/null; rm -f /tmp/qa-private-owned.txt' EXIT
if [ "$1" = stage ]; then
 printf 'Owned private media qualification' >/tmp/qa-private-owned.txt
 mc cp /tmp/qa-private-owned.txt "qa_private/$MEDIA_BUCKET/$2" >/dev/null
else
 mc rm --force "qa_private/$MEDIA_BUCKET/$2" >/dev/null
fi
'''
if os.environ.get('E2E_MONGO_TRANSPORT')=='kvm':
 w.namespace_owned();w.kube('-n',w.NAMESPACE,'exec','-i','deployment/minio','--','sh','-s','--',action,key,data=script)
else:
 container=os.environ['E2E_MINIO_CONTAINER'];assert container.startswith('myscoutee-template-')
 subprocess.run(['docker','exec','-i',container,'sh','-s','--',action,key],input=script,text=True,check=True,capture_output=True)
`;
 execFileSync('python3',['-c',code,key,action],{stdio:['ignore','pipe','pipe'],timeout:30000});
}
async function request(path,headers={},status=200){const r=await fetch(base+path,{headers,redirect:'manual'});assert.equal(r.status,status,`${path}: ${await (r.status===status?Promise.resolve(''):r.text())}`);return r;}
const headers=[],cookies=[];
let sessionsClosed=false;
try{
 object('stage');
 for(const id of fixture.ids){
  const h=await sessions.headersForRequest('GET',base+'/api/auth/me',{headers:{'X-App-Session-Kind':'demo','X-Demo-User-Id':id}});headers.push(h);
  const r=await request('/api/auth/me',h);const cookie=r.headers.getSetCookie().find(value=>value.startsWith('myscoutee_media_session='));assert(cookie);cookies.push({Cookie:cookie.split(';')[0]});
 }
 await request(url,{},401);
 const own=await request(url,cookies[0]);assert.equal(await own.text(),'Owned private media qualification');
 assert.match(own.headers.get('cache-control'),/no-store/);assert.match(own.headers.get('content-disposition'),/attachment/);
 await request(url,cookies[1],403);
 evidence.checks.ownerAllowedUnrelatedViewerAndAnonymousDenied=true;
 const legacy=await request('/api/media/public?key='+encodeURIComponent(key),{},307);assert.equal(legacy.headers.get('location'),url);
 evidence.checks.legacyPublicRouteCannotBypassAuthorization=true;
 const messageId='qa-private-'+fixture.ids[0];
 await fixture.query(`db.chatMessages.insertOne({_id:${JSON.stringify(messageId)},id:${JSON.stringify(messageId)},chatId:${JSON.stringify(fixture.chatId)},attachments:[{url:${JSON.stringify(url)}}],deletedAtIso:null})`);
 assert.equal(await (await request(url,cookies[1])).text(),'Owned private media qualification');
 await fixture.query(`db.chatMessages.updateOne({_id:${JSON.stringify(messageId)}},{$set:{deletedAtIso:new Date().toISOString()}})`);
 await request(url,cookies[1],403);
 evidence.checks.currentChatAttachmentVisibilityAndDeletion=true;
 for(const kind of ['real','demo']){
  const h=kind==='demo'?{'X-App-Session-Kind':'demo'}:{};
  const values=[];
  for(const lang of ['en','hu','en']){
   const r=await request('/api/i18n/bundle?lang='+lang,{...h,'Accept-Language':lang==='en'?'hu':'en'});
   const bundle=await r.json();assert.equal(bundle.lang,lang);assert(Object.keys(bundle.data).length>100);values.push(bundle.data);
   assert.match(r.headers.get('cache-control'),/no-store/);
  }
  assert.deepEqual(values[0],values[2]);assert.notDeepEqual(values[0],values[1]);
 }
 evidence.checks.realDemoEnglishHungarianEnglishNoCacheLeak=true;
 assert.equal(process.env.E2E_EXPECT_REGION_SECURITY,'false');
 for(const query of ['', '?latitude=52.52&longitude=13.405','?latitude=0&longitude=0']){
  const v=await (await request('/api/auth/eligibility'+query)).json();assert.equal(v.eligible,true);assert.equal(v.securityGateEnabled,false);assert.equal(v.locationRequired,false);
 }
 await request('/api/auth/eligibility?latitude=91&longitude=13',{},400);
 evidence.checks.disabledLocationPolicyAndInvalidCoordinates=true;
 await sessions.close();sessionsClosed=true;
 await request(url,cookies[0],401);
 evidence.checks.logoutRevokesMediaCredential=true;
 console.log('DONE private-media authorization/revocation, live EN/HU switching, disabled location policy.');
}finally{
 try{if(!sessionsClosed)await sessions.close();}finally{try{object('cleanup');}finally{await fixture.close();}}
 evidence.cleanup=true;
 if(process.env.E2E_EVIDENCE_FILE)writeFileSync(process.env.E2E_EVIDENCE_FILE,JSON.stringify(evidence,null,2)+'\n');
}
