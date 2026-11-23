export const AUTOMATIC_REFRESH_COOLDOWN_MS = 30_000;

export function shouldRefreshAutomatically(options: {
  visible: boolean;
  playing: boolean;
  inFlight: boolean;
  lastRefreshAt: number | null;
  now: number;
}): boolean {
  return options.visible && !options.playing && !options.inFlight &&
    (options.lastRefreshAt === null ||
      options.now - options.lastRefreshAt >= AUTOMATIC_REFRESH_COOLDOWN_MS);
}
