import { loadStored, saveStored } from "./storage";
import type { CalendarRelease } from "./calendar";

interface Projection { key: string; expires: number; releases: CalendarRelease[] }
const STORAGE_KEY = "arvio.web.calendarTitles.v1";
let projections: Map<string, Projection> | undefined;
function records() {
  if (!projections) {
    const stored = loadStored<unknown>(STORAGE_KEY, []);
    projections = new Map((Array.isArray(stored) ? stored : []).filter((row): row is Projection =>
      row && typeof row.key === "string" && Number.isFinite(row.expires) && row.expires > Date.now()
      && Array.isArray(row.releases)).map(row => [row.key, row]));
  }
  return projections;
}
export function readCalendarTitle(key: string): CalendarRelease[] | undefined {
  const row = records().get(key);
  return row && row.expires > Date.now() ? row.releases : undefined;
}
export function rememberCalendarTitle(key: string, releases: CalendarRelease[], ttl: number) {
  records().delete(key);
  records().set(key, { key, expires: Date.now() + ttl, releases });
}
export function persistCalendarTitles() {
  // Private, profile-partitioned projections include verified empty months.
  const recent = [...records().values()].filter(row => row.expires > Date.now()).reverse();
  let bytes = 0;
  const kept = recent.filter((row, index) => {
    bytes += JSON.stringify(row).length * 2;
    return index < 1024 && bytes <= 2_000_000;
  });
  projections = new Map(kept.reverse().map(row => [row.key, row]));
  saveStored(STORAGE_KEY, [...projections.values()]);
}
