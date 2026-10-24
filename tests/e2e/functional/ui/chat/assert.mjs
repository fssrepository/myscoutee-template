import { assert } from '@fssrepository/myscoutee-e2e/shared/assertions.mjs';

// Track only this isolated test browser's actual chat sockets. The optimized
// application deliberately has no Angular debug API or test-only accessors.
export async function trackChatTransport(page) {
 await page.addInitScript(`(() => {
  const sockets = new Set();
  window.__qaChatSockets = sockets;
  window.__qaChatTransport = [];
  const callIds = new Map();
  const record = (direction, raw) => {
   try {
    const event=JSON.parse(raw), call=event.call??event;
    if(event.type!=='call')return;
    if(call.callId&&!callIds.has(call.callId))callIds.set(call.callId,callIds.size+1);
    window.__qaChatTransport.push({at:Math.round(performance.now()),direction,kind:call.kind??call.action,call:callIds.get(call.callId),epoch:call.epoch,participants:call.participants?.length,error:call.error});
   }catch{}
  };
  window.WebSocket = new Proxy(window.WebSocket, {
   construct(target, args, newTarget) {
    const socket = Reflect.construct(target, args, newTarget);
    if (new URL(args[0], location.href).pathname === '/api/activities/chats/ws') {
     sockets.add(socket);
     socket.addEventListener('message', event => record('received',event.data));
     const send=socket.send.bind(socket);
     socket.send=data=>{record('sent',data);return send(data);};
     socket.addEventListener('close', event => {sockets.delete(socket);window.__qaChatTransport.push({at:Math.round(performance.now()),closed:event.code});});
    }
    return socket;
   }
  });
 })();`);
}

export async function openConversation(page, peerName) {
 await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
 await page.clickVisible('.user-menu-content [data-guide-item="chat"]');
 await page.waitForText(peerName);
 await page.clickByText('single-row article',peerName);
 await page.waitForSelector('.chat-compose-box textarea');
}

export async function messageAction(page, id, action) {
 const row=`[data-chat-message-id="${id}"]`;
 if(!await page.isSelectorVisible(row+' .chat-message-tools')) await page.clickVisible(row+' .chat-bubble');
 if(action==='reply'){await page.clickVisible(row+' button[aria-label="Reply"]');return;}
 await page.clickVisible(row+' app-menu-trigger button');
 for(const omitted of ['Report','Invite'])assert(!await page.isSelectorVisible(`.app-menu__panel button[aria-label="${omitted}"]`),'Omitted '+omitted+' action must stay absent');
 await page.clickVisible(`[data-guide-item="chat-message-${action}-${id}"]`);
}

export async function assertMessageTools(page, id) {
 const row=`[data-chat-message-id="${id}"]`;
 const pageReads=()=>page.network.filter(request=>/\/activities\/chats\/[^/]+\/messages(?:\?|$)/.test(request.url)).length;
 const beforeReads=pageReads();
 await messageAction(page,id,'pin');await page.waitForSelector(row+' .chat-pin-badge');
 await page.clickVisible('[data-guide-item="chat-header-pins"]');
 await page.waitForSelector('.chat-pinned-message');await page.clickVisible('.chat-pinned-message');
 if(!await page.isSelectorVisible(row+' .chat-message-tools'))await page.clickVisible(row+' .chat-bubble');
 await page.clickVisible(row+' button[aria-label="React"]');
 await page.clickByText(row+' .chat-quick-reactions button','👍');
 await page.waitForSelector(row+' .chat-reaction-summary');
 await page.clickVisible(row+' .chat-reaction-summary');await page.waitForText('Message reactions');
 await page.clickVisible('app-popup [aria-label="Close reactions"]');
 assert(pageReads()===beforeReads,'Pin, reaction and message details must patch the loaded thread without another page request');
 return {pinned:true,pinNavigation:true,reaction:true,reactionDetails:true,noExtraPageRequest:true};
}

export async function assertPairedChat(sender, receiver) {
 const first='Template chat send '+Date.now();
 await sender.fillVisible('.chat-compose-box textarea',first);
 await sender.clickVisible('.chat-send-btn');
 await sender.waitForText(first);await receiver.waitForText(first);
 const id=await sender.evaluate(text=>[...document.querySelectorAll('[data-chat-message-id]')].find(node=>node.innerText.includes(text))?.getAttribute('data-chat-message-id'),first);
 assert(id,'Sent message has a stable server identity');
 await sender.waitFor('peer read receipt',()=>sender.evaluate(id=>[...document.querySelectorAll(`[data-chat-message-id="${id}"] .chat-reader`)].some(reader=>reader.textContent.trim()==='QR'||reader.querySelector('img')?.alt==='QR'),id));
 for(const page of [sender,receiver])assert(await page.evaluate(id=>document.querySelectorAll(`[data-chat-message-id="${id}"]`).length,id)===1,'WebSocket echo must not duplicate a sent message');
 const edited=first+' edited';
 await messageAction(sender,id,'edit');
 await sender.fillVisible('.chat-compose-box textarea',edited);await sender.clickVisible('.chat-send-btn');
 await sender.waitForText(edited);await receiver.waitForText(edited);
 assert(!await receiver.isSelectorVisible(`[data-chat-message-id="${id}"] button[aria-label="Edit"]`),'Receiver must not get sender edit controls');
 await assertMessageTools(sender,id);await receiver.waitForSelector(`[data-chat-message-id="${id}"] .chat-pin-badge`);await receiver.waitForSelector(`[data-chat-message-id="${id}"] .chat-reaction-summary`);
 // Interrupt the real client WebSocket, then require a new OPEN connection.
 await sender.evaluate(()=>{
  const socket=[...window.__qaChatSockets].find(socket=>socket.readyState===WebSocket.OPEN);
  if(!socket||socket.readyState!==WebSocket.OPEN)throw new Error('Live WebSocket missing before reconnect test');
  window.__qaClosedChatSocket=socket;socket.close(4001,'QA transport interruption');
 });
 await sender.waitFor('new open chat WebSocket',()=>sender.evaluate(()=>{
  return [...window.__qaChatSockets].some(socket=>socket!==window.__qaClosedChatSocket&&socket.readyState===WebSocket.OPEN);
 }));
 const reply='Reply after reconnect '+Date.now();
 await messageAction(receiver,id,'reply');
 await receiver.fillVisible('.chat-compose-box textarea',reply);await receiver.clickVisible('.chat-send-btn');
 await sender.waitForText(reply);await receiver.waitForText(reply);
 const order=await sender.evaluate(()=>[...document.querySelectorAll('[data-chat-message-id]')].map(node=>({text:node.innerText,y:node.getBoundingClientRect().top})).sort((a,b)=>a.y-b.y).map(item=>item.text));
 assert(order.findIndex(text=>text.includes(edited))<order.findIndex(text=>text.includes(reply)),'Messages must appear oldest to newest from top to bottom');
 await sender.waitForSelector('.chat-message-reply-context');
 await sender.navigate('/home?page=chat');await sender.waitForText('QA Template Receiver');await sender.clickByText('single-row article','QA Template Receiver');
 await sender.waitForSelector(`[data-chat-message-id="${id}"] .chat-pin-badge`);await sender.waitForSelector(`[data-chat-message-id="${id}"] .chat-reaction-summary`);await sender.waitForSelector('.chat-message-reply-context');
 await messageAction(sender,id,'pin');await sender.waitFor('unpin persisted',()=>sender.evaluate(id=>!document.querySelector(`[data-chat-message-id="${id}"] .chat-pin-badge`),id));
 await messageAction(sender,id,'unsend');
 await sender.waitForSelector(`[data-chat-message-id="${id}"] .chat-line-deleted`);await receiver.waitForSelector(`[data-chat-message-id="${id}"] .chat-line-deleted`);
 await sender.navigate('/home?page=chat');await sender.waitForText('QA Template Receiver');
 await sender.clickByText('single-row article','QA Template Receiver');
 await sender.waitForText(reply);await sender.waitForSelector(`[data-chat-message-id="${id}"] .chat-line-deleted`);
 assert(!await sender.isSelectorVisible('.chat-composer-app-menu'),'The omitted bottom add-item menu must not render');
 const actions=await sender.evaluate(()=>[...document.querySelectorAll('.event-chat-popup-shell .ui-popup__toolbar-group--end button')].map(button=>({control:button.getAttribute('data-guide-control'),item:button.getAttribute('data-guide-item')})));
 assert(actions.length===3&&actions.some(action=>action.control==='chat-call')&&actions.some(action=>action.item==='chat-header-pins'),'Native right-aligned header retains call, members and pins');
 return {sent:true,edited:true,peerReceived:true,readReceipt:true,noDuplicateEcho:true,reconnected:true,replyAfterReconnect:true,replyReference:true,pinReactionAndReplyReload:true,unpin:true,chronologicalOrder:true,deleted:true,reloadPersisted:true};
}
