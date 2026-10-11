import {stageOperatorConfigurationFixture} from '../operator/fixture.mjs';

export async function stageAdminFixture(baseUrl){
 const base=await stageOperatorConfigurationFixture(baseUrl),adminId=base.ids[0]+'-admin',adminEmail=adminId+'@template.test';
 const owned=[...base.ids,adminId],supportIds=base.ids.map(id=>'c-support-admin-'+id);
 async function cleanup(){
  await base.query(`(()=>{const ids=${JSON.stringify(owned)},support=${JSON.stringify(supportIds)};
   db.userReports.deleteMany({userId:{$in:ids}});db.userFeedback.deleteMany({userId:{$in:ids}});
   db.chatMessages.deleteMany({chatId:{$in:support}});db.chats.deleteMany({id:{$in:support}});
   for(const collection of ['privacyConsents','userLoginAudits','userSessionActions','userSessionStates','userNotifications','notificationOutbox'])db.getCollection(collection).deleteMany({$or:[{userId:${JSON.stringify(adminId)}},{recipientUserId:${JSON.stringify(adminId)}},{accountUserId:${JSON.stringify(adminId)}},{recipientUserIds:${JSON.stringify(adminId)}}]});
   for(const collection of ['integrationTokens','mcpAuthorizations','mcpEventSubscriptions','integrationWebhookDeliveries'])db.getCollection(collection).deleteMany({ownerUserId:{$in:ids}});
   db.users.deleteOne({userId:${JSON.stringify(adminId)}});return {adminRemoved:db.users.countDocuments({userId:${JSON.stringify(adminId)}})===0};})()`);
 }
 try{
  await base.query(`(()=>{const source=db.users.findOne({userId:${JSON.stringify(base.ids[1])}}),id=${JSON.stringify(adminId)};if(!source||db.users.countDocuments({userId:id}))throw Error('Owned admin fixture is unavailable');db.users.insertOne({...source,_id:id,userId:id,syncKey:id,name:'QA Template Admin',initials:'QA',email:${JSON.stringify(adminEmail)},hostTier:'Admin',operator:false,activeSessions:[],activities:{chats:0,notifications:0,chat:{all:0,contacts:0,appSupport:0}}});return {created:true};})()`);
  await base.setAdministratorEmails([adminEmail]);
  return {...base,adminId,adminName:'QA Template Admin',async close(){try{await cleanup();}finally{await base.close();}return {restored:true};}};
 }catch(error){try{await cleanup();}finally{await base.close();}throw error;}
}
