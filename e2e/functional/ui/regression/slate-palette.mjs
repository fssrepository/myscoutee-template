import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '../lib/chrome-cdp.mjs';
const result={point:15,infoCardBadges:'UI N/A: only retained Showcase InfoCard binds no badges or menu options',samples:[]};
let hold,browser,page;
try{
 // Use the launcher's second, anonymous session; the first remains attached to its existing QA actor.
 hold=await launchChromeBrowser({baseUrl:'https://127.0.0.1'});
 for(const [mode,baseUrl] of [['http','https://127.0.0.1'],['local','http://127.0.0.1:4200']]){
 browser=await launchChromeBrowser({baseUrl});page=await browser.newPage();await page.connection.send('Page.bringToFront');await page.navigate('/entry');await page.waitForSelector('[data-guide-item="explore"]:not([disabled])');
 if(await page.isSelectorVisible('.explanation-guide-overlay__close'))await page.clickVisible('.explanation-guide-overlay__close');
 await page.clickVisible('[data-guide-item="explore"]:not([disabled])');await page.waitForSelector('[data-guide-item="entry-privacy-reject"]');
 for(const width of [1280,390]){await page.setViewport({width,height:844});const sample=await page.evaluate(({mode,width})=>{const n=document.querySelector('[data-guide-item="entry-privacy-reject"]'),s=getComputedStyle(n);return {mode,width,text:n.innerText,classes:n.className,color:s.color,border:s.borderColor,background:s.backgroundImage,rect:n.getBoundingClientRect().toJSON()};},{mode,width});assert.match(sample.classes,/slate/);assert.ok(sample.rect.width>0);result.samples.push(sample);}
 await page.close();page=null;await browser.close();browser=null;
 }
 result.status='PASS';
}catch(error){result.status='FAIL';result.error=error.stack;process.exitCode=1;}
finally{if(page)await page.close();if(browser)await browser.close();if(hold)await hold.close();}
await writeFile('.e2e-artifacts/functional/template-regression/slate-palette.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
