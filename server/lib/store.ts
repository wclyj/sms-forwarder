import { createHash } from "crypto";
import type { Report } from "./notify";

const KEY = "sms:history";
const MAX = 200;
const g = globalThis as unknown as { __hist?: Report[] };
export const history: Report[] = (g.__hist ??= []);

function redisOn(): boolean {
  return Boolean(process.env.UPSTASH_REDIS_REST_URL && process.env.UPSTASH_REDIS_REST_TOKEN);
}

function idOf(r: Report): string {
  return createHash("sha256").update(`${r.sender}\0${r.content}\0${r.receivedAt}`).digest("hex");
}

function rememberLocal(r: Report): boolean {
  const id = idOf(r);
  if (history.some((item) => idOf(item) === id)) return false;
  history.unshift(r);
  if (history.length > MAX) history.length = MAX;
  return true;
}

async function command(args: (string | number)[]): Promise<unknown> {
  const base = (process.env.UPSTASH_REDIS_REST_URL as string).replace(/\/$/, "");
  const res = await fetch(base, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${process.env.UPSTASH_REDIS_REST_TOKEN}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(args),
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) throw new Error(`redis ${res.status}`);
  const data = (await res.json()) as { result?: unknown; error?: string };
  if (data.error) throw new Error(data.error);
  return data.result;
}

function parseRow(raw: unknown): Report | null {
  if (typeof raw !== "string") return null;
  try {
    const value = JSON.parse(raw) as Report;
    if (!value || typeof value.content !== "string" || typeof value.sender !== "string") return null;
    return value;
  } catch {
    return null;
  }
}

export async function add(r: Report): Promise<boolean> {
  if (!redisOn()) return rememberLocal(r);
  try {
    const created = await command(["SET", `sms:seen:${idOf(r)}`, "1", "EX", 86400, "NX"]);
    if (created !== "OK") return false;
    await command(["LPUSH", KEY, JSON.stringify(r)]);
    await command(["LTRIM", KEY, 0, MAX - 1]);
    rememberLocal(r);
    return true;
  } catch {
    console.error("redis history failed");
    return rememberLocal(r);
  }
}

export async function list(): Promise<Report[]> {
  if (!redisOn()) return history.slice();
  try {
    const rows = await command(["LRANGE", KEY, 0, MAX - 1]);
    if (!Array.isArray(rows)) return history.slice();
    return rows.flatMap((raw) => {
      const row = parseRow(raw);
      return row ? [row] : [];
    });
  } catch {
    console.error("redis history read failed");
    return history.slice();
  }
}
