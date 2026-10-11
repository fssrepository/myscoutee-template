import assert from 'node:assert/strict';
export async function assertGuideTour(page, expected){
 await page.clickVisible('app-explanation-launcher .explanation-guide-menu button');
 await page.waitForSelector('.explanation-guide-overlay__progress');
 const found=[];
 for(let index=0;index<expected.length;index++){
  const state=await page.evaluate(()=>({title:document.querySelector('.explanation-guide-overlay__card h3')?.textContent?.trim(),progress:document.querySelector('.explanation-guide-overlay__progress')?.textContent?.trim()}));
  assert.equal(state.progress,`${index+1} / ${expected.length}`,'Only rendered controls belong in the native tour');found.push(state.title);
  await page.clickVisible('.explanation-guide-overlay__next [data-guide-item="next"]');
  if(index<expected.length-1)await page.waitFor('next guide step',()=>page.evaluate(expected=>document.querySelector('.explanation-guide-overlay__progress')?.textContent?.trim()===expected,`${index+2} / ${expected.length}`));
 }
 assert.deepEqual(found.sort(),[...expected].sort());
 await page.waitForSelectorHidden('.explanation-guide-overlay__card');
}
