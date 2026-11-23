const { chromium, expect } = require('@playwright/test');
const esbuild = require('esbuild');
const path = require('node:path');
const fs = require('node:fs');

// Opt-in live check. Credentials stay in the environment and browser memory.
(async () => {
  const manifestUrl = process.env.SPORTS_TEST_MANIFEST;
  if (!manifestUrl) throw Error('SPORTS_TEST_MANIFEST is required');
  const browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--mute-audio'] });
  let page;
  try {
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' });
    page.on('response', response => {
      const url = new URL(response.url());
      if (response.status() >= 400 && !url.href.includes('example.invalid')) console.log(JSON.stringify({
        requestFailed: url.hostname, status: response.status()
      }));
    });
    page.on('requestfailed', request => console.log(JSON.stringify({ networkFailure: new URL(request.url()).hostname, reason: request.failure()?.errorText })));
    page.on('console', message => { if (message.type() === 'error') console.log(message.text().replace(/https?:\/\/[^\s"']+/g, '[URL redacted]')); });
    await page.goto(`${process.env.SPORTS_TEST_BASE_URL || 'http://localhost:3108'}/dev/stabilization?sourcePlayback=1`);
    await expect(page.getByRole('button', { name: /^Stuttgart vs Viking/ }).first()).toBeVisible({ timeout: 30000 });
    const bundle = await esbuild.build({ entryPoints: [path.resolve(__dirname, '../lib/sportsAddons.ts')], bundle: true,
      write: false, format: 'iife', globalName: 'SportsQA', define: { 'process.env': '{}' } });
    await page.addScriptTag({ content: bundle.outputFiles[0].text });
    const result = await page.evaluate(async ({ manifestUrl, offset }) => {
      const proxy = url => `/api/proxy?url=${encodeURIComponent(url)}`;
      const response = await fetch(proxy(manifestUrl));
      if (!response.ok) throw Error(`Manifest request failed: ${response.status}`);
      const manifest = await response.json();
      const addon = { ...manifest, manifestUrl, enabled: true };
      const started = performance.now(); let first = 0;
      const events = await SportsQA.loadSportsAddonEvents([addon], AbortSignal.timeout(90000), batch => {
        if (!first && batch.length) first = performance.now() - started;
      });
      const now = Date.now(), live = events.filter(e => SportsQA.addonEventIsLive(e, now));
      const stats = { addon: addon.name, events: events.length, live: live.length, firstMs: Math.round(first), totalMs: Math.round(performance.now() - started) };
      for (const event of live.slice(offset, offset + 3)) {
        try {
          const streams = await SportsQA.resolveSportsAddon(event, [addon], AbortSignal.timeout(16000));
          const direct = streams.filter(s => !s.external);
          if (direct.length) return { stats, event, streams: direct };
        } catch { /* One unavailable event must not mask the next live source. */ }
      }
      return { stats };
    }, { manifestUrl, offset: Number(process.env.SPORTS_TEST_EVENT_OFFSET || 0) });
    console.log(JSON.stringify(result.stats));
    if (!result.event) throw Error('No live event with direct sources available for playback verification');
    const { event, streams } = result;
    console.log(JSON.stringify({ event: event.title, source: streams[0].name, host: new URL(streams[0].url).hostname, headerNames: Object.keys(streams[0].headers) }));
    console.log(JSON.stringify(await page.evaluate(async url => {
      try {
        const response = await fetch(`/api/proxy?url=${encodeURIComponent(url)}&rewrite=direct`, { signal: AbortSignal.timeout(20000) });
        const body = await response.text();
        return { manifestProbe: response.status, contentType: response.headers.get('content-type'), hls: body.trimStart().startsWith('#EXTM3U'), bytes: body.length };
      } catch { return { manifestProbe: 'timeout or network error' }; }
    }, streams[0].url)));
    await page.route('**/api/proxy?**', route => {
      const url = new URL(route.request().url()).searchParams.get('url') || '';
      if (url.includes('example.invalid/sports/catalog/')) return route.fulfill({ json: { metas: [{
        id: event.eventId, type: event.type, name: event.title, genres: event.genres, releaseInfo: 'LIVE',
        released: event.startsAt ? new Date(event.startsAt).toISOString() : undefined,
        background: event.artwork
      }] } });
      if (url.includes('example.invalid/sports/stream/')) return route.fulfill({ json: { streams: streams.map(s => ({
        name: s.name, title: s.description, url: s.url, behaviorHints: { proxyHeaders: { request: s.headers } }
      })) } });
      if (url.includes('example.invalid')) return route.fulfill({ json: {} });
      return route.continue();
    });
    await page.reload();
    await expect(page.locator('[data-fixture-ready="true"]')).toBeVisible();
    await expect(page.getByRole('button', { name: /^Stuttgart vs Viking/ }).first()).toBeVisible({ timeout: 30000 });
    await page.getByRole('button', { name: 'Sports', exact: true }).click();
    const card = page.locator('.tv-event-card').filter({ hasText: event.title }).first();
    await expect(card).toBeVisible({ timeout: 30000 });
    await card.click();
    const picker = page.locator('dialog[open]');
    await picker.getByRole('button', { name: /Sports fixture/ }).click();
    await picker.getByRole('button').filter({ hasText: streams[0].name }).last().click();
    await page.waitForFunction(() => [...document.querySelectorAll('video')].some(v =>
      v.videoWidth > 0 && v.readyState >= 2 && v.currentTime > 2 && !v.paused), null, { timeout: 90000 });
    const before = await page.locator('video').first().evaluate(v => ({ time: v.currentTime, frames: v.getVideoPlaybackQuality().totalVideoFrames }));
    await page.waitForTimeout(8000);
    const after = await page.locator('video').first().evaluate(v => ({ time: v.currentTime, frames: v.getVideoPlaybackQuality().totalVideoFrames,
      width: v.videoWidth, height: v.videoHeight, error: v.error?.code ?? null }));
    if (after.time <= before.time + 3 || after.frames <= before.frames || after.error) throw Error('Playback did not sustain advancing video frames');
    const output = path.resolve(__dirname, '../../artifacts/sports-addons/browser-real-playback.png');
    fs.mkdirSync(path.dirname(output), { recursive: true });
    await page.screenshot({ path: output });
    console.log(JSON.stringify({ event: event.title, source: streams[0].name, ...after, playback: 'verified' }));
  } catch (error) {
    if (page) {
      const output = path.resolve(__dirname, '../../artifacts/sports-addons/browser-playback-failure.png');
      fs.mkdirSync(path.dirname(output), { recursive: true });
      await page.screenshot({ path: output }).catch(() => {});
      console.log(JSON.stringify(await page.locator('video').evaluateAll(videos => videos.map(v => ({
        time: v.currentTime, width: v.videoWidth, height: v.videoHeight, readyState: v.readyState,
        paused: v.paused, error: v.error?.code ?? null
      })))));
    }
    throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(String(error.message).replace(/https?:\/\/[^\s"']+/g, '[URL redacted]')); process.exitCode = 1; });
