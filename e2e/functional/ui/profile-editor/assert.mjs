import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

// Uses the native media-upload-change-remove scenario's carousel and reload contract.
export async function openProfileEditor(page) {
 await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
 await page.clickVisible('[data-guide-field="navigation-profile"]');
 await page.waitForSelector('app-profile-editor [data-guide-item="profile-api-integration"]');
}
export async function openProfileIntegrations(page) {
 if (!await page.evaluate(()=>!!document.querySelector('app-profile-editor'))) await openProfileEditor(page);
 await page.clickVisible('app-profile-editor [data-guide-item="profile-api-integration"]');
 await page.waitForSelector('app-integration-settings-popup [data-guide-control="integration-generate"]');
}
export async function closeProfileEditor(page) {
 await page.clickVisible('app-profile-editor > app-popup .ui-popup__header .ui-popup__close');
 await page.waitFor('profile editor removed',()=>page.evaluate(()=>!document.querySelector('app-profile-editor')));
}
async function openImages(page) {
 await page.clickVisible('app-profile-editor [data-guide-field="profile-photo"]');
 await page.waitForSelector('app-profile-editor app-image-carousel');
 await page.waitFor('existing first image rendered',()=>page.evaluate(()=>{
  const slot=document.querySelector('app-profile-editor .image-carousel__slot');
  return !!slot && (!slot.classList.contains('is-filled') || slot.classList.contains('lazy-bg-loaded'));
 }));
}
async function slotUrl(page) {
 return page.evaluate(()=>getComputedStyle(document.querySelector('app-profile-editor .image-carousel__slot')).backgroundImage.match(/url\(["']?(.*?)["']?\)/)?.[1]??'');
}
async function saveAndReopen(page, name) {
 await page.clickVisible('app-profile-editor app-popup:has(app-image-carousel) [data-guide-control="close"]');
 await page.clickVisible('app-profile-editor [data-guide-item="profile-save"]');
 // The host contains a fixed popup and has no visible layout box. Wait for
 // actual teardown after the awaited save before navigating away.
 await page.waitFor('saved profile editor removed',()=>page.evaluate(()=>!document.querySelector('app-profile-editor')));
 await page.waitForText(name);
 await page.navigate('/home');await page.waitForText(name);await openProfileEditor(page);
 assert.equal(await page.evaluate(()=>document.querySelector('app-profile-editor [data-control-id="name"] input').value),name);
 await openImages(page);
}
async function upload(page,file,oldUrl='') {
 await page.setFileInputFiles('app-profile-editor input[type="file"]',fileURLToPath(new URL('./fixtures/'+file,import.meta.url)));
 await page.waitFor('image uploaded and preview ready',async()=>{
  const url=await slotUrl(page);
  return !!url && url!==oldUrl && !await page.isSelectorVisible('app-profile-editor .image-carousel__upload-ring');
 },45000);
 return slotUrl(page);
}
export async function assertProfileEditor({page,local,readHidden}) {
 const before=readHidden?await readHidden():null;
 await openProfileEditor(page);
 assert.equal(await page.evaluate(()=>document.querySelectorAll('app-profile-editor [data-control-id]').length),1,'Only the name is exposed as a profile field');
 const name='Template Profile '+Date.now();
 await page.fillVisible('app-profile-editor [data-control-id="name"] input',name);
 await openImages(page);
 const first=await upload(page,'avatar.png',await slotUrl(page));
 assert(local?first.startsWith('data:image/'):first.includes('/media/public?key='),'The selected adapter stores the image');
 await saveAndReopen(page,name);await page.waitFor('saved image is rendered',async()=>await slotUrl(page)===first,15000);
 if(process.env.E2E_SCREENSHOT_DIR){
  await mkdir(process.env.E2E_SCREENSHOT_DIR,{recursive:true});
  await writeFile(join(process.env.E2E_SCREENSHOT_DIR,'profile-desktop.png'),await page.screenshot());
  await page.setViewport({width:390,height:844});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile profile must not overflow');
  await writeFile(join(process.env.E2E_SCREENSHOT_DIR,'profile-mobile.png'),await page.screenshot());
  await page.setViewport({width:1440,height:950});
 }
 const second=await upload(page,'replacement.png',first);
 assert.notEqual(second,first);await saveAndReopen(page,name);
 await page.waitFor('replacement survives reload',async()=>await slotUrl(page)===second,15000);
 await page.clickVisible('app-profile-editor [data-guide-field="image-remove"]');
 await saveAndReopen(page,name);assert.equal(await slotUrl(page),'','Removed avatar stays removed');
 await page.clickVisible('app-profile-editor app-popup:has(app-image-carousel) [data-guide-control="close"]');
 await page.fillVisible('app-profile-editor [data-control-id="name"] input','Discarded edit');
 await page.clickVisible('app-profile-editor .ui-popup__close');
 await openProfileEditor(page);assert.equal(await page.evaluate(()=>document.querySelector('app-profile-editor [data-control-id="name"] input').value),name);
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No document horizontal overflow');
 await page.clickVisible('app-profile-editor .ui-popup__close');
 if(readHidden)assert.deepEqual(await readHidden(),before,'Hidden stored fields are preserved');
 if(local)assert(!page.network.some(request=>new URL(request.url).pathname.startsWith('/api/')),'Local profile does not call Java');
 else for(const path of ['/api/media/images','/api/auth/me/profile'])assert(page.network.some(request=>new URL(request.url).pathname===path&&request.status===200),'Actual backend boundary: '+path);
 return {nameSaved:true,imageUploaded:true,imageReplaced:true,imageRemoved:true,reloadPersisted:true,cancelPreserved:true,hiddenFieldsPreserved:!!readHidden};
}
