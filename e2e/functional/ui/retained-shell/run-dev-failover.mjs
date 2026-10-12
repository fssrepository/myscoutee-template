import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { writeFile } from 'node:fs/promises';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';
const exec = promisify(execFile);
assert(process.env.E2E_MANAGED_BROWSER_MANIFEST, 'Use the visible versioned DEV browser checkpoint');
assert.equal(process.env.E2E_BACKEND_CONTAINER, 'myscoutee-template-dev-profilemanagement', 'This check stops only the selected DEV backend');
const container=process.env.E2E_BACKEND_CONTAINER;
const browser=await launchChromeBrowser({baseUrl:process.env.E2E_SERVER_BASE_URL??'https://localhost'});
const page=await browser.newPage();
const evidence={checkedAt:new Date().toISOString(),checks:[]};
let stopped=false;
async function openMenu(){await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');}
async function localList(item,ready,indicator){
 await openMenu();const start=Date.now();await page.clickVisible(`.user-menu-content [data-guide-item="${item}"]`);
 await page.waitForSelector(indicator);await page.waitForSelector(ready);
 const elapsedMs=Date.now()-start;assert(elapsedMs>=1300,'Native local route delay must remain visible');
 evidence.checks.push({surface:item,indicator:true,elapsedMs});
}
try {
 await page.setCacheDisabled(true);await page.connection.send('Network.setBypassServiceWorker',{bypass:true});
 await page.navigate('/home');await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
 await page.connection.send('Page.bringToFront');
 assert(await page.evaluate(()=>!!localStorage.getItem('myscoutee-template.http.session.v1')),'Start with an HTTP demo session');
 await exec('docker',['stop','--time','15',container]);stopped=true;
 await page.waitFor('automatic local demo failover',()=>page.evaluate(()=>!!localStorage.getItem('myscoutee-template.demo.failover.pair')),45000);
 await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
 const localStart=page.network.length;
 evidence.checks.push({automaticLocalFailover:true,url:await page.evaluate(()=>location.pathname)});
 await localList('showcase','app-showcase app-info-card','app-showcase .smart-list__header-progress-shell app-indicator');
 assert.equal(await page.evaluate(()=>document.querySelectorAll('app-showcase .smart-list__pagination-arrows').length),0);
 await page.clickVisible('app-popup .ui-popup__close');
 await localList('chat','app-template-chat single-row article','app-template-chat .smart-list__header-progress-shell app-indicator');
 const started=Date.now();await page.clickVisible('app-template-chat single-row article');
 await page.waitForSelector('.event-chat-popup-shell .smart-list__header-progress-shell app-indicator');
 await page.waitForSelector('.event-chat-popup-shell .chat-bubble');
 const chat=await page.evaluate(()=>({
  members:document.querySelectorAll('[data-guide-item="chat-header-members"] img').length,
  senderImages:document.querySelectorAll('.chat-sender-avatar img').length,
  callDisabled:document.querySelector('[data-guide-control="chat-call"]').disabled,
  rightAligned:document.querySelector('.ui-popup__toolbar-group--end [data-guide-control="chat-header-actions"]')!==null
 }));
 assert.equal(chat.members,2);assert.equal(chat.senderImages,2);assert.equal(chat.callDisabled,true);assert.equal(chat.rightAligned,true);
 evidence.checks.push({surface:'thread',elapsedMs:Date.now()-started,...chat});
 const text='Local parity verification '+Date.now();
 await page.fillVisible('.chat-compose-box textarea',text);await page.clickVisible('.chat-send-btn');await page.waitForText(text);
 await page.navigate('/home?page=chat');await page.waitForSelector('app-template-chat single-row article');await page.clickVisible('app-template-chat single-row article');await page.waitForText(text);
 evidence.checks.push({localMessageSavedAndReloaded:true});
 const unexpected=page.network.slice(localStart).filter(r=>new URL(r.url).pathname.startsWith('/api/')&&!new URL(r.url).pathname.startsWith('/api/runtime-status'));
 assert.deepEqual(unexpected,[],'Local interactions must not call Java');
 assert.deepEqual(page.pageErrors,[]);
 await writeFile('/tmp/msc122-local-thread.png',await page.screenshot());
 console.log(JSON.stringify(evidence,null,2));
} catch(error){
 console.error(error.stack);console.error('UI',await page.evaluate(()=>document.body.innerText.slice(-2000)).catch(()=>''));
 console.error('ERRORS',page.pageErrors);console.error('REQUESTS',page.network.filter(r=>r.status>=400).map(r=>({url:r.url,status:r.status})));
 process.exitCode=1;
} finally {
 if(stopped)await exec('docker',['start',container]);
 await writeFile('/tmp/msc122-dev-failover-evidence.json',JSON.stringify(evidence,null,2)+'\n');
 await page.close();await browser.close();
}
