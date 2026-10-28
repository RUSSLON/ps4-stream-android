const fs=require('fs'),vm=require('vm'),assert=require('assert');
const code=fs.readFileSync(__dirname+'/../app/src/main/assets/rezka_adapter.js','utf8');
function el(txt,attrs={}) {return {textContent:txt,alt:attrs.alt||'',getAttribute:k=>attrs[k]||'',querySelector:s=>attrs.nodes&&attrs.nodes[s]||null};}
const a=el('Демо', {href:'/films/drama/123-demo.html'}),poster=el('', {src:'https://img.example.org/poster.webp',alt:'Демо'}),title=el('Демо: фильм'),description=el('2026, Казахстан, драма');
const card=el('', {nodes:{'a[href]':a,'img':poster,'.b-content__inline_item-link a, .b-content__inline_item-link, .b-top__title, .title, .film-title':title,'.b-content__inline_item-link div, .b-content__inline_item-link + div, .b-content__inline_item-desc, .b-top__description, .description':description}});
const dom={title:'Пример',querySelectorAll:s=>s==='.b-content__inline_item, .b-content__list_item, .b-search__item'?[card]:[],querySelector:s=>null};
const ctx={window:{},document:dom,location:{hostname:'rezka-tv.org',href:'https://rezka-tv.org/',pathname:'/'},URL};
vm.runInNewContext(code,ctx);const result=ctx.window.PS4MobileAdapter.catalog();
assert.equal(result.items.length,1);assert.equal(result.featured.length,1);
assert.equal(result.items[0].title,'Демо: фильм');assert.equal(result.items[0].year,'2026');assert.equal(result.items[0].country,'Казахстан');assert.equal(result.items[0].poster,'https://img.example.org/poster.webp');
console.log('Native catalog adapter: 5 checks OK');
