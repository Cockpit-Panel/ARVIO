import type { MediaItem } from "./types";

export type EpisodeAvailability = { exists: boolean; airDate?: string | null };
export const episodeAvailabilityKey = (item: MediaItem) =>
  `${item.traktId ? "trakt" : "tmdb"}:${item.traktId || item.tmdbId || item.id}:${item.seasonNumber}:${item.episodeNumber}`;

/** Cache metadata, not the time-dependent verdict: an episode can air while the app is open. */
export function createEpisodeValidator(lookup: (item: MediaItem) => Promise<EpisodeAvailability>) {
  const cache = new Map<string, { at: number; value: EpisodeAvailability }>();
  const pending = new Map<string, Promise<EpisodeAvailability>>();
  const read = async (item: MediaItem) => {
    const key = episodeAvailabilityKey(item);
    const cached = cache.get(key);
    if (cached && Date.now() - cached.at < (cached.value.exists ? 900_000 : 60_000)) return cached.value;
    const active = pending.get(key);
    if (active) return active;
    const request = lookup(item).then((value) => {
      cache.delete(key);
      cache.set(key, { at: Date.now(), value });
      if (cache.size > 600) cache.delete(cache.keys().next().value!);
      return value;
    }).finally(() => pending.delete(key));
    pending.set(key, request);
    return request;
  };
  return async (items: MediaItem[], rejected = new Set<string>(), onUnknown = () => {}) => {
    const results: Array<MediaItem | null> = new Array(items.length).fill(null);
    let cursor = 0;
    await Promise.all(Array.from({ length: Math.min(4, items.length) }, async () => {
      while (cursor < items.length) {
        const index = cursor++;
        const item = items[index];
        if (item.mediaType !== "tv") { results[index] = item; continue; }
        const key = episodeAvailabilityKey(item);
        if (!Number.isInteger(item.seasonNumber) || item.seasonNumber! < 0 ||
            !Number.isInteger(item.episodeNumber) || item.episodeNumber! < 1) {
          rejected.add(key);
          continue;
        }
        try {
          const metadata = await read(item);
          const airedAt = Date.parse(metadata.airDate ?? "");
          if (!metadata.exists || (Number.isFinite(airedAt) && airedAt > Date.now())) {
            rejected.add(key);
            continue;
          }
          if (!Number.isFinite(airedAt) && item.badge === "Up Next") {
            rejected.add(key);
            continue;
          }
          results[index] = item;
        } catch {
          // An outage is not proof that an existing saved playback session is invalid.
          onUnknown();
          if (item.badge !== "Up Next") results[index] = item;
        }
      }
    }));
    return results.filter((item): item is MediaItem => item !== null);
  };
}
