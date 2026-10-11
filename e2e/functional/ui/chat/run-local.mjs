import assert from 'node:assert/strict';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { openConversation } from './assert.mjs';
const browser=await launchChromeBrowser({baseUrl:process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'});
const page=await browser.newPage();
const text='Local saved message '+Date.now(), edited=text+' edited', reply='Local peer reply '+Date.now();
let messageId;
try{
 await assertUi({page,local:true,memberReady:async()=>{
  await openConversation(page,'Sam Taylor');await page.waitForText('Welcome to Templates. This is a saved conversation.');
  await page.fillVisible('.chat-composer textarea',text);await page.clickVisible('.chat-composer .send');await page.waitForText(text);
  messageId=await page.evaluate(text=>[...document.querySelectorAll('[data-chat-message-id]')].find(node=>node.innerText.includes(text))?.getAttribute('data-chat-message-id'),text);
  assert.ok(messageId);await page.clickVisible(`[data-chat-message-id="${messageId}"] button[aria-label="Edit"]`);
  await page.fillVisible('.chat-composer textarea',edited);await page.clickVisible('.chat-composer .send');await page.waitForText(edited);
  await page.navigate('/home?page=chat');await page.waitForText('Sam Taylor');await page.clickByText('single-row article','Sam Taylor');await page.waitForText(edited);
 }});
 await assertUi({page,local:true,consentExpected:false,actorId:'100000000000000000000002',actorName:'Sam Taylor',memberReady:async()=>{
  await openConversation(page,'Alex Morgan');await page.waitForText(edited);
  assert.equal(await page.isSelectorVisible(`[data-chat-message-id="${messageId}"] button[aria-label="Edit"]`),false);
  await page.fillVisible('.chat-composer textarea',reply);await page.clickVisible('.chat-composer .send');await page.waitForText(reply);
 }});
 await assertUi({page,local:true,consentExpected:false,memberReady:async()=>{
  await openConversation(page,'Sam Taylor');await page.waitForText(reply);await page.waitForText(edited);
  await page.waitForSelector(`[data-chat-message-id="${messageId}"] .read`);
  await page.clickVisible(`[data-chat-message-id="${messageId}"] button[aria-label="Delete"]`);await page.waitForSelector(`[data-chat-message-id="${messageId}"] .deleted`);
  await page.navigate('/home?page=chat');await page.waitForText('Sam Taylor');await page.clickByText('single-row article','Sam Taylor');await page.waitForSelector(`[data-chat-message-id="${messageId}"] .deleted`);
 }});
 console.log('PASS local chat send/edit/read/delete/reload and both participant histories; no backend requests');
}catch(error){console.error(error.stack);console.error(JSON.stringify({errors:page.consoleMessages.filter(message=>message.type==='error'),text:await page.evaluate(()=>document.body.innerText.slice(0,2500))}));process.exitCode=1;}
finally{await page.close();await browser.close();}
