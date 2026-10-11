import { assert } from '../../../shared/assertions.mjs';

export async function openConversation(page, peerName) {
 await page.clickVisible('.member-navigation [data-guide-item="chat"]');
 await page.waitForText(peerName);
 await page.clickByText('single-row article',peerName);
 await page.waitForSelector('.chat-composer textarea');
}

export async function assertPairedChat(sender, receiver) {
 const first='Template chat send '+Date.now();
 await sender.fillVisible('.chat-composer textarea',first);
 await sender.clickVisible('.chat-composer .send');
 await sender.waitForText(first);await receiver.waitForText(first);
 const id=await sender.evaluate(text=>[...document.querySelectorAll('[data-chat-message-id]')].find(node=>node.innerText.includes(text))?.getAttribute('data-chat-message-id'),first);
 assert(id,'Sent message has a stable server identity');
 await sender.waitForSelector(`[data-chat-message-id="${id}"] .read`);
 for(const page of [sender,receiver])assert(await page.evaluate(id=>document.querySelectorAll(`[data-chat-message-id="${id}"]`).length,id)===1,'WebSocket echo must not duplicate a sent message');
 const edited=first+' edited';
 await sender.clickVisible(`[data-chat-message-id="${id}"] button[aria-label="Edit"]`);
 await sender.fillVisible('.chat-composer textarea',edited);await sender.clickVisible('.chat-composer .send');
 await sender.waitForText(edited);await receiver.waitForText(edited);
 assert(!await receiver.isSelectorVisible(`[data-chat-message-id="${id}"] button[aria-label="Edit"]`),'Receiver must not get sender edit controls');
 // Interrupt the real client WebSocket, then require a new OPEN connection.
 await sender.evaluate(()=>{
  const component=window.ng.getComponent(document.querySelector('app-template-chat'));
  const socket=component.chats.http.socket;
  if(!socket||socket.readyState!==WebSocket.OPEN)throw new Error('Live WebSocket missing before reconnect test');
  window.__qaClosedChatSocket=socket;socket.close(4001,'QA transport interruption');
 });
 await sender.waitFor('new open chat WebSocket',()=>sender.evaluate(()=>{
  const socket=window.ng.getComponent(document.querySelector('app-template-chat')).chats.http.socket;
  return socket&&socket!==window.__qaClosedChatSocket&&socket.readyState===WebSocket.OPEN;
 }));
 const reply='Reply after reconnect '+Date.now();
 await receiver.fillVisible('.chat-composer textarea',reply);await receiver.clickVisible('.chat-composer .send');
 await sender.waitForText(reply);await receiver.waitForText(reply);
 const order=await sender.evaluate(()=>[...document.querySelectorAll('[data-chat-message-id]')].map(node=>({text:node.innerText,y:node.getBoundingClientRect().top})).sort((a,b)=>a.y-b.y).map(item=>item.text));
 assert(order.findIndex(text=>text.includes(edited))<order.findIndex(text=>text.includes(reply)),'Messages must appear oldest to newest from top to bottom');
 await sender.clickVisible(`[data-chat-message-id="${id}"] button[aria-label="Delete"]`);
 await sender.waitForSelector(`[data-chat-message-id="${id}"] .deleted`);await receiver.waitForSelector(`[data-chat-message-id="${id}"] .deleted`);
 await sender.navigate('/home?page=chat');await sender.waitForText('QA Template Receiver');
 await sender.clickByText('single-row article','QA Template Receiver');
 await sender.waitForText(reply);await sender.waitForSelector(`[data-chat-message-id="${id}"] .deleted`);
 assert(!await sender.isSelectorVisible('.chat-composer-app-menu'),'The omitted bottom add-item menu must not render');
 assert(await sender.evaluate(()=>document.querySelectorAll('.chat-header button').length)===1,'Conversation header has only the collapse control');
 return {sent:true,edited:true,peerReceived:true,readReceipt:true,noDuplicateEcho:true,reconnected:true,replyAfterReconnect:true,chronologicalOrder:true,deleted:true,reloadPersisted:true};
}
