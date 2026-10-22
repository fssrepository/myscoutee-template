import assert from 'node:assert/strict';

// Entry is a fixture prerequisite, not a replay of the qualified entry suite.
// Same selectors as entry/assert.mjs; no privacy/profile/public-document tests.
export async function enter(page, {id='100000000000000000000001', role='member', entryPath='/entry', targetPath=role==='member'?'/home':'/'+role}={}) {
  await page.navigate(entryPath);
  await page.waitFor('entry or retained session',()=>page.evaluate(()=>!!document.querySelector('[data-guide-item="explore"]:not([disabled]),.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])')));
  if(!await page.evaluate(()=>location.pathname==='/entry')) {
    await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
    await page.clickVisible('[data-guide-item="settings"]');
    await page.clickVisible('[data-guide-item="logout"]');
    await page.clickVisible('.dialog-panel [data-guide-item="confirm"]');
    await page.waitForUrlPath('/entry');
  }
  await page.waitForSelector('[data-guide-item="explore"]:not([disabled])');
  if (await page.isSelectorVisible('.explanation-guide-overlay__close')) {
    await page.clickVisible('.explanation-guide-overlay__close');
    await page.waitForSelectorHidden('.explanation-guide-overlay__card');
  }
  await page.clickVisible('[data-guide-item="explore"]:not([disabled])');
  await page.waitFor('consent or selector',()=>page.evaluate(()=>!!document.querySelector('app-document-viewer [role="dialog"],.demo-user-item')));
  if (await page.isSelectorVisible('app-document-viewer [role="dialog"]'))
    await page.clickVisible('app-document-viewer [data-guide-item="entry-privacy-accept"]');
  if (role!=='member') {
    await page.clickVisible('[data-guide-control="selector-role"] .app-menu__trigger');
    await page.clickVisible(`[data-guide-item="${role}"]`);
  }
  await page.clickVisible(`.demo-user-item[data-user-id="${id}"]`);
  await page.waitForUrlPath(targetPath,30000);
  if(targetPath!=='/mcp/authorize') await page.waitForSelector('.user-selector-menu');
}

export async function calendar(page) {
  const samples=[];
  for (const width of [1280,390]) {
    await page.setViewport({width,height:844});
    for (const view of ['week','month']) {
      await page.navigate('/home?page=showcase&view='+view);
      await page.waitForSelector('app-showcase .smart-list--'+view);
      await page.waitFor('sample badge',()=>page.evaluate(()=>document.querySelector('app-showcase')?.innerText.includes('Coastal light')));
      samples.push(await page.evaluate(({width,view})=>{
        const host=document.querySelector('app-showcase');
        const nodes=[...host.querySelectorAll('[class*="badge"]')].filter(n=>/Coastal light|Quiet peaks|Desert rhythm|Forest canopy/.test(n.textContent));
        return {width,view,badges:nodes.map(n=>({text:n.textContent.trim(),class:n.className,color:getComputedStyle(n).backgroundColor,background:getComputedStyle(n).backgroundImage,clamp:getComputedStyle(n.querySelector('span')??n).webkitLineClamp,rect:n.getBoundingClientRect().toJSON()})),overflow:document.documentElement.scrollWidth>innerWidth};
      },{width,view}));
      assert.equal(samples.at(-1).overflow,false,'Calendar must not cause horizontal page overflow');
      assert.ok(samples.at(-1).badges.length>=4,'Four retained palette samples must render');
    }
  }
  return samples;
}

export async function guide(page) {
  await page.navigate('/home');
  await page.setViewport({width:390,height:560});
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
  await page.clickVisible('app-explanation-launcher .explanation-guide-menu button');
  await page.waitForSelector('.explanation-guide-overlay__progress');
  const samples=[];
  for(let i=0;i<3;i++) {
    await page.waitFor('positioned guide',()=>page.evaluate(()=>{
      const n=document.querySelector('.explanation-guide-overlay__card');return n&&getComputedStyle(n).visibility==='visible';
    }));
    samples.push(await page.evaluate(()=>{
      const card=document.querySelector('.explanation-guide-overlay__card');
      return {title:card.querySelector('h3')?.textContent,progress:card.querySelector('.explanation-guide-overlay__progress')?.textContent.trim(),rect:card.getBoundingClientRect().toJSON(),viewport:{width:innerWidth,height:innerHeight},shade:document.querySelectorAll('.explanation-guide-overlay__shade').length,launcher:!!document.querySelector('app-explanation-launcher')};
    }));
    const s=samples.at(-1);assert.ok(s.shade&&s.launcher,'Guide shade and launcher remain');
    assert.ok(s.rect.x>=-1&&s.rect.right<=s.viewport.width+1&&s.rect.y>=-1&&s.rect.bottom<=s.viewport.height+1,'Guide fits short mobile');
    if(i<2){
      await page.clickVisible('.explanation-guide-overlay__next [data-guide-item="next"]');
      await page.waitFor('guide advance',()=>page.evaluate(old=>document.querySelector('.explanation-guide-overlay__progress')?.textContent.trim()!==old,s.progress));
    }
  }
  await page.clickVisible('.explanation-guide-overlay__close');
  return samples;
}

export async function introduction(page) {
  await page.navigate('/entry');
  await page.waitForSelector('.explanation-guide-overlay__progress');
  const samples=[];
  for(const [width,height] of [[1280,900],[390,844],[390,560]]){
    await page.setViewport({width,height});
    await page.waitFor('intro positioned',()=>page.evaluate(()=>{
      const n=document.querySelector('.explanation-guide-overlay__card'),r=n?.getBoundingClientRect();
      return n&&getComputedStyle(n).visibility==='visible'&&r.x>=0&&r.right<=innerWidth&&r.y>=0&&r.bottom<=innerHeight;
    }));
    samples.push(await page.evaluate(()=>({progress:document.querySelector('.explanation-guide-overlay__progress')?.textContent.trim(),title:document.querySelector('.explanation-guide-overlay__card h3')?.textContent,consent:localStorage.getItem('myscoutee-template.demo.entry.gdpr-consent.v1'),rect:document.querySelector('.explanation-guide-overlay__card').getBoundingClientRect().toJSON(),width:innerWidth,height:innerHeight})));
    assert.equal(samples.at(-1).progress,'1 / 1');assert.equal(samples.at(-1).consent,null);
  }
  await page.clickVisible('.explanation-guide-overlay__close');
  await page.navigate('/entry');
  await page.waitForSelector('[data-guide-item="explore"]');
  assert.equal(await page.isSelectorVisible('.explanation-guide-overlay__card'),false,'Dismissed intro stays dismissed');
  return samples;
}

export async function chatErrors(page, {local=false}={}) {
  await page.setViewport({width:390,height:844});
  await page.navigate('/home?page=chat');
  await page.waitForSelector('app-template-chat single-row article');
  const thread='.event-chat-popup-shell';
  if(!local){
    await page.clickVisible('app-template-chat single-row article');
    await page.waitForSelector(thread+' app-smart-list-empty-state');
    assert.equal(await page.evaluate(selector=>document.querySelector(selector).getAttribute('role'),thread+' app-smart-list-empty-state'),'status');
    await page.clickVisible(thread+' .ui-popup__close');
  }
  let paused=null,pausedAt=null;
  const stop=page.connection.on('Fetch.requestPaused',event=>{paused=event;pausedAt=Date.now();});
  try {
    if(!local)await page.connection.send('Fetch.enable',{patterns:[{urlPattern:'*/api/activities/chats/*/messages*',requestStage:'Response'}]});
    const openedAt=Date.now();
    await page.clickVisible('app-template-chat single-row article');
    await page.waitForSelector(thread+' .chat-compose-box');
    const openedInMs=Date.now()-openedAt;
    if(local){
      await page.waitForSelector(thread+' .smart-list__empty, '+thread+' .chat-message');
      return {openedInMs,empty:await page.evaluate(selector=>document.querySelector(selector).innerText,thread)};
    }
    await page.waitFor('actual message response held',()=>paused!==null);
    await page.waitForSelector(thread+' .smart-list__empty--error');
    const failedAt=Date.now();
    const timeoutInMs=failedAt-pausedAt;
    const sample=()=>page.evaluate(selector=>{
      const host=document.querySelector(selector),indicator=host.querySelector('.app-indicator'),empty=host.querySelector('app-smart-list-empty-state');
      return {online:navigator.onLine,error:empty?.innerText,indicator:indicator?.className,color:indicator?getComputedStyle(indicator.querySelector('.app-indicator__bar-loading')??indicator).backgroundImage:null,role:empty?.getAttribute('role'),emptyRect:empty?.getBoundingClientRect().toJSON(),listRect:empty?{top:empty.parentElement.getBoundingClientRect().top+parseFloat(getComputedStyle(empty.parentElement).paddingTop),bottom:empty.parentElement.getBoundingClientRect().bottom-parseFloat(getComputedStyle(empty.parentElement).paddingBottom)}:null,notices:[...document.querySelectorAll('app-dialog .dialog-panel')].filter(n=>n.getBoundingClientRect().width>0).length,listErrors:document.querySelector('app-template-chat .activities-popup .smart-list__empty--error')?.innerText};
    },thread);
    const failed=await sample();
    assert.equal(failed.role,'alert');
    assert.match(failed.error,/Conversation is unavailable|A beszélgetés nem érhető el/);
    assert.match(failed.color,/213, 54, 68|196, 29, 42/);
    assert.ok(Math.abs((failed.emptyRect.top+failed.emptyRect.bottom)-(failed.listRect.top+failed.listRect.bottom))<8,'Read error centering: '+JSON.stringify(failed));
    assert.match(failed.indicator,/is-state-error/);assert.equal(failed.notices,0);assert.equal(failed.listErrors,undefined);
    await page.emulateNetworkConditions({offline:true});
    await page.waitFor('native offline',()=>page.evaluate(()=>!navigator.onLine));
    await page.waitFor('grey offline indicator',()=>page.evaluate(selector=>!!document.querySelector(selector+' .is-state-inactive'),thread));
    const offline=await sample();assert.match(offline.color,/165, 173, 184|145, 154, 167/);
    await page.emulateNetworkConditions({offline:false});
    await page.waitFor('restored red failure',()=>page.evaluate(selector=>navigator.onLine&&!!document.querySelector(selector+' .is-state-error'),thread));
    const online=await sample();assert.match(online.color,/213, 54, 68|196, 29, 42/);
    await page.connection.send('Fetch.continueResponse',{requestId:paused.requestId});paused=null;
    await page.connection.send('Fetch.disable');
    await new Promise(resolve=>setTimeout(resolve,200));
    assert.equal((await sample()).error,failed.error,'A late response cannot replace the timeout');
    await page.clickVisible(thread+' .ui-popup__close');
    await page.clickVisible('app-template-chat single-row article');
    await page.waitForSelector(thread+' app-smart-list-empty-state');
    await page.waitFor('successful retry clears red',()=>page.evaluate(selector=>!document.querySelector(selector+' .is-state-error')&&!document.querySelector(selector+' .smart-list__empty--error'),thread));
    const retry=await sample();
    await page.clickVisible(thread+' .ui-popup__close');
    await page.connection.send('Fetch.enable',{patterns:[{urlPattern:'*/api/activities/chats/*/messages*',requestStage:'Response'}]});
    paused=null;
    await page.clickVisible('app-template-chat single-row article');
    await page.waitFor('actual read held before close',()=>paused!==null);
    await page.clickVisible(thread+' .ui-popup__close');
    await new Promise(resolve=>setTimeout(resolve,3200));
    await page.connection.send('Fetch.continueResponse',{requestId:paused.requestId});paused=null;
    await page.connection.send('Fetch.disable');
    await new Promise(resolve=>setTimeout(resolve,200));
    assert.equal(await page.isSelectorVisible(thread+' .chat-compose-box'),false,'A late read cannot reopen a closed popup');
    assert.equal(await page.isSelectorVisible('app-template-chat .activities-popup .is-state-error'),false,'Closing alone is not a source-list failure');
    return {openedInMs,timeoutInMs,failed,offline,online,retry,closedLateResponse:'PASS'};
  } finally {
    stop();
    if(paused)await page.connection.send('Fetch.continueResponse',{requestId:paused.requestId}).catch(()=>{});
    await page.connection.send('Fetch.disable').catch(()=>{});
    await page.emulateNetworkConditions({offline:false});
  }
}

export async function notificationGroupResize(page,{scrollOnly=false}={}) {
  const host='app-notification-center-popup';
  await page.setViewport({width:1280,height:1000});
  await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('.navigator-header-actions [data-guide-item="notifications"]');
  await page.waitForSelector(host+' single-row article');
  const samples=[];
  const sample=()=>page.evaluate(host=>{
    const p=document.querySelector(host),s=p.querySelector('.smart-list'),h=p.querySelector('.smart-list__sticky');
    const label=h?.querySelector('.smart-list__sticky-label')?.textContent.trim();
    return {width:innerWidth,height:innerHeight,scrollable:s.scrollHeight>s.clientHeight+1,top:s.scrollTop,scrollHeight:s.scrollHeight,clientHeight:s.clientHeight,
      header:h?.getBoundingClientRect().toJSON(),label,
      firstMarker:[...p.querySelectorAll('.smart-list__group-marker')].some(n=>n.dataset.groupLabel===label),
      targets:[...s.querySelectorAll('.smart-list__item-shell')].map(n=>getComputedStyle(n).scrollSnapAlign)};
  },host);
  for(const [width,height,scrollable] of (scrollOnly?[[390,560,true]]:[[1280,1000,false],[390,560,true],[1280,1000,false]])){
    await page.setViewport({width,height});
    try { await page.waitFor('group marker follows resized scrollability',async()=>{
      const value=await sample();return value.scrollable===scrollable&&value.firstMarker===scrollable;
    }); } catch(error) { throw new Error(error.message+' '+JSON.stringify({expectedScrollable:scrollable,actual:await sample(),accepted:samples})); }
    await new Promise(resolve=>setTimeout(resolve,500));
    const value=await sample();samples.push(value);
    assert(value.header.height>0&&value.targets.every(target=>target!=='none'));
    if(scrollable){
      const before=value.header.top;
      await page.connection.send('Input.dispatchMouseEvent',{type:'mouseWheel',x:width/2,y:height/2,deltaX:0,deltaY:240});
      await page.waitFor('native notification scroll',async()=>(await sample()).top>value.top+10);
      const scrolled=await sample();assert(Math.abs(scrolled.header.top-before)<2,'Sticky header stays in place');samples.push(scrolled);
    }
  }
  await page.clickVisible(host+' .ui-popup__close');
  return samples;
}

export async function notificationLayout(page) {
  await page.navigate('/home');
  await page.waitForSelector('.user-selector-menu [data-guide-item="navigator-avatar"]:not([disabled])');
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('.navigator-header-actions [data-guide-item="notifications"]');
  const host='app-notification-center-popup';
  await page.waitForSelector(host+' single-row article');
  const samples=[];
  for(const [width,height] of [[1280,900],[390,560],[1280,650]]) {
    await page.setViewport({width,height});
    await new Promise(resolve=>setTimeout(resolve,250));
    const sample=await page.evaluate(selector=>{
      const popup=document.querySelector(selector),list=popup.querySelector('.smart-list'),scroll=popup.querySelector('.smart-list'),header=popup.querySelector('.smart-list__sticky');
      return {viewport:{width:innerWidth,height:innerHeight},overflow:document.documentElement.scrollWidth>innerWidth,
        layout:list?.className,snap:scroll?getComputedStyle(scroll).scrollSnapType:null,
        header:header?{rect:header.getBoundingClientRect().toJSON(),position:getComputedStyle(header).position}:null,
        markers:[...popup.querySelectorAll('.smart-list__group-marker')].map(n=>({label:n.textContent.trim(),rect:n.getBoundingClientRect().toJSON()})),
        rows:[...popup.querySelectorAll('.smart-list__item-shell')].map(n=>({snap:getComputedStyle(n).scrollSnapAlign,rect:n.getBoundingClientRect().toJSON(),icon:n.querySelector('mat-icon')?.textContent,unread:!!n.querySelector('.is-unread')}))};
    },host);
    assert.equal(sample.overflow,false);assert.ok(sample.rows.length>0);
    assert.ok(sample.header?.rect.height>0,'The sticky notification header stays visible');
    assert.ok(sample.rows.every(row=>row.snap!=='none'),'Existing row shells own snap targets');
    assert.equal(new Set(sample.rows.map(row=>Math.round(row.rect.x))).size,1,'Notifications remain one column');
    samples.push(sample);
  }
  await page.clickVisible(host+' .ui-popup__close');
  return samples;
}
