import languages from './languages.json';

/** Native language names stay recognizable even when the current UI is foreign. */
function nativeName(code: string, fallback: string): string {
  if (code === 'en-US') return 'English (US)';
  if (code === 'en-GB') return 'English (UK)';
  try {
    const displayCode = code === 'zh-CN' ? 'zh-Hans' : code === 'zh-TW' ? 'zh-Hant'
      : code.startsWith('pt-') ? code : code.split('-')[0];
    const name = new Intl.DisplayNames([code], {type:'language'}).of(displayCode) ?? fallback;
    return name.charAt(0).toLocaleUpperCase(code) + name.slice(1);
  } catch { return fallback; }
}

export const CONTENT_LANGUAGE_OPTIONS: Array<[string, string]> = [
  ...languages.map(({code, label}): [string, string] => [code, nativeName(code, label)]),
  ['en-GB', 'English (UK)']
];
