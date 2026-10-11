import { randomUUID } from 'node:crypto';
import { queryE2eMongo } from '../../../shared/mongo-fixture.mjs';

// The native Mongo fixture transport is reused; only the template actors/data differ.
export async function stageChatFixture() {
  if (!process.env.E2E_MONGO_CONTAINER?.startsWith('myscoutee-template-')) throw new Error('Select the template Mongo container explicitly.');
  const database = process.env.E2E_DATABASE ?? 'demo_db';
  if (!['demo_db','e2e_db'].includes(database)) throw new Error('Chat fixtures require an isolated demo/E2E database.');
  const run = randomUUID();
  const ids = [`qa-template-chat-${run}-a`,`qa-template-chat-${run}-b`];
  const names = ['QA Template Sender','QA Template Receiver'];
  const chatId = 'c-contact-' + Buffer.from([...ids].sort().join('\n')).toString('base64url');
  const ctx = {vars:{e2eDatabase:database}};
  const query = expression => queryE2eMongo(ctx,expression);
  await query(`(()=>{
    const ids=${JSON.stringify(ids)}, names=${JSON.stringify(names)}, chatId=${JSON.stringify(chatId)}, run=${JSON.stringify(run)};
    if(db.users.countDocuments({userId:{$in:ids}})||db.chats.countDocuments({id:chatId}))throw Error('Owned fixture already exists');
    const source=db.users.findOne({userId:'100000000000000000000001'});
    if(!source || source.name!=='Alex Morgan')throw Error('Template baseline is missing');
    for(let i=0;i<ids.length;i++){
      const user={...source,_id:ids[i],userId:ids[i],syncKey:'qa-chat:'+ids[i],name:names[i],initials:i?'QR':'QS',email:ids[i]+'@template.test',admin:false,operator:false,activeSessions:[],devices:[],qaFixture:run,
        activities:{chats:1,notifications:0,chat:{all:1,contacts:1,appSupport:0}}};
      db.users.insertOne(user);
      db.chats.insertOne({_id:'qa-chat-owner:'+ids[i],id:chatId,ownerUserId:ids[i],channelType:'contact',memberIds:ids,title:names[1-i],avatar:i?'QS':'QR',lastMessage:'',lastSenderId:'',unread:0,revision:1,dateIso:new Date().toISOString(),qaFixture:run});
    }
    const pair=[...ids].sort();
    db.contactChatAccess.insertOne({_id:pair[0].length+':'+pair.join(''),leftUserId:pair[0],rightUserId:pair[1],requestedBy:ids[0],status:'approved',requestedAtIso:new Date().toISOString(),decidedAtIso:new Date().toISOString(),version:0,qaFixture:run});
    return {ready:true};
  })()`);
  return {ids,names,chatId,query,async close(){
    return query(`(()=>{
      const ids=${JSON.stringify(ids)}, chatId=${JSON.stringify(chatId)}, run=${JSON.stringify(run)};
      db.chatMessages.deleteMany({chatId});
      db.chats.deleteMany({id:chatId,ownerUserId:{$in:ids}});
      db.contactChatAccess.deleteMany({qaFixture:run});
      for(const collection of ['privacyConsents','userLoginAudits','userSessionActions','userSessionStates','userNotifications','notificationOutbox']){
        db.getCollection(collection).deleteMany({$or:[{userId:{$in:ids}},{recipientUserId:{$in:ids}},{accountUserId:{$in:ids}},{recipientUserIds:{$in:ids}}]});
      }
      db.users.deleteMany({userId:{$in:ids}});
      return {usersRemaining:db.users.countDocuments({userId:{$in:ids}}),chatsRemaining:db.chats.countDocuments({id:chatId}),messagesRemaining:db.chatMessages.countDocuments({chatId})};
    })()`);
  }};
}
