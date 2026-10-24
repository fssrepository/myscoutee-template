import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {enter} from './assert.mjs';
const result={point:18,credited:'Existing guide-position 6/6 tests: only-Close/no-guide and conditional repeated-menu owners; no rerun',samples:[]};
try{for(const [mode,baseUrl] of [['http','https://127.0.0.1'],['local','http://127.0.0.1:4200']]){
 const browser=await launchChromeBrowser({baseUrl});let page;
 try{page=await browser.newPage();await page.connection.send('Page.bringToFront');await enter(page);await page.setViewport({width:390,height:560});
 await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');await page.clickVisible('[data-guide-field="navigation-profile"]');await page.waitForSelector('app-profile-editor [data-control-id="name"] input');
 await page.clickVisible('app-profile-editor [data-guide-field="profile-photo"]');await page.waitForSelector('app-profile-editor [data-guide-field="image-preview"]');
 await page.clickVisible('app-explanation-launcher .explanation-guide-menu button');await page.waitForSelector('.explanation-guide-overlay__progress');
 const sample=await page.evaluate(mode=>{const card=document.querySelector('.explanation-guide-overlay__card'),target=document.querySelector('app-profile-editor [data-guide-field="image-preview"]');return {mode,title:card?.querySelector('h3')?.textContent,progress:card?.querySelector('.explanation-guide-overlay__progress')?.textContent,card:card?.getBoundingClientRect().toJSON(),target:target?{tag:target.tagName,rect:target.getBoundingClientRect().toJSON(),background:getComputedStyle(target).backgroundImage,text:target.innerText,focus:[...document.querySelectorAll('.explanation-guide-overlay__focus')].map(n=>n.getBoundingClientRect().toJSON())}:null,surface:card?document.querySelector('app-profile-editor')?.contains(target):false};},mode);
 result.samples.push(sample);assert.match(sample.title,/selected photo/i);assert.ok(sample.surface&&sample.target?.rect.width>0);assert.match(sample.target.background,/url\(/,'Retained photo preview uses its actual image');assert.ok(sample.target.focus.some(r=>Math.abs(r.x-sample.target.rect.x)<15&&Math.abs(r.y-sample.target.rect.y)<15),'Guide focus belongs to meaningful image preview');
 await page.clickVisible('.explanation-guide-overlay__close');
 }finally{if(page)await page.close();await browser.close();}
}result.status='PASS';}catch(error){result.status='FAIL';result.error=error.stack;process.exitCode=1;}
await writeFile('.e2e-artifacts/functional/template-regression/guide-image.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
