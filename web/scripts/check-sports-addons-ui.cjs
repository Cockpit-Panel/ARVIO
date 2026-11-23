const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

(async () => {
  const output = path.resolve(__dirname, '../../artifacts/sports-addons');
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    for (const [name, width, height] of [['desktop', 1440, 1000], ['tablet', 820, 1180], ['mobile', 390, 844]]) {
      const page = await browser.newPage({ viewport: { width, height } });
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      let streamRequests = 0;
      let catalogRequests = 0;
      await page.route('**/api/proxy?**', async route => {
        const target = new URL(route.request().url()).searchParams.get('url') || '';
        if (target.includes('example.invalid/sports/catalog/')) {
          catalogRequests++;
          return route.fulfill({ json: { metas: [{ id: 'sport:fixture', type: 'sport', name: 'Stuttgart vs Viking',
            genres: ['Football'], releaseInfo: 'LIVE', description: 'LIVE NOW',
            released: new Date(Math.floor(Date.now() / 3600000) * 3600000).toISOString() }] } });
        }
        if (target.includes('example.invalid/sports/stream/')) {
          streamRequests++;
          return route.fulfill({ json: { streams: [{ name: 'Test add-on HD', url: 'https://example.invalid/test.mp4',
            behaviorHints: { proxyHeaders: { request: { Referer: 'https://example.invalid' } } } }] } });
        }
        return route.fulfill({ json: { metas: [], streams: [] } });
      });
      await page.route('**/api/sports/**', route => route.fulfill({ json: { events: [], fixtures: [], broadcasts: [] } }));
      await page.goto(process.env.SPORTS_UI_URL || 'http://localhost:3108/dev/stabilization', { waitUntil: 'domcontentloaded' });
      await expect(page.locator('[data-fixture-ready="true"]')).toBeVisible({ timeout: 120000 });
      await expect(page.getByRole('button', { name: /^Stuttgart vs Viking/ }).first()).toBeVisible({ timeout: 30000 });
      const sports = page.getByRole('button', { name: 'Sports', exact: true });
      if (!await sports.isVisible()) {
        const categories = page.getByRole('button', { name: 'Categories', exact: true }).first();
        if (await categories.isVisible()) await categories.click();
      }
      await sports.click();
      const card = page.locator('.tv-event-card').filter({ hasText: 'Stuttgart vs Viking' }).first();
      try { await expect(card).toBeVisible({ timeout: 30000 }); }
      catch (error) { console.log({ name, catalogRequests, errors }); throw error; }
      await expect(card).toContainText(/\d+ guide match/, { timeout: 30000 });
      assert.equal(streamRequests, 0, 'Catalog matching must not resolve streams eagerly');
      await card.click();
      const picker = page.locator('dialog[open]');
      await expect(picker.getByRole('button').filter({ hasText: 'Guide match' }).first()).toBeVisible();
      await expect(picker.getByRole('button', { name: /Sports fixture/ })).toBeVisible();
      await picker.getByRole('button', { name: /Sports fixture/ }).click();
      await expect(picker.getByRole('button', { name: /Test add-on HD/ })).toBeVisible();
      await page.screenshot({ path: path.join(output, `${name}-combined-picker.png`) });
      await picker.getByRole('button', { name: 'Close', exact: true }).click();
      await page.getByRole('button', { name: 'Remove test playlist', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Add your IPTV playlist' })).toHaveCount(0);
      await expect(card).toBeVisible({ timeout: 15000 });
      await card.click();
      await picker.getByRole('button', { name: /Sports fixture/ }).click();
      await expect(picker.getByRole('button', { name: /Test add-on HD/ })).toBeEnabled();
      await page.screenshot({ path: path.join(output, `${name}-addon-only-picker.png`) });
      await picker.getByRole('button', { name: /Test add-on HD/ }).click();
      await expect(page.locator('[data-play-requests="1"]')).toBeVisible();
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'No page overflow');
      assert.deepEqual(errors, []);
      console.log(`${name}: combined and add-on-only source selection passed; ${streamRequests} on-demand requests`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
