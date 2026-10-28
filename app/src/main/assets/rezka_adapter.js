/* PS4 Stream Android: same-origin source adapter. No privileged Android JS interface. */
(function () {
  'use strict';
  var SALTS = ['IyMjI14hISMjIUBA', 'QEBAQEAhIyMhXl5e', 'JCQhIUAkJEBeIUAjJCRA', 'JCQjISFAIyFAIyM=', 'Xl5eIUAjIyEhIyM='];
  function text(el) {return (el && (el.textContent || el.innerText) || '').trim().replace(/\s+/g, ' ').slice(0, 140);}
  function one(s) {return document.querySelector(s);}
  function many(s) {return Array.prototype.slice.call(document.querySelectorAll(s));}
  function title() {return text(one('h1')) || document.title || 'Видео';}
  function selection(selector) {
    return many(selector).filter(function(el){return text(el).length > 0;}).map(function(el, i){return {
      label: text(el), index:i, selected: el.classList.contains('active') || el.classList.contains('current') || el.classList.contains('selected')
    };}).slice(0, 120);
  }
  var SELECTORS = {
    translations: '.b-translator__item, .b-translator__list .b-translator__item',
    seasons: '.b-simple_season__item, .b-simple_seasons__list .b-simple_season__item',
    episodes: '.b-simple_episode__item'
  };
  function uniqueNodes(selector) {
    var seen=[];
    many(selector).forEach(function(el){if(seen.indexOf(el)<0)seen.push(el);});
    return seen.filter(function(el){return text(el).length>0;});
  }
  function snapshot() {
    return {
      title:title(), location:location.href,
      translations:selection(SELECTORS.translations), seasons:selection(SELECTORS.seasons), episodes:selection(SELECTORS.episodes),
      hasHelperMenu:!!one('#downloadMenu .download-menu-body'),
      ready:/\.html(?:$|[?#])/.test(location.href),
      sourceHost:location.hostname
    };
  }
  function choose(kind, index) {
    if(!SELECTORS[kind]) throw Error('Неизвестный тип выбора');
    var elements=uniqueNodes(SELECTORS[kind]);
    if(index<0||index>=elements.length) throw Error('Элемент не найден');
    elements[index].click();
    return true;
  }
  function paramFromHash(key) {var m=new RegExp('(?:^|[\\-#])'+key+':(\\d+)').exec(location.hash);return m?m[1]:'';}
  function activeId(selector, attribute) {var el=one(selector);return el && (el.getAttribute(attribute)||el.getAttribute('data-id')) || '';}
  function metadata() {
    var scripts=many('script').map(function(el){return el.textContent||'';}).join('\n');
    var init=/initCDN(Series|Movies)Events\(\s*(\d+)\s*,\s*(\d+)/.exec(scripts);
    var episode=one('.b-simple_episode__item.active,.b-simple_episode__item.current,.b-simple_episode__item.selected');
    var season=one('.b-simple_season__item.active,.b-simple_season__item.current,.b-simple_season__item.selected');
    var activeTranslator=one('.b-translator__item.active,.b-translator__item.current,.b-translator__item.selected');
    var isSeries=!!(paramFromHash('s') || one('.b-simple_episode__item') || (init && init[1]==='Series'));
    var id=init&&init[2] || activeId('[data-post_id]','data-post_id') || activeId('[data-post-id]','data-post-id');
    var translator=paramFromHash('t') || activeTranslator&&(activeTranslator.getAttribute('data-translator_id')||activeTranslator.getAttribute('data-id')) || init&&init[3] || '';
    var seasonId=paramFromHash('s') || season&&(season.getAttribute('data-season_id')||season.getAttribute('data-id')) || episode&&episode.getAttribute('data-season_id') || '';
    var episodeId=paramFromHash('e') || episode&&(episode.getAttribute('data-episode_id')||episode.getAttribute('data-id')) || '';
    return {title:title()+(isSeries&&seasonId&&episodeId?' · S'+seasonId+'E'+episodeId:''), id:id,
      translatorId:translator, seasonId:seasonId, episodeId:episodeId, isSeries:isSeries,
      is_camrip: activeTranslator&&activeTranslator.getAttribute('data-camrip') || '0',
      is_ads: activeTranslator&&activeTranslator.getAttribute('data-ads') || '0',
      is_director: activeTranslator&&activeTranslator.getAttribute('data-director') || '0'};
  }
  function cleanUrl(s) {
    s=String(s||'').replace(/\\\//g,'/').split(/:hls|:dash|:m3u8/i)[0].trim();
    var m=/https?:\/\/[^\s,|;<>"']+/i.exec(s);
    if(!m)return '';
    try {var u=new URL(m[0]);return ['https:','http:'].indexOf(u.protocol)>=0?u.href:'';} catch(_){return '';}
  }
  function decodeUrl(encoded) {
    if(typeof encoded!=='string')return '';
    if(encoded.charAt(0)==='[' || /^https?:/.test(encoded))return encoded;
    var s=encoded.replace(/^#h/,'');
    for(var tries=0;tries<60&&s.indexOf('//_//')>=0;tries++){
      var p=s.indexOf('//_//'),rest=s.slice(p+5),salt='';
      for(var j=0;j<SALTS.length;j++)if(rest.indexOf(SALTS[j])===0){salt=SALTS[j];break;}
      s=s.slice(0,p)+rest.slice(salt?salt.length:16);
    }
    try{return atob(s);}catch(_){return encoded;}
  }
  function qualities(encoded) {
    var decoded=decodeUrl(encoded).replace(/<[^>]*>/g,'');
    var regex=/\[([^\]]+)\]([\s\S]*?)(?=\[[^\]]+\]|$)/g, videos=[],m;
    while((m=regex.exec(decoded))){
      var candidates=m[2].split(/\s+or\s+|\s*\|\s*|,/i).map(cleanUrl).filter(Boolean);
      var mp4=candidates.find(function(u){return /\.mp4(?:[?#]|$)/i.test(u);});
      if(mp4)videos.push({quality:m[1].trim(),url:mp4});
    }
    if(!videos.length){var alone=cleanUrl(decoded);if(/\.mp4(?:[?#]|$)/i.test(alone))videos.push({quality:'Авто',url:alone});}
    return videos.filter(function(v,i,a){return a.findIndex(function(x){return x.quality===v.quality&&x.url===v.url;})===i;})
      .sort(function(a,b){return (parseInt(b.quality,10)||0)-(parseInt(a.quality,10)||0);});
  }
  function subtitleEntries(value){
    if(typeof value!=='string')return [];
    return value.split(/,(?=\[)/).map(function(part){
      var m=/^\[([^\]]+)\](.+)$/.exec(part);if(!m)return null;
      var url=cleanUrl(m[2]);return url?{lang:m[1].trim(),url:url}:null;
    }).filter(Boolean).slice(0,12);
  }
  function helperSources() {
    var body=one('#downloadMenu .download-menu-body');if(!body)return null;
    var videos=[],subtitles=[];
    Array.prototype.slice.call(body.querySelectorAll('a[href]')).forEach(function(a){
      var link=cleanUrl(a.href), label=text(a.querySelector('span'))||text(a);
      if(!link)return;
      if(a.closest('details') && /\.(srt|vtt)(?:[?#]|$)/i.test(link))subtitles.push({lang:label,url:link});
      else if(/\.mp4(?:[?#]|$)/i.test(link)&&/\d{3,4}p|4k/i.test(label))videos.push({quality:label,url:link});
    });
    return videos.length?{videos:videos,subtitles:subtitles}:null;
  }
  async function sources() {
    var meta=metadata(), helper=helperSources();
    if(helper)return Object.assign({title:meta.title},helper);
    if(!meta.id||!meta.translatorId)throw Error('Не найден фильм или озвучка. Выбери перевод на странице.');
    if(meta.isSeries&&(!meta.seasonId||!meta.episodeId))throw Error('Выбери сезон и серию на странице.');
    var fields=new URLSearchParams({id:meta.id, translator_id:meta.translatorId,favs:'',
      action:meta.isSeries?'get_stream':'get_movie'});
    if(meta.isSeries){fields.set('season',meta.seasonId);fields.set('episode',meta.episodeId);}
    else {fields.set('is_camrip',meta.is_camrip);fields.set('is_ads',meta.is_ads);fields.set('is_director',meta.is_director);}
    var response=await fetch('/ajax/get_cdn_series/?t='+Date.now(),{
      method:'POST',body:fields.toString(),credentials:'include',headers:{'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'}
    });
    if(!response.ok)throw Error('Источник ответил HTTP '+response.status);
    var json=await response.json();if(!json.success)throw Error(json.message||'Сайт не вернул поток');
    var videos=qualities(json.url);
    if(!videos.length)throw Error('Прямых MP4-ссылок нет. HLS/DASH/DRM в этой версии не поддерживаются.');
    return {title:meta.title,videos:videos,subtitles:subtitleEntries(json.subtitle)};
  }
  function catalog() {
    var host=location.hostname, seen={}, candidates=[];
    function pick(el) {if(!el)return '';return (el.getAttribute('data-src')||el.getAttribute('data-original')||el.getAttribute('data-lazy-src')||el.getAttribute('src')||'').trim();}
    function add(container,featured) {
      if(!container || candidates.length>180)return;
      var a=container.querySelector('a[href]');if(!a)return;
      var href='';try{href=new URL(a.getAttribute('href'),location.href).href;}catch(_){return;}
      if(!/\.html(?:[?#]|$)/.test(href) || !href.startsWith('https://'+host+'/')||seen[href])return;
      var img=container.querySelector('img');
      var name=container.querySelector('.b-content__inline_item-link a, .b-content__inline_item-link, .b-top__title, .title, .film-title');
      var heading=text(name)||text(a.getAttribute('title')?{textContent:a.getAttribute('title')}:null)||text(img&&img.alt?{textContent:img.alt}:null);
      if(!heading || heading.length<2)return;
      var details=text(container.querySelector('.b-content__inline_item-link div, .b-content__inline_item-link + div, .b-content__inline_item-desc, .b-top__description, .description'));
      var year=(details.match(/(?:19|20)\d{2}/)||[])[0]||'';
      var country=details.replace(/(?:19|20)\d{2}\s*[·,]?/g,'').split(/[,·]/)[0].trim().slice(0,60);
      var image='';try{image=new URL(pick(img),location.href).href;}catch(_){}
      if(image && !image.startsWith('https://'))image='';
      seen[href]=true;
      candidates.push({url:href,title:heading,year:year,country:country,poster:image,featured:featured});
    }
    // First-screen carousel containers (may vary across mirror domains).
    many('.b-top__item, .b-top-slider__item, .b-slider__item, .owl-item .b-content__inline_item, .b-content__top .b-content__inline_item').forEach(function(el){add(el,true);});
    // Common HDrezka search and catalog cards.
    many('.b-content__inline_item, .b-content__list_item, .b-search__item').forEach(function(el){add(el,false);});
    var featured=candidates.filter(function(x){return x.featured;}).slice(0,16);
    var items=candidates.filter(function(x){return !x.featured;}).slice(0,80);
    // Some mirrors present the top items in the same main grid; use the first row.
    if(!featured.length && items.length>0 && !/\/search\//.test(location.pathname))featured=items.slice(0,10);
    return {featured:featured,items:items};
  }
  var result={state:'idle'};
  function begin(){
    result={state:'loading'};
    sources().then(function(data){result={state:'done',data:data};}).catch(function(e){result={state:'error',message:String(e&&e.message||e)};});
    return true;
  }
  window.PS4MobileAdapter={snapshot:snapshot,catalog:catalog,choose:choose,begin:begin,peek:function(){return result;},
    debug:{qualities:qualities,subtitleEntries:subtitleEntries,decodeUrl:decodeUrl}};
})();
