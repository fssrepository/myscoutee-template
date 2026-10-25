import assert from 'node:assert/strict';
import {stageAdminFixture} from '../../ui/admin/fixture.mjs';
import {nativeDemoSessions} from '@fssrepository/myscoutee-e2e/shared/native-demo-sessions.mjs';
import {createHttpClient} from '@fssrepository/myscoutee-e2e/shared/http.mjs';
const baseUrl=process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=await stageAdminFixture(baseUrl),sessions=nativeDemoSessions(baseUrl),timings=[];
const http=createHttpClient({baseUrl,headersForRequest:sessions.headersForRequest});
const [reporter,target]=fixture.ids,admin=fixture.adminId;
async function request(actor,path,method='GET',json,status=200){
 const headers={'X-App-Session-Kind':'demo','X-Demo-User-Id':actor};
 const response=await http.request(method,'/api'+path,{headers,json,expectedStatus:status});
 timings.push({method,path:path.split('?')[0].replaceAll(fixture.chatId,'<chat>').replaceAll(target,'<target>').replaceAll(reporter,'<reporter>'),ms:Math.round(response.durationMs),status:response.status});
 return response.data;
}
try{
 const settings=await request(admin,'/admin/client-api/settings');
 assert.equal(settings.baseUrl,'/api/admin-client/v1');assert(Array.isArray(settings.tokens));assert(settings.mcp);
 await request(target,'/admin/client-api/settings','GET',undefined,403);
 const omitted=[
  ['/admin/dashboard?adminUserId='+admin,'GET'],
  ['/auth/me/report-user','POST',{userId:reporter,targetUserId:target,reason:'omitted'}],
  ['/auth/me/feedback','POST',{userId:reporter,details:'omitted'}],
  [`/admin/users/${target}/warn`,'POST',{adminUserId:admin}],
  [`/admin/users/${target}/block`,'POST',{adminUserId:admin}],
  [`/admin/users/${target}/unblock`,'POST',{adminUserId:admin}],
  ['/admin/reports/omitted/resolve','POST',{adminUserId:admin,resolved:true}],
  ['/admin/feedback/omitted/resolve','POST',{adminUserId:admin,resolved:true}],
  [`/activities/chats/${fixture.chatId}/support-case?userId=${admin}`,'POST',{userId:admin,action:'pick'}]
 ];
 for(const [path,method,body] of omitted)await request(admin,path,method,body,404);
 const profile=await request(target,'/auth/me');assert.equal(profile.id,target);assert.equal(profile.profileStatus,'public');
 const message=await request(target,`/activities/chats/${encodeURIComponent(fixture.chatId)}/messages?userId=${target}`,'POST',{text:'Retained personal chat',attachments:[]});assert(message.id);
 const messages=await request(reporter,`/activities/chats/${encodeURIComponent(fixture.chatId)}/messages?userId=${reporter}`);assert(messages.items.some(item=>item.id===message.id));
 const collections=await fixture.query('db.getCollectionNames()');assert(!collections.includes('userReports'));assert(!collections.includes('userFeedback'));
 console.log(JSON.stringify({result:'PASS',checks:'Admin API retained and member denied; report/feedback/support mutations absent; personal chat still persists and reads; omitted collections are not initialized',timings},null,2));
}finally{try{await sessions.close();}finally{console.log('Owned admin fixture cleanup',await fixture.close());}}
