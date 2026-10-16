const hits = new Map<string, number[]>();

/** 返回 true 表示本次允许通过。 */
export function rateLimit(key: string, limit = 60, windowMs = 60_000, now = Date.now()): boolean {
  const recent = (hits.get(key) ?? []).filter((t) => now - t < windowMs);
  if (recent.length >= limit) {
    hits.set(key, recent);
    return false;
  }
  recent.push(now);
  hits.set(key, recent);
  if (hits.size > 5000) hits.clear();
  return true;
}

export function resetRateLimit(): void {
  hits.clear();
}
