import type { CatalogConfig, CollectionSourceConfig } from "./types";

type Obj = Record<string, unknown>;
const object = (value: unknown): Obj => value && typeof value === "object" && !Array.isArray(value) ? value as Obj : {};
const text = (value: unknown): string => typeof value === "string" || typeof value === "number" ? String(value).trim() : "";
const slug = (value: string) => value.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "") || "x";
const array = (value: unknown): unknown[] => Array.isArray(value) ? value : [];

function parseSource(raw: unknown): CollectionSourceConfig | null {
  const s = object(raw);
  const provider = (text(s.provider) || "addon").toLowerCase();
  if (provider === "addon") {
    if (!text(s.type) || !text(s.catalogId)) return null;
    return { kind: "ADDON_CATALOG", mediaType: text(s.type), addonId: text(s.addonId) || null,
      addonCatalogType: text(s.type), addonCatalogId: text(s.catalogId), addonGenre: text(s.genre) || null };
  }
  if (provider === "trakt") return text(s.traktListId) && text(s.traktListId) !== "0"
    ? { kind: "TRAKT_LIST", traktListId: text(s.traktListId) } : null;
  if (provider === "mdblist") return text(s.slug ?? s.mdblistSlug)
    ? { kind: "MDBLIST_PUBLIC", mdblistSlug: text(s.slug ?? s.mdblistSlug) } : null;
  if (provider !== "tmdb") return null;
  const type = text(s.tmdbSourceType).toUpperCase();
  const id = Number(s.tmdbId);
  if (s.tmdbId != null && (!Number.isInteger(id) || id <= 0)) throw new Error("Invalid TMDB ID");
  if (type === "COLLECTION" || type === "LIST") return id > 0
    ? { kind: type === "LIST" ? "TMDB_LIST" : "TMDB_COLLECTION", ...(type === "LIST" ? { tmdbListId: id } : { tmdbCollectionId: id }) } : null;
  const tv = type === "NETWORK" || ["TV", "SERIES", "SHOW"].includes(text(s.mediaType).toUpperCase());
  if (type === "PERSON" || type === "DIRECTOR") {
    if (Object.values(object(s.filters)).some(value => value != null && text(value))) throw new Error("Additional filters on person/director collections are not supported");
    return id > 0 ? { kind: "TMDB_PERSON", tmdbPersonId: id, tmdbCreditRole: type === "DIRECTOR" ? "Director" : "Cast",
      mediaType: tv ? "tv" : "movie", sortBy: text(s.sortBy) && s.sortBy !== "original" ? text(s.sortBy) : null } : null;
  }
  const params: Record<string, string> = {};
  const sourceKeys: Record<string, string> = { COMPANY: "with_companies", NETWORK: "with_networks", PERSON: tv ? "with_people" : "with_cast", DIRECTOR: "with_crew" };
  if (sourceKeys[type]) {
    if (!(id > 0)) return null;
    params[sourceKeys[type]] = String(id);
  } else if (type !== "DISCOVER") return null;
  const f = object(s.filters);
  const keys: Record<string, string> = {
    withGenres: "with_genres", withoutGenres: "without_genres", voteAverageGte: "vote_average.gte",
    voteAverageLte: "vote_average.lte", voteCountGte: "vote_count.gte", withOriginalLanguage: "with_original_language",
    withOriginCountry: "with_origin_country", withKeywords: "with_keywords", withoutKeywords: "without_keywords",
    withCompanies: "with_companies", withoutCompanies: "without_companies", watchRegion: "watch_region",
    withWatchProviders: "with_watch_providers", withoutWatchProviders: "without_watch_providers",
    releaseDateGte: tv ? "first_air_date.gte" : "primary_release_date.gte",
    releaseDateLte: tv ? "first_air_date.lte" : "primary_release_date.lte",
    year: tv ? "first_air_date_year" : "primary_release_year", ...(tv ? { withNetworks: "with_networks" } : {})
  };
  for (const [key, dest] of Object.entries(keys)) if (text(f[key])) params[dest] = text(f[key]);
  return { kind: "TMDB_DISCOVER", mediaType: tv ? "tv" : "movie", discoverParams: params,
    sortBy: text(s.sortBy) && s.sortBy !== "original" ? text(s.sortBy) : null };
}

export async function parseCustomCollections(json: string, url?: string): Promise<CatalogConfig[]> {
  if (json.length > 2_000_000) throw new Error("Collections document is too large");
  const root: unknown = JSON.parse(json);
  const obj = object(root);
  const collections = (Array.isArray(root) ? root : Array.isArray(obj.collections) ? obj.collections : Array.isArray(obj.folders) ? [obj] : [])
    .map(object).filter(c => text(c.title) && Array.isArray(c.folders));
  if (!collections.length || collections.length > 100) throw new Error("Invalid collections count");
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(url?.trim() ?? json)));
  const packId = `usercol_${Array.from(digest.slice(0, 6), b => b.toString(16).padStart(2, "0")).join("")}`;
  const packName = text(obj.name) || (collections.length === 1 ? text(collections[0].title) : "") || (url ? new URL(url).hostname : "Imported collections");
  const result: CatalogConfig[] = [];
  collections.forEach((c, ci) => {
    const railKey = `custom_${packId}_${slug(text(c.id) || `c${ci}`)}`;
    const folders: CatalogConfig[] = [];
    array(c.folders).forEach((raw, fi) => {
      const f = object(raw);
      if (!text(f.title)) return;
      const sources = (array(f.sources).length ? array(f.sources) : array(f.catalogSources)).map(parseSource).filter((s): s is CollectionSourceConfig => s !== null);
      if (!sources.length) return;
      folders.push({
        id: `collection_${railKey}_${slug(text(f.id) || `f${fi}`)}`, name: text(f.title), title: text(f.title),
        sourceType: "preinstalled", isPreinstalled: false, enabled: true, kind: "COLLECTION", collectionGroup: "NETWORK",
        collectionRailKey: railKey, packId, packName, collectionSources: sources,
        collectionCoverImageUrl: text(f.coverImageUrl) || (f.focusGifEnabled !== false ? text(f.focusGifUrl) : ""),
        collectionHeroImageUrl: text(f.heroBackdropUrl), collectionDescription: text(f.description),
        collectionTileShape: text(f.tileShape).toUpperCase() === "POSTER" ? "POSTER" : "LANDSCAPE",
        collectionHideTitle: f.hideTitle === true && Boolean(text(f.coverImageUrl) || text(f.focusGifUrl)),
        // Preserve Android-specific artwork through cloud round trips.
        ...{ collectionFocusGifUrl: f.focusGifEnabled !== false ? text(f.focusGifUrl) : null,
          collectionHeroVideoUrl: text(f.heroVideoUrl), collectionClearLogoUrl: text(f.titleLogoUrl) }
      });
    });
    if (folders.length) result.push({ id: `collection_rail_${railKey}`, name: text(c.title), title: text(c.title),
      sourceType: "preinstalled", isPreinstalled: false, enabled: true, kind: "COLLECTION_RAIL", collectionGroup: "NETWORK",
      collectionRailKey: railKey, packId, packName }, ...folders);
  });
  if (!result.length || result.filter(c => c.kind === "COLLECTION").length > 500) throw new Error("No supported folders, or too many folders");
  if (new Set(result.map(c => c.id)).size !== result.length) throw new Error("Duplicate collection or folder IDs");
  return result;
}

export function mergeImportedCollections(current: CatalogConfig[], imported: CatalogConfig[]): CatalogConfig[] {
  const packId = imported[0]?.packId;
  if (!packId) return current;
  const pending = new Map(imported.map(c => [c.id, c]));
  const next = current.flatMap(c => {
    if (c.packId !== packId) return [c];
    const replacement = pending.get(c.id);
    pending.delete(c.id);
    return replacement ? [{ ...replacement, name: c.name, title: c.title, enabled: c.enabled }] : [];
  });
  return [...next, ...pending.values()];
}
