/* Tests the actual asset against a minimal DOM, with mocked fetch. */
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const source = fs.readFileSync(__dirname+'/../app/src/main/assets/rezka_adapter.js','utf8');
const scriptNode={textContent:'initCDNSeriesEvents(51066,238)'};
const translator = {textContent:'Русская озвучка',classList:{contains:k=>k==='active'},getAttribute:k=>k==='data-translator_id'?'238':'',click:()=>{}};
const season = {textContent:'2 сезон',classList:{contains:k=>k==='active'},getAttribute:k=>k==='data-season_id'?'2':'',click:()=>{}};
const episode = {textContent:'6 серия',classList:{contains:k=>k==='active'},getAttribute:k=>k==='data-episode_id'?'6':'',click:()=>{}};
const header={textContent:'Киберпанк: Бегущие по краю'};
const mapping={
 'script':[scriptNode], 'h1':[header],
 '.b-translator__item, .b-translator__list .b-translator__item':[translator],
 '.b-simple_season__item, .b-simple_seasons__list .b-simple_season__item':[season],
 '.b-simple_episode__item':[episode]
};
let clicked=0;episode.click=()=>{clicked++};
const document={
 title:'Киберпанк',
 querySelectorAll:s=>mapping[s]||[],
 querySelector:s=>{
  if(s==='h1')return header;
  if(s==='script')return scriptNode;
  if(s.includes('active')&&s.includes('translator'))return translator;
  if(s.includes('active')&&s.includes('season'))return season;
  if(s.includes('active')&&s.includes('episode'))return episode;
  if(s==='.b-simple_episode__item')return episode;
  return null;
 }
};
let fetched;
const ctx={
 document,window:{},location:{href:'https://rezka-tv.org/animation/fiction/51066-sample.html#t:238-s:2-e:6',hash:'#t:238-s:2-e:6',hostname:'rezka-tv.org'},
 URL, URLSearchParams,atob:s=>Buffer.from(s,'base64').toString('binary'),
 fetch:async(path,options)=>{
  fetched={path,options};
  return {ok:true,json:async()=>({success:true,url:'[1080p]https://video.example.org/video1080.mp4,[720p]https://video.example.org/video720.mp4',subtitle:'[Русский]https://cdn.example.org/ru.vtt'})};
 }
};
const sandbox=vm.createContext(ctx);
vm.runInContext(source,sandbox);
const adapter=ctx.window.PS4MobileAdapter;
assert(adapter,'JS adapter was not installed');
const snap=adapter.snapshot();
assert.equal(snap.episodes.length,1);
assert.equal(snap.translations[0].label,'Русская озвучка');
assert.equal(adapter.choose('episodes',0),true);
assert.equal(clicked,1);
const parsed=adapter.debug.qualities('[480p]https://example.com/480.mp4,[720p]https://example.com/720.mp4');
assert.equal(parsed.length,2);
assert.equal(parsed[0].quality,'720p');
assert.equal(adapter.debug.subtitleEntries('[Русский]https://cdn.example.com/ru.vtt').length,1);
(async()=>{
 adapter.begin();
 for(let i=0;i<8 && adapter.peek().state==='loading';i++)await new Promise(r=>setImmediate(r));
 assert.equal(adapter.peek().state,'done',adapter.peek().message||'');
 assert.equal(adapter.peek().data.videos[0].quality,'1080p');
 assert.equal(adapter.peek().data.subtitles[0].lang,'Русский');
 assert.equal(fetched.options.credentials,'include');
 assert.equal(new URLSearchParams(fetched.options.body).get('episode'),'6');
 assert.equal(new URLSearchParams(fetched.options.body).get('action'),'get_stream');
 console.log('Android WebView adapter: 9 checks OK');
})().catch(e=>{console.error(e);process.exit(1)});
