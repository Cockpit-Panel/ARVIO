// Actual premium shell/gate/preview, isolated app screens, identity and HTTP.
// Owns an ephemeral headless profile. Both routing and a deny proxy block all
// external traffic; this cannot send mail, start a real trial or make a charge.
const { chromium } = require('@playwright/test');
const esbuild = require('esbuild');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const assert = require('node:assert/strict');

(async () => {
  const root = path.resolve(__dirname, '..');
  const fixture = name => path.join(root, 'tests/fixtures', name);
  const stubNames = new Set(['AddonsScreen', 'DetailsDrawer', 'HomeScreen', 'LiveTvScreen', 'LoginScreen', 'PlayerOverlay',
    'ProfileSelectionScreen', 'SearchScreen', 'SettingsScreen', 'WatchlistScreen', 'BackHandler', 'ExternalPlaybackPrompt',
    'MediaContextMenu', 'NoAddonsPrompt', 'Toast', 'TopNav', 'PremiumUsage', 'PartnerLinkHandler', 'tvNav', 'gamepadNav']);
  const result = await esbuild.build({ entryPoints: [fixture('paid-preview-ui.tsx')], bundle: true, write: false,
    outdir: 'paid-preview-fixture', jsx: 'automatic',
    define: { 'process.env': '{}', 'process.env.NODE_ENV': '"development"',
      'process.env.NEXT_PUBLIC_PAYWALL_ENABLED': '"true"', 'process.env.NEXT_PUBLIC_KOFI_URL': '"https://ko-fi.com/arvio/tiers"' },
    plugins: [{ name: 'isolated-paid-preview', setup(build) {
      build.onResolve({ filter: /./ }, args => {
        if (stubNames.has(path.basename(args.path).replace(/\.tsx?$/, ''))) return { path: fixture('premium-shell-components.tsx') };
        if (args.path === '@/lib/store') return { path: fixture('premium-store.ts') };
        if (/(^|\/)http$/.test(args.path)) return { path: fixture('premium-http.ts') };
        if (args.path.startsWith('@/')) return { path: ['.tsx', '.ts', '/index.tsx', '/index.ts', ''].map(ext => path.join(root, args.path.slice(2) + ext)).find(file => fs.existsSync(file)) };
      });
    } }]
  });
  const js = result.outputFiles.find(file => file.path.endsWith('.js')).contents;
  const css = result.outputFiles.find(file => file.path.endsWith('.css')).contents;
  const publicRoot = path.join(root, 'public');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'");
    if (url.pathname === '/bundle.js' || url.pathname === '/bundle.css') {
      res.setHeader('content-type', url.pathname.endsWith('js') ? 'text/javascript' : 'text/css');
      return res.end(url.pathname.endsWith('js') ? js : css);
    }
    const asset = path.resolve(publicRoot, '.' + decodeURIComponent(url.pathname));
    if (asset.startsWith(publicRoot + path.sep) && /\.(svg|webp|png|jpe?g|json)$/.test(asset) && fs.existsSync(asset)) {
      const ext = path.extname(asset);
      res.setHeader('content-type', ({ '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.json': 'application/json' })[ext]);
      return res.end(fs.readFileSync(asset));
    }
    res.setHeader('content-type', 'text/html');
    res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local paid-only preview test</title><link rel="stylesheet" href="/bundle.css"></head><body><div id="root"></div><script src="/bundle.js"></script></body></html>');
  });
  const denyProxy = http.createServer((_req, res) => { res.writeHead(502); res.end('No external network in preview QA'); });
  denyProxy.on('connect', (_req, socket) => socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n'));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  await new Promise(resolve => denyProxy.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const evidence = path.resolve(root, '../artifacts/paid-only-web-20261023');
  fs.mkdirSync(evidence, { recursive: true });
  let browser, passed = 0;
  try {
    browser = await chromium.launch({ ...(process.platform === 'win32' ? { channel: 'msedge' } : {}), headless: true,
      proxy: { server: `http://127.0.0.1:${denyProxy.address().port}`, bypass: '127.0.0.1' } });
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'en-US', serviceWorkers: 'block' });
    const errors = [], externalAttempts = [];
    context.setDefaultTimeout(15000);
    context.on('page', page => page.on('pageerror', error => errors.push(error.message)));
    await context.route('**/*', route => {
      if (new URL(route.request().url()).origin === base) return route.continue();
      externalAttempts.push(route.request().url()); return route.abort();
    });
    const page = await context.newPage();
    const preview = () => page.getByTestId('web-feature-preview');
    const ticks = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const noChildren = async label => assert.deepEqual(await page.evaluate(() => window.__shellMounts), [], `${label}: app/login/profile screens remain unmounted`);
    const noTrials = async label => {
      assert.equal(await page.getByRole('button', { name: /(?:start|connect|try).*(?:trial)|free trial/i }).count(), 0, `${label}: no trial button`);
      assert.equal(await page.getByRole('link', { name: /(?:start|connect|try).*(?:trial)|free trial/i }).count(), 0, `${label}: no trial link`);
      assert.equal(await page.evaluate(() => window.__premiumFixture.trialRequests), 0, `${label}: never POST start-trial`);
      assert.equal(await page.locator('video, audio').count(), 0, `${label}: no live playback surface`);
    };
    const noOverflow = async label => {
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${label}: no page overflow`);
      assert.equal(await page.getByTestId('feature-screenshot').locator('img').evaluate(image => image.getBoundingClientRect().width > innerWidth), false, `${label}: preview image fits viewport`);
      for (const dialog of await page.getByRole('dialog').all()) assert.equal(await dialog.evaluate(element => element.scrollWidth > element.clientWidth), false, `${label}: no dialog overflow`);
    };
    for (const [label, width, height] of [['small-phone', 320, 720], ['mobile', 390, 844], ['desktop', 1440, 900]]) {
      await page.setViewportSize({ width, height });
      await page.goto(`${base}/?case=preview-signedout`); await preview().waitFor(); await ticks();
      const content = await preview().innerText();
      for (const feature of ['Live TV', 'Jellyfin', 'Plex', 'Emby', 'Trakt', 'Simkl', 'Cloud']) assert.match(content, new RegExp(feature, 'i'), `${label}: ${feature} is explained before payment`);
      assert.match(content, /\$2\.99/);
      await noTrials(label); await noChildren(label); await noOverflow(label);
      assert.ok(['grid', 'flex'].includes(await page.locator('.web-preview-layout').evaluate(element => getComputedStyle(element).display)), `${label}: production preview layout styles are loaded`);
      assert.equal(await page.evaluate(() => window.__premiumFixture.statusRequests), 0, `${label}: signed-out preview requires no entitlement lookup`);
      assert.equal(await page.evaluate(() => window.__authNavigateCount), 0, `${label}: auth navigation is not automatic`);
      await page.waitForFunction(() => [...document.images].every(image => image.complete && image.naturalWidth > 0));
      const shot = await page.screenshot({ path: path.join(evidence, `feature-preview-${label}.png`), fullPage: true });
      assert.equal(shot.readUInt32BE(16), width, `${label}: full-page evidence stays within requested viewport`);
      assert.equal(page.url(), `${base}/?case=preview-signedout`, 'preview does not auto-redirect to sign-in');
      passed++;
    }
    // The actual store navigates straight to the auth portal. Its fixture spy
    // records the deliberate action without contacting a real account service.
    await page.getByRole('button', { name: /premium|ko-fi|sign in|connect/i }).first().click();
    assert.equal(await page.evaluate(() => window.__authNavigateCount), 1);
    await noChildren('explicit sign-in'); passed++;
    await page.getByRole('button', { name: 'Connect & get Premium', exact: true }).click();
    assert.equal(await page.evaluate(() => window.__authNavigateCount), 2, 'primary signed-out purchase CTA also starts explicit auth'); passed++;
    for (const [label, asset] of [['Live TV', 'tv'], ['Library', 'library'], ['Home Server', 'homeserver'], ['Sports', 'sports'], ['Home', 'home']]) {
      const tab = page.getByRole('button', { name: label, exact: true }); await tab.click();
      assert.equal(await tab.getAttribute('aria-pressed'), 'true');
      const image = page.getByTestId('feature-screenshot').locator('img');
      assert.equal(await image.getAttribute('src'), `/premium/${asset}.png`);
      await image.evaluate(image => image.decode()); await noChildren(`preview tab ${label}`);
    }
    passed++;

    for (const scenario of ['preview-unpaid', 'preview-legacy', 'preview-stale']) {
      await page.goto(`${base}/?case=${scenario}${scenario === 'preview-legacy' ? '&intent=trial' : ''}`);
      await preview().waitFor(); await ticks(); await noTrials(scenario); await noChildren(scenario);
      const link = page.getByRole('link', { name: /premium|ko-fi|subscribe/i }).first();
      assert.equal(await link.getAttribute('href'), 'https://ko-fi.com/arvio/tiers');
      assert.match(await link.getAttribute('rel'), /noopener/);
      passed++;
    }
    // New paid access from the existing recovery path unlocks profile selection.
    await page.goto(`${base}/?case=preview-unpaid`); await preview().waitFor();
    await page.screenshot({ path: path.join(evidence, 'signed-in-feature-preview-desktop.png'), fullPage: true });
    await page.getByRole('button', { name: 'Already paid? Check access', exact: true }).click();
    const dialog = page.getByRole('dialog'); await dialog.waitFor(); await noOverflow('payment recovery');
    await page.screenshot({ path: path.join(evidence, 'feature-preview-payment-recovery-desktop.png') });
    await page.evaluate(() => window.__premiumFixture.markPaid());
    await dialog.getByRole('button', { name: 'Check payment status', exact: true }).click();
    await page.getByTestId('shell-profiles').waitFor();
    assert.equal(await preview().count(), 0); passed++;

    await page.goto(`${base}/?case=preview-paid`);
    await page.getByTestId('shell-profiles').waitFor();
    await page.evaluate(() => window.__premiumFixture.setView('app'));
    await page.getByTestId('shell-home').waitFor();
    assert.equal(await preview().count(), 0); passed++;

    for (const [locale, action] of [['nl', 'Log in en neem Premium'], ['es', 'Inicia sesión y obtén Premium']]) {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(`${base}/?case=preview-signedout&lang=${locale}`);
      await page.getByRole('button', { name: action, exact: true }).waitFor();
      assert.equal(await page.locator('html').getAttribute('lang'), locale);
      await noTrials(locale); await noChildren(locale); await noOverflow(locale);
      await page.waitForFunction(() => [...document.images].every(image => image.complete && image.naturalWidth > 0));
      await page.screenshot({ path: path.join(evidence, `feature-preview-${locale}-mobile.png`), fullPage: true });
      passed++;
    }

    assert.deepEqual(externalAttempts, [], 'preview and local recovery do not contact external services');
    assert.deepEqual(errors, [], 'no uncaught premium UI errors');
    console.log(`Paid-only preview UI passed ${passed} scenarios: 320/390/1440px; signed-out preview before auth; blocked trial/deep-link/cache; no app/profile/playback before payment; confirmed payment recovery and paid access. Evidence: ${evidence}`);
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
    await new Promise(resolve => denyProxy.close(resolve));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
