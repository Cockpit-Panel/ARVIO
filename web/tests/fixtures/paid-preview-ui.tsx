import React from 'react';
import { createRoot } from 'react-dom/client';
import { AppShell } from '../../components/shell/AppShell';
import { LanguageProvider } from '../../lib/i18n';
import { fixture } from './premium-http';
import { setFixtureAccount, setFixtureView } from './premium-store';
import '../../app/globals.css';
import '../../app/premium.css';

// Only the premium shell, gate and preview are production UI. Playback, account
// identity and backend HTTP are isolated fixtures. No customer browser is used.
localStorage.clear();
if (new URLSearchParams(location.search).get('case') === 'preview-legacy') localStorage.setItem('arvio.premium.trial-intent.v1', '1');
if (new URLSearchParams(location.search).get('case') === 'preview-stale') localStorage.setItem('arvio.web.entitlement.v2:premium-fixture', JSON.stringify({
  at: Date.now(), state: { entitled: true, reason: 'trial', source: 'trial', status: 'active', expiresAt: '2099-01-01', trialAvailable: false }
}));
Object.assign(window, { __premiumFixture: fixture, __shellMounts: [], __authNavigateCount: 0 });
Object.assign(fixture, { setAccount: setFixtureAccount, setView: setFixtureView });
createRoot(document.getElementById('root')!).render(<LanguageProvider language={new URLSearchParams(location.search).get('lang') || 'en'}><AppShell /></LanguageProvider>);
