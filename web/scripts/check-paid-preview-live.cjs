// PUBLIC PRODUCTION SCREENSHOTS ONLY. A new, isolated headless browser profile
// is used; no user's Edge tabs, mouse, cookies, credentials or sessions are used.
// Every non-GET/HEAD browser request is blocked, including optional analytics.
// Never fill a form, start a trial, join a membership, or perform a payment.
// Run only after the intended production deployment is ready:
//   node scripts/check-paid-preview-live.cjs
const { chromium } = require('@playwright/test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = 'https://web.arvio.tv/';
const AUTH_ORIGIN = 'https://auth.arvio.tv';
const KOFI = 'https://ko-fi.com/arvio/tiers';
const evidence = path.resolve(__dirname, '../../artifacts/paid-only-live-20261023');

(async () => {
  fs.mkdirSync(evidence, { recursive: true });
  const report = { capturedAt: new Date().toISOString(), source: 'public production pages',
    safety: 'Fresh headless profile; only GET/HEAD; no credentials, customer sessions, form submissions or purchases.',
    captures: [], blockedRequests: [], consoleErrors: [], kofi: null };
  const browser = await chromium.launch({ ...(process.platform === 'win32' ? { channel: 'msedge' } : {}), headless: true,
    args: ['--disable-background-networking', '--disable-sync', '--disable-component-update'] });
  const isolatedContext = async viewport => {
    const context = await browser.newContext({ viewport, locale: 'en-US', serviceWorkers: 'block', extraHTTPHeaders: { DNT: '1' } });
    context.setDefaultTimeout(30000);
    await context.addInitScript(() => {
      try { Object.defineProperty(navigator, 'doNotTrack', { configurable: true, get: () => '1' }); } catch { /* server header also opts out */ }
    });
    await context.route('**/*', route => {
      const request = route.request();
      const url = new URL(request.url());
      if (!['GET', 'HEAD'].includes(request.method())) {
        report.blockedRequests.push({ method: request.method(), host: url.hostname, path: url.pathname });
        return route.abort('blockedbyclient');
      }
      if (!['https:', 'http:', 'data:', 'blob:'].includes(url.protocol)) return route.abort('blockedbyclient');
      return route.continue();
    });
    await context.routeWebSocket('**/*', socket => socket.close());
    context.on('page', page => page.on('pageerror', error => report.consoleErrors.push({ message: error.message.slice(0, 300), page: new URL(page.url()).hostname })));
    return context;
  };
  const capture = async (page, name, details = {}, fullPage = true) => {
    const file = path.join(evidence, name);
    const pixels = await page.screenshot({ path: file, fullPage, animations: 'disabled' });
    report.captures.push({ file, url: page.url(), width: pixels.readUInt32BE(16), height: pixels.readUInt32BE(20), ...details });
  };
  try {
    for (const [name, width, height] of [['desktop', 1440, 900], ['mobile', 390, 844]]) {
      const context = await isolatedContext({ width, height });
      try {
        const page = await context.newPage();
        const response = await page.goto(WEB, { waitUntil: 'domcontentloaded' });
        assert.equal(response.status(), 200, `${name}: public web app is available`);
        const preview = page.getByTestId('web-feature-preview');
        await preview.waitFor({ state: 'visible' });
        await page.waitForFunction(() => [...document.images].every(image => image.complete && image.naturalWidth > 0));
        await page.evaluate(() => document.fonts.ready);
        assert.equal(new URL(page.url()).origin, new URL(WEB).origin, 'preview does not automatically redirect to authentication');
        const content = await preview.innerText();
        for (const feature of ['Live TV', 'Plex', 'Emby', 'Jellyfin', 'Trakt', 'Simkl', 'Cloud']) assert.match(content, new RegExp(feature, 'i'));
        assert.match(content, /\$2\.99/);
        assert.equal(await page.getByRole('button', { name: /free trial|start.*trial|connect.*trial/i }).count(), 0);
        assert.equal(await page.getByRole('link', { name: /free trial|start.*trial|connect.*trial/i }).count(), 0);
        assert.equal(await page.locator('a[href*="intent=trial"], video, audio, .app-shell').count(), 0, 'no trial handoff or live application is available');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${name}: no horizontal overflow`);
        await capture(page, `live-feature-preview-${name}.png`, { stage: 'Public product preview before authentication/payment' });
        assert.equal(report.captures.at(-1).width, width, `${name}: full-page capture stays within viewport`);

        // Static feature tabs are safe local UI actions; no playback is started.
        await page.getByRole('button', { name: 'Live TV', exact: true }).click();
        const image = page.getByTestId('feature-screenshot').locator('img');
        await image.evaluate(image => image.decode());
        assert.equal(await image.getAttribute('src'), '/premium/tv.png');
        await capture(page, `live-feature-preview-tv-${name}.png`, { stage: 'Live TV screenshot tab, not a live stream' });

        // This deliberate CTA may only enter the public Cloud sign-in page.
        // Do not type an email/password or activate any authentication action.
        await Promise.all([
          page.waitForURL(url => url.origin === AUTH_ORIGIN, { waitUntil: 'domcontentloaded' }),
          page.getByRole('button', { name: 'Connect & get Premium', exact: true }).click()
        ]);
        assert.equal(new URL(page.url()).origin, AUTH_ORIGIN);
        await page.locator('body').waitFor({ state: 'visible' });
        await page.evaluate(() => document.fonts.ready);
        await capture(page, `live-cloud-signin-${name}.png`, { stage: 'Public Cloud sign-in entry after explicit Connect CTA; no credentials submitted' });
      } finally { await context.close(); }
    }

    // A separate fresh context shows the public merchant membership page only.
    // Never click Join/Subscribe/Checkout, and never bypass an anti-bot challenge.
    const kofiContext = await isolatedContext({ width: 1440, height: 1000 });
    try {
      const page = await kofiContext.newPage();
      const response = await page.goto(KOFI, { waitUntil: 'domcontentloaded' });
      const title = await page.title();
      const body = (await page.locator('body').innerText()).slice(0, 12000);
      const challenge = response.status() === 403 || /just a moment|verify (?:that )?you are human|checking your browser|access denied|attention required/i.test(title + '\n' + body.slice(0, 3000)) ||
        await page.locator('#challenge-running, #challenge-stage, iframe[src*="challenges.cloudflare.com"]').count() > 0;
      if (challenge) {
        report.kofi = { url: page.url(), status: response.status(), outcome: 'Public page was challenged; stopped without bypass or interaction.' };
        console.log('Ko-fi public page challenged; no bypass or membership action attempted.');
      } else {
        assert.equal(new URL(page.url()).hostname, 'ko-fi.com', 'public Ko-fi destination remains on the expected provider');
        assert.match(title + '\n' + body, /ARVIO/i, 'public membership page identifies ARVIO');
        await page.evaluate(() => document.fonts.ready);
        await capture(page, 'live-kofi-memberships-desktop.png', { stage: 'Public Ko-fi membership offers only; no join/checkout/payment action' });
        report.kofi = { url: page.url(), status: response.status(), outcome: 'Public membership offers captured; checkout not opened.' };
      }
    } catch (error) {
      report.kofi = { outcome: 'Public page unavailable without interaction; stopped.', error: String(error.message).slice(0, 300) };
      console.log(`Ko-fi public preview not captured: ${String(error.message).slice(0, 200)}`);
    } finally { await kofiContext.close(); }
    assert.equal(report.consoleErrors.filter(error => ['web.arvio.tv', 'auth.arvio.tv'].includes(error.page)).length, 0, 'no uncaught public preview/auth UI errors');
    console.log(`Live public flow verified; ${report.captures.length} production captures. No sign-in, trials, purchases or mail. Evidence: ${evidence}`);
  } finally {
    fs.writeFileSync(path.join(evidence, 'verification.json'), JSON.stringify(report, null, 2));
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
