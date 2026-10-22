import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {queryE2eMongo} from '../../../mongo-fixture.mjs';
import {enter} from './assert.mjs';

const local=process.argv.includes('--local');
const id='qa-calendar-'+randomUUID();
const title='QA long calendar label — '+('A long description with several words and an unbrokenlongwordforoverflowcoverage ').repeat(6);
const directory='.e2e-artifacts/functional/template-regression';
await mkdir(directory,{recursive:true});
const result={item:7,mode:local?'local':'http',startedAt:new Date().toISOString(),samples:[]};
const query=expression=>queryE2eMongo({vars:{e2eDatabase:'demo_db'}},expression);
let staged=false,browser,page,localStaged=false;
try {
  if(!local){
    await query(`(()=>{const card=db.sampleCards.findOne({_id:'500000000000000000000001'});if(!card?.endDateIso)throw Error('Calendar seed baseline missing');db.sampleCards.insertOne({...card,_id:${JSON.stringify(id)},title:${JSON.stringify(title)}});return {staged:true};})()`);
    staged=true;
  }
  browser=await launchChromeBrowser({baseUrl:local?'http://127.0.0.1:4200':'https://127.0.0.1'});
  page=await browser.newPage();
  await page.connection.send('Page.bringToFront');
  await page.navigate('/home?page=showcase&view=week');
  await page.waitFor('existing demo session or entry',()=>page.evaluate(()=>!!document.querySelector('app-showcase,[data-guide-item="explore"]:not([disabled])')));
  if(!await page.evaluate(()=>!!document.querySelector('app-showcase')))await enter(page);
  if(local){
    await page.evaluate(async({id,title})=>{
      const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('myscoutee-template_demo_db');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      try {await new Promise((resolve,reject)=>{
        const tx=db.transaction('tables','readwrite'),store=tx.objectStore('tables'),r=store.get('sampleCards');
        r.onsuccess=()=>{const table=r.result,card=table?.byId['500000000000000000000001'];if(!card?.endDateIso){tx.abort();return;}store.put({byId:{...table.byId,[id]:{...card,id,title}},ids:[...table.ids,id]},'sampleCards');};
        tx.oncomplete=resolve;tx.onabort=()=>reject(Error('Local calendar baseline missing'));tx.onerror=()=>reject(tx.error);
      });} finally {db.close();}
    },{id,title});
    localStaged=true;
  }
  const widths=process.argv.includes('--mobile-week')?[390]:[1280,390];
  const views=process.argv.includes('--mobile-week')?['week']:['week','month'];
  for(const width of widths)for(const view of views){
    await page.setViewport({width,height:844});
    await page.navigate('/home?page=showcase&view='+view);
    await page.waitFor('long seeded calendar label',()=>page.evaluate(title=>[...document.querySelectorAll('app-showcase .smart-list__calendar-badge')].some(n=>n.title===title),title));
    await new Promise(resolve=>setTimeout(resolve,500));
    await page.evaluate(title=>[...document.querySelectorAll('app-showcase .smart-list__calendar-badge')].find(n=>n.title===title).scrollIntoView({block:'center',inline:'center',behavior:'instant'}),title);
    await page.waitFor('fixture visible in the selected calendar page',()=>page.evaluate(title=>{const r=[...document.querySelectorAll('app-showcase .smart-list__calendar-badge')].find(n=>n.title===title)?.getBoundingClientRect();return r&&r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight;},title));
    await page.connection.send('Page.bringToFront');
    await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    await new Promise(resolve=>setTimeout(resolve,1000));
    const sample=await page.evaluate(({title,width,view})=>{
      const badge=[...document.querySelectorAll('app-showcase .smart-list__calendar-badge')].find(n=>n.title===title),text=badge.querySelector('span'),style=getComputedStyle(text);
      const parent=badge.closest(view==='week'?'.smart-list__week-day-col':'.smart-list__month-week');
      const r=badge.getBoundingClientRect(),p=parent?.getBoundingClientRect();
      return {width,view,badge:r.toJSON(),parent:p?.toJSON(),text:{height:text.getBoundingClientRect().height,lineHeight:parseFloat(style.lineHeight),clamp:style.webkitLineClamp,overflow:style.overflow,ellipsis:style.textOverflow,scrollHeight:text.scrollHeight,clientHeight:text.clientHeight},pageOverflow:document.documentElement.scrollWidth>innerWidth};
    },{title,width,view});
    result.samples.push(sample);
    assert.equal(sample.pageOverflow,false,'Long label must not overflow the page');
    assert.ok(sample.parent,'Calendar containment owner exists');
    if(view==='month')assert.ok(sample.badge.width<=sample.parent.width/7+1,'Single-day badge stays inside one month cell');
    assert.ok(sample.badge.left>=sample.parent.left-1&&sample.badge.right<=sample.parent.right+1,'Badge stays inside its calendar owner');
    if(view==='week'){
      assert.equal(sample.text.clamp,'2');assert.equal(sample.text.overflow,'hidden');assert.equal(sample.text.ellipsis,'ellipsis');
      assert.ok(sample.text.height<=sample.text.lineHeight*2+1,'Week label is limited to two lines');
      assert.ok(sample.text.scrollHeight>sample.text.clientHeight,'Fixture exercises actual clipped text');
    }
    await writeFile(`${directory}/calendar-long-${result.mode}-${view}-${width}.png`,Buffer.from((await page.connection.send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false,fromSurface:false})).data,'base64'));
  }
  result.status='PASS';
}catch(error){result.status='FAIL';result.error=error.stack;process.exitCode=1;}
finally{
  if(localStaged&&page){
    result.localCleanup=await page.evaluate(async id=>{
      const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('myscoutee-template_demo_db');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
      try{return await new Promise((resolve,reject)=>{
        const tx=db.transaction('tables','readwrite'),store=tx.objectStore('tables'),r=store.get('sampleCards');
        r.onsuccess=()=>{const table=r.result;delete table.byId[id];table.ids=table.ids.filter(x=>x!==id);store.put(table,'sampleCards');};
        tx.oncomplete=()=>resolve({removed:id});tx.onerror=()=>reject(tx.error);
      });}finally{db.close();}
    },id);
    // Dispose the page before any further application writes can restore its in-memory fixture.
  }
  if(page)await page.close();if(browser)await browser.close();
  if(staged){result.cardCleanup=await query(`(()=>{db.sampleCards.deleteOne({_id:${JSON.stringify(id)}});return {remaining:db.sampleCards.countDocuments({_id:${JSON.stringify(id)}})};})()`);}
  result.finishedAt=new Date().toISOString();await writeFile(`${directory}/calendar-long-${result.mode}.json`,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
}
