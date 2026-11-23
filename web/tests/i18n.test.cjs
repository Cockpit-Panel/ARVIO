const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const {load} = require('./load.cjs');
const manifest = require('../lib/i18n/manifest.json');
const es = require('../public/i18n/es.json');
const phrases = require('../lib/i18n/phrases.json');
const languages = require('../lib/i18n/languages.json');
const {resolveLocale, translate, localeUrl} = load('lib/i18n/core.ts', {'./manifest.json':manifest});

test('profile language regions resolve to the same Android translation family', () => {
  for (const [input, expected] of [['es-ES','es'],['es-MX','es'],['es_AR','es'],['de-DE','de'],['pt-BR','pt-BR'],['zh-Hant','zh-TW'],['iw-IL','he'],['no-NO','nb'],['xx-ZZ','en']]) assert.equal(resolveLocale(input), expected);
  assert.match(localeUrl('es'), /^\/i18n\/es\.json\?v=[a-f0-9]{12}$/);
});
test('shared Android labels and web-only Spanish labels translate without changing identifiers', () => {
  assert.equal(translate(es, 'Home'), 'Inicio');
  assert.equal(translate(es, 'Search'), 'Buscar');
  assert.equal(translate(es, 'Settings'), 'Ajustes');
  assert.equal(translate(es, 'My lists'), 'Mis listas');
  assert.equal(translate(es, ' My watchlist '), ' Mi lista ');
  assert.equal(translate(es, 'WATCHLISTS'), 'Listas para ver');
  assert.equal(translate(es, 'Dune: Part Two'), 'Dune: Part Two');
  assert.equal(translate({}, 'Home'), 'Home');
  assert.equal(translate({}, 'constructor'), 'constructor');
  assert.equal(translate({}, '__proto__'), '__proto__');
});
test('interpolation preserves user content as text and handles assembled status messages', () => {
  assert.equal(translate(es, 'Play {value0}', {value0:'<img src=x onerror=alert(1)>'}), 'Reproducir <img src=x onerror=alert(1)>');
  assert.equal(translate(es, 'Trial: 3 days left'), 'Prueba: quedan 3 días');
  assert.equal(translate(es, 'Season 12'), 'Temporada 12');
  assert.equal(translate(es, 'No channels match {value0}.', {value0:'{value1}'}), 'Ningún canal coincide con {value1}.');
});
test('all Spanish web additions retain every interpolation slot', () => {
  for (const [key, value] of Object.entries(require('../lib/i18n/es.json'))) {
    const slots = text => [...text.matchAll(/\{\w+\}/g)].map(m=>m[0]).sort();
    assert.deepEqual(slots(value), slots(key), key);
  }
});
test('every Android language resolves to an available web dictionary', () => {
  for (const {code} of languages) {
    const locale = resolveLocale(code);
    if (code.startsWith('en')) assert.equal(locale, 'en');
    else { assert.notEqual(locale, 'en', code); assert.ok(manifest[locale], code); }
  }
  assert.equal(resolveLocale('fil-PH'), 'tl');
  assert.equal(resolveLocale('in-ID'), 'id');
});
test('every language covers all registered web phrases and preserves placeholders', () => {
  const normalize = text => text.trim().toLowerCase().replace(/…/g,'...');
  const slots = text => [...text.matchAll(/\{\w+\}/g)].map(match=>match[0]).sort();
  for (const locale of Object.keys(manifest)) {
    const dictionary = require(`../public/i18n/${locale}.json`);
    const indexed = new Map(Object.entries(dictionary).map(([key,value])=>[normalize(key),value]));
    for (const key of phrases) {
      const value=indexed.get(normalize(key));
      assert.equal(typeof value,'string',`${locale}: missing ${key}`);
      assert.ok(value.trim(),`${locale}: empty ${key}`);
      assert.deepEqual(slots(value),slots(key),`${locale}: placeholders in ${key}`);
      assert.doesNotMatch(value, /[⟦⟧⟪⟫]/, `${locale}: authoring token leaked into ${key}`);
    }
  }
});
test('the registered corpus includes all current display calls and web-only wording', async () => {
  const {requiredPhrases} = await import('../scripts/translation-sources.mjs');
  assert.deepEqual(phrases, requiredPhrases());
});
test('the interface uses the synced content language without remounting the application', () => {
  const store = fs.readFileSync(path.join(__dirname,'../lib/store.tsx'),'utf8');
  assert.match(store, /<LanguageProvider language=\{settings\.language\}>/);
  assert.doesNotMatch(store, /<LanguageProvider[^>]*key=/);
  const cloud = fs.readFileSync(path.join(__dirname,'../lib/cloud.ts'),'utf8');
  assert.match(cloud, /partial\.language = String\(state\.contentLanguage/);
});
test('Spanish covers every directly rendered interface phrase except language-neutral tokens', () => {
  const neutral = new Set(['ARVIO','ARVIO Web Premium','Android:','DASH','E','HLS','Infuse','MPEG-TS','No','S','Simkl','TV (IPTV)','Telegram','TheSportsDB','VLC','VS','Video','iOS / iPadOS:','s','via','vlc-setup.bat (Windows)','vlc-setup.sh (Linux)','{value0} x {value1}','{value0}m','••••••••','+1 650 555 1234','00:1A:79:00:00:00']);
  const root = path.join(__dirname,'../components');
  const missing = new Set();
  for (const file of fs.readdirSync(root,{recursive:true}).filter(file=>file.endsWith('.tsx'))) {
    const source = ts.createSourceFile(file, fs.readFileSync(path.join(root,file),'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
    function visit(node) {
      if (ts.isCallExpression(node) && node.expression.getText(source)==='translateUi' && ts.isStringLiteral(node.arguments[0])) {
        const text=node.arguments[0].text.trim();
        if (/[a-zA-Z]/.test(text) && !/^https?:\/\//.test(text) && !neutral.has(text) && !Object.keys(es).some(key=>key.toLowerCase()===text.toLowerCase())) missing.add(text);
      }
      ts.forEachChild(node, visit);
    }
    visit(source);
  }
  assert.deepEqual([...missing], []);
});
