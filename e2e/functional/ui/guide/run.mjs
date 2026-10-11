import { assertGuideTour } from './assert.mjs';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
const local=process.argv.includes('--local'),fixture=local?null:await stageChatFixture();
const browser=await launchChromeBrowser({baseUrl:local?(process.env.E2E_LOCAL_BASE_URL??'http://127.0.0.1:4200'):process.env.E2E_SERVER_BASE_URL??'http://localhost'}),page=await browser.newPage();

try{
 await page.navigate('/entry');await page.waitForText('v1.0.0');await assertGuideTour(page, ['Enter the application']);
 await assertUi({page,local,actorId:fixture?.ids[0],actorName:fixture?.names[0],memberReady:async()=>{
  await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');
  await assertGuideTour(page, ['Edit profile','Example cards','Chat']);
  await page.clickVisible('[data-guide-field="navigation-profile"]');await page.waitForSelector('app-profile-editor [data-control-id="name"] input');
  await assertGuideTour(page, ['Save profile','Profile picture','Display name']);
  await page.clickVisible('app-profile-editor .ui-popup__close');
  await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('.member-navigation [data-guide-item="chat"]');await page.waitForSelector('app-template-chat single-row article');
  await assertGuideTour(page, ['Conversations']);
  await page.clickVisible('app-template-chat single-row article');await page.waitForSelector('.chat-composer');
  await assertGuideTour(page, ['Back to conversations','Write a message']);
 }});
 console.log('PASS '+(local?'local':'HTTP')+' landing, navigation, profile, conversation list and message composer guides');
}catch(error){console.error(error.stack);console.error(await page.evaluate(()=>document.body.innerText.slice(0,2500)));process.exitCode=1;}
finally{await page.close();await browser.close();if(fixture)console.log('Owned fixture cleanup',await fixture.close());}
