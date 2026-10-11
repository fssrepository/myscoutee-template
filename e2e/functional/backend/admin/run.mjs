import assert from 'node:assert/strict';
import {stageAdminFixture} from '../../ui/admin/fixture.mjs';
import {nativeDemoSessions} from '../../../shared/native-demo-sessions.mjs';
const baseUrl=process.env.E2E_SERVER_BASE_URL??'http://localhost';
const fixture=await stageAdminFixture(baseUrl),sessions=nativeDemoSessions(baseUrl),timings=[];
const [reporter,target]=fixture.ids,admin=fixture.adminId;
async function request(actor,path,method='GET',json,status=200){
 const url=baseUrl+'/api'+path,headers={'X-App-Session-Kind':'demo','X-Demo-User-Id':actor,'Content-Type':'application/json'};
 Object.assign(headers,await sessions.headersForRequest(method,url,{headers}));
 const start=performance.now(),response=await fetch(url,{method,headers,...(json===undefined?{}:{body:JSON.stringify(json)})});
 timings.push({method,path:path.split('?')[0].replaceAll(fixture.chatId,'<chat>').replaceAll(target,'<target>').replaceAll(reporter,'<reporter>'),ms:Math.round(performance.now()-start),status:response.status});
 assert.equal(response.status,status,path);const text=await response.text();return text?JSON.parse(text):null;
}
try{
 assert.equal((await fetch(baseUrl+'/api/admin/dashboard')).status,401);
 await request(target,'/admin/dashboard','GET',undefined,403);
 const initial=await request(admin,'/admin/dashboard?adminUserId='+admin);assert.equal(initial.activeAdmin.id,admin);
 await request(admin,'/admin/dashboard?adminUserId='+target,'GET',undefined,403);
 const message=await request(target,`/activities/chats/${encodeURIComponent(fixture.chatId)}/messages?userId=${target}`,'POST',{text:'QA report evidence',attachments:[]});
 const report={userId:reporter,targetUserId:target,handle:'QA receiver',reason:'inappropriate',details:'QA report',chatId:fixture.chatId,messageId:message.id,sourceText:'Forged client text',eventId:'omitted-event'};
 assert.equal((await request(reporter,'/auth/me/report-user','POST',{...report,chatId:'unreadable-chat'})).submitted,false);
 assert.equal((await request(reporter,'/auth/me/report-user','POST',report)).submitted,true);
 assert.equal((await request(reporter,'/auth/me/report-user','POST',report)).submitted,true);
 // Native profile selection uses the authenticated actor, ignoring a forged body userId.
 assert.equal((await request(target,'/auth/me/report-user','POST',report)).submitted,false);
 const saved=await fixture.query(`db.userReports.find({userId:${JSON.stringify(reporter)}}).toArray()`);assert.equal(saved.length,1);assert.equal(saved[0].sourceText,'QA report evidence');assert(!saved[0].eventId);const reportId=saved[0]._id.$oid??saved[0]._id;
 const dashboard=await request(admin,'/admin/dashboard?adminUserId='+admin);const reported=dashboard.reportedUsers.find(x=>x.userId===target);assert(reported);assert.equal(reported.reports[0].chatMessages.find(x=>x.id===message.id).text,'QA report evidence');
 await request(target,`/admin/users/${target}/block`,'POST',{adminUserId:admin},403);
 await request(admin,`/admin/users/${target}/warn`,'POST',{adminUserId:admin,reportId,message:'QA warning'});
 let support=await fixture.query(`db.chats.find({id:${JSON.stringify('c-support-admin-'+target)}}).toArray()`);assert.equal(support.length,2);assert.deepEqual(new Set(support.map(x=>x.ownerUserId)),new Set([target,admin]));
 await request(admin,`/admin/users/${target}/block`,'POST',{adminUserId:admin,message:'QA block'});
 assert.equal(await fixture.query(`db.users.findOne({userId:${JSON.stringify(target)}}).profileStatus`),'blocked');
 await request(admin,`/admin/users/${target}/unblock`,'POST',{adminUserId:admin});assert.equal(await fixture.query(`db.users.findOne({userId:${JSON.stringify(target)}}).profileStatus`),'public');
 await request(admin,`/admin/reports/${reportId}/resolve`,'POST',{adminUserId:admin,resolved:true});
 assert((await fixture.query(`db.userReports.findOne({userId:${JSON.stringify(reporter)}})`)).resolvedAtIso);
 await request(admin,`/admin/reports/${reportId}/resolve`,'POST',{adminUserId:admin,resolved:false});
 assert(!(await fixture.query(`db.userReports.findOne({userId:${JSON.stringify(reporter)}})`)).resolvedAtIso);
 assert.equal((await request(reporter,'/auth/me/feedback','POST',{userId:reporter,category:'suggestion',subject:'QA feedback',details:'QA details'})).submitted,true);
 const feedback=(await fixture.query(`db.userFeedback.find({userId:${JSON.stringify(reporter)}}).toArray()`))[0];assert(feedback);
 await request(admin,`/admin/feedback/${feedback._id.$oid??feedback._id}/resolve`,'POST',{adminUserId:admin,resolved:true});
 assert((await fixture.query(`db.userFeedback.findOne({userId:${JSON.stringify(reporter)}})`)).resolvedAtIso);
 const indexes=await fixture.query(`({reports:db.userReports.getIndexes().map(x=>x.name),feedback:db.userFeedback.getIndexes().map(x=>x.name)})`);assert(indexes.reports.includes('user_report_sync_key'));assert(indexes.feedback.includes('user_feedback_sync_key'));
 console.log(JSON.stringify({result:'PASS',checks:'Admin/member/anonymous/actor isolation, chat evidence, duplicate report, warning/support chat, block/unblock, resolve/reopen, feedback resolve, required indexes',timings},null,2));
}finally{try{await sessions.close();}finally{console.log('Owned admin fixture cleanup',await fixture.close());}}
