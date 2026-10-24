import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {launchChromeBrowser} from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import {assertUi} from '../entry/assert.mjs';
assert(process.env.E2E_MANAGED_BROWSER_MANIFEST,'Use the versioned visible browser launcher');
const baseUrl='http://127.0.0.1:18888/myscoutee-template-frontend';
const browser=await launchChromeBrowser({baseUrl}),page=await browser.newPage();
const output='.e2e-artifacts/functional/pages-140';mkdirSync(output,{recursive:true});
try{
 await page.setViewport({width:393,height:852});await page.navigate('/entry');
 await page.waitForText('Templates');await page.waitForText('v1.4.0');
 await page.waitFor('logo loaded',()=>page.evaluate(()=>{const image=document.querySelector('.brand img');return !!image?.complete&&image.naturalWidth>0;}));
 const layout=await page.evaluate(()=>{
  const title=document.querySelector('.entry-hero-content h1').getBoundingClientRect();
  const explore=document.querySelector('[data-guide-item="explore"]').getBoundingClientRect();
  const brand=document.querySelector('.brand').getBoundingClientRect();
  return {titleBottom:title.bottom,exploreTop:explore.top,exploreBottom:explore.bottom,brandBottom:brand.bottom,width:innerWidth,scrollWidth:document.documentElement.scrollWidth,tabTitle:document.title};
 });
 assert(layout.exploreTop>=layout.titleBottom);assert(layout.exploreTop>layout.brandBottom);assert(layout.scrollWidth<=layout.width);
 assert.equal(layout.tabTitle,'MyScoutee');
 writeFileSync(output+'/landing-mobile.png',await page.screenshot());
 await page.setViewport({width:1440,height:950});
 await assertUi({page,local:true});
 const version=await (await fetch(baseUrl+'/app-version.json')).json();
 const production=JSON.parse(readFileSync('frontend/prod/app-version.json','utf8'));
 const pkg=JSON.parse(readFileSync('frontend/package.json','utf8'));
 assert.equal(version.version,pkg.version);assert.equal(production.version,pkg.version);
 assert.equal(JSON.parse(readFileSync('frontend/dev/app-version.json','utf8')).version,pkg.version);
 assert.equal(page.pageErrors.length,0);
 const evidence={task:'15.3',status:'DONE',date:'2026-10-12',scope:'Built Pages artifact served under its deployment prefix with the actual 404 redirect document. Publication is task 29.3.',pagesVersion:version,productionVersion:production,devVersion:pkg.version,mobileLayout:layout,checks:['Mobile Explore below title, loaded logo, no horizontal overflow','Operator-seeded browser title','Native entry consent/profile selection, local persistence and logout','Privacy/terms/home deep links through the Pages redirect','No unexpected failed assets, Java API calls or browser errors','DEV/production/Pages application version agreement']};
 writeFileSync('.e2e-artifacts/qualification/MSC122_TEMPLATE_PAGES_140_20261012.json',JSON.stringify(evidence,null,2)+'\n');
 console.log('DONE Pages 1.4.0 landing/assets/deep links and DEV/production/Pages version agreement.');
}finally{await page.close();await browser.close();}
