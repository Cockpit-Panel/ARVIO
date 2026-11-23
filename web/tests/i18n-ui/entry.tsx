import React from 'react';
import {createRoot} from 'react-dom/client';
import {FixtureStore, useApp} from './store';
import {LanguageProvider} from '../../lib/i18n';
import {TopNav} from '../../components/shell/TopNav';
import {WatchlistScreen} from '../../components/watchlist/WatchlistScreen';
import {SettingsScreen} from '../../components/settings/SettingsScreen';
import {SearchScreen} from '../../components/search/SearchScreen';
function App() {
  const {settings, section} = useApp();
  return <LanguageProvider language={settings.language}><main className="app-shell oled">
    <TopNav/>
    {section==='settings' ? <SettingsScreen/> : section==='search' ? <SearchScreen/> : <WatchlistScreen/>}
  </main></LanguageProvider>;
}
createRoot(document.getElementById('root')!).render(<FixtureStore><App/></FixtureStore>);
