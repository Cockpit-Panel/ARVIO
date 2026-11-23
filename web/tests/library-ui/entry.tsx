import React from "react";
import { createRoot } from "react-dom/client";
import { WatchlistScreen } from "../../components/watchlist/WatchlistScreen";
import { installTvNav } from "../../lib/tvNav";
installTvNav();
import { Home, Search, Bookmark, Tv, Settings } from "lucide-react";
createRoot(document.getElementById("root")!).render(<main className="app-shell oled" style={{"--accent":"#fff"} as React.CSSProperties}>
  <header className="library-test-header"><span className="library-test-avatar">P</span><nav><span><Search/>Search</span><span><Home/>Home</span><span className="active"><Bookmark/>Library</span><span><Tv/>TV</span></nav><span><Settings/>21:08</span></header>
  <WatchlistScreen/>
</main>);
