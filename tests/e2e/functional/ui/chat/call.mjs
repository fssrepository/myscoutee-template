import { assert } from '@fssrepository/myscoutee-e2e/shared/assertions.mjs';

// Observe browser-owned WebRTC objects; exercise the production controls, not Angular debug APIs.
export async function trackCallMedia(page) {
 await page.addInitScript(`(() => {
  window.__qaCallPeers=[];window.__qaCallTracks=[];
  window.RTCPeerConnection=new Proxy(window.RTCPeerConnection,{construct(target,args,newTarget){const peer=Reflect.construct(target,args,newTarget);window.__qaCallPeers.push(peer);return peer;}});
  const media=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
  navigator.mediaDevices.getUserMedia=async constraints=>{const stream=await media(constraints);window.__qaCallTracks.push(...stream.getTracks());return stream;};
 })();`);
}
export async function mediaStats(page) {
 return page.evaluate(async()=>{
  const result={audioBytes:0,videoFrames:0,connected:0,relay:false};
  for(const peer of window.__qaCallPeers){
   if(peer.connectionState!=='connected')continue;result.connected++;
   const stats=await peer.getStats();
   stats.forEach(stat=>{
    if(stat.type==='inbound-rtp'&&stat.kind==='audio')result.audioBytes+=stat.bytesReceived??0;
    if(stat.type==='inbound-rtp'&&stat.kind==='video')result.videoFrames+=stat.framesDecoded??0;
    if(stat.type==='transport'&&stat.selectedCandidatePairId){const pair=stats.get(stat.selectedCandidatePairId);result.relay ||= stats.get(pair?.localCandidateId)?.candidateType==='relay'||stats.get(pair?.remoteCandidateId)?.candidateType==='relay';}
   });
  }
  return result;
 });
}
export async function callDiagnostics(page) {
 return page.evaluate(async()=>({transport:window.__qaChatTransport,tracks:window.__qaCallTracks?.map(t=>({kind:t.kind,enabled:t.enabled,muted:t.muted,state:t.readyState})),peers:await Promise.all((window.__qaCallPeers??[]).map(async peer=>({
  state:peer.connectionState,signaling:peer.signalingState,ice:peer.iceConnectionState,
  transceivers:peer.getTransceivers().map(t=>({mid:t.mid,direction:t.direction,currentDirection:t.currentDirection,sending:t.sender.track?{kind:t.sender.track.kind,state:t.sender.track.readyState,enabled:t.sender.track.enabled}:null,receiving:{kind:t.receiver.track.kind,muted:t.receiver.track.muted,state:t.receiver.track.readyState}})),
  rtp:[...await peer.getStats()].map(([,v])=>v).filter(v=>['inbound-rtp','outbound-rtp'].includes(v.type)).map(v=>({type:v.type,kind:v.kind,bytesReceived:v.bytesReceived,bytesSent:v.bytesSent,framesDecoded:v.framesDecoded,packetsReceived:v.packetsReceived,packetsSent:v.packetsSent}))
 })))}));
}
export async function assertChatCall(sender,receiver,{holdMs=0}={}) {
 const start=Date.now();
 await sender.waitFor('call available',()=>sender.evaluate(()=>document.querySelector('[data-guide-control="chat-call"]')?.disabled===false));
 await sender.clickVisible('[data-guide-control="chat-call"]');
 await receiver.waitForSelector('[data-call-state="incoming"]');
 await receiver.clickVisible('[data-call-accept]');
 for(const page of [sender,receiver]){
  await page.waitForSelector('[data-call-state="active"]',{timeoutMs:45000});
  await page.waitFor('incoming audio packets',async()=>{const stats=await mediaStats(page);return stats.connected===1&&stats.audioBytes>0&&!stats.relay;},45000);
  await page.clickVisible('[data-call-camera]');
 }
 for(const page of [sender,receiver])await page.waitFor('decoded peer video frames',async()=>{const stats=await mediaStats(page);return stats.videoFrames>0&&!stats.relay;},45000);
 const before=await Promise.all([sender,receiver].map(mediaStats));
 if(holdMs){
  for(const page of [sender,receiver])await page.evaluate(()=>{
   window.__qaCallSocket=[...window.__qaChatSockets].find(socket=>socket.readyState===WebSocket.OPEN);
   if(!window.__qaCallSocket)throw Error('Call requires an open chat socket');
  });
  console.log(`Keeping the real call active for ${holdMs}ms across the delegation expiry.`);
  await new Promise(resolve=>setTimeout(resolve,holdMs));
  for(const [index,page] of [sender,receiver].entries()){
   const after=await mediaStats(page);
   assert(after.audioBytes>before[index].audioBytes&&after.videoFrames>before[index].videoFrames,'Audio and video must continue during the long call');
  }
 }
 for(const page of [sender,receiver]){
  const state=await callDiagnostics(page);
  const peers=state.peers.filter(peer=>peer.state!=='closed');
  assert(peers.length===1&&peers[0].transceivers.length===2,'Each peer negotiates exactly one audio and one video channel');
  assert(peers[0].transceivers.every(t=>t.mid!==null&&t.currentDirection==='sendrecv'),'Both media channels must negotiate sending and receiving');
 }
 await sender.clickVisible('[data-call-mute]');
 await sender.waitForSelector('[data-call-mute][aria-pressed="true"]');
 assert(await sender.evaluate(()=>window.__qaCallTracks.filter(t=>t.kind==='audio'&&t.readyState==='live').every(t=>!t.enabled)),'Mute disables the sending microphone');
 await sender.clickVisible('[data-call-mute]');
 await sender.waitForSelector('[data-call-mute][aria-pressed="false"]');
 await sender.clickVisible('[data-call-camera]');
 await sender.waitForSelector('[data-call-camera][aria-pressed="true"]');
 assert(await sender.evaluate(()=>window.__qaCallTracks.filter(t=>t.kind==='video').every(t=>t.readyState==='ended')),'Camera off releases the device');
 if(holdMs){
  // Camera changes and leave use the production signaling transport after its
  // initial 30-second delegation expired; a reconnect must not mask renewal.
  for(const page of [sender,receiver])assert(await page.evaluate(()=>window.__qaCallSocket.readyState===WebSocket.OPEN&&window.__qaChatSockets.has(window.__qaCallSocket)),'Long call preserves its original signaling socket');
 }
 for(const page of [sender,receiver])await page.clickVisible('[data-call-hangup]');
 for(const page of [sender,receiver]){
  await page.waitFor('call media teardown',()=>page.evaluate(()=>window.__qaCallTracks.every(t=>t.readyState==='ended')&&window.__qaCallPeers.every(p=>p.connectionState==='closed')));
  assert(!await page.isSelectorVisible('[data-chat-call]'),'Leave closes the call popup');
 }
 return {twoPartyAudio:true,twoPartyVideo:true,directMedia:true,mute:true,cameraOff:true,mediaReleased:true,holdMs,received:before,elapsedMs:Date.now()-start};
}
