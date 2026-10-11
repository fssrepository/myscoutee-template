import assert from 'node:assert/strict';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from './fixture.mjs';
import { openConversation, assertPairedChat } from './assert.mjs';
const fixture=await stageChatFixture();
const browsers=[],pages=[];
try{
 for(let i=0;i<2;i++){const browser=await launchChromeBrowser({baseUrl:process.env.E2E_SERVER_BASE_URL??'http://localhost'});browsers.push(browser);pages.push(await browser.newPage());}
 const [a,b]=pages;
 await assertUi({page:a,local:false,actorId:fixture.ids[0],actorName:fixture.names[0],memberReady:async()=>{
  await openConversation(a,fixture.names[1]);
  await assertUi({page:b,local:false,actorId:fixture.ids[1],actorName:fixture.names[1],memberReady:async()=>{
   await openConversation(b,fixture.names[0]);
   console.log(JSON.stringify(await assertPairedChat(a,b)));
  }});
 }});
 console.log('PASS two-browser HTTP chat lifecycle');
}catch(error){console.error(error.stack);for(let i=0;i<pages.length;i++)console.error(JSON.stringify({actor:i,console:pages[i].consoleMessages.filter(message=>message.type==='error'),text:await pages[i].evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally{for(const page of pages)await page.close();for(const browser of browsers)await browser.close();const cleanup=await fixture.close();console.log('Fixture cleanup:',cleanup);assert.deepEqual(cleanup,{usersRemaining:0,chatsRemaining:0,messagesRemaining:0});}
