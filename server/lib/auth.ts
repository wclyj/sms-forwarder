import { createHash, timingSafeEqual } from "crypto";

const PLACEHOLDER = "change-me-to-a-long-random-string";

export function tokenConfigured(): boolean {
  const expected = process.env.API_TOKEN;
  return !!expected && expected.length >= 16 && expected !== PLACEHOLDER;
}

function digest(value: string): Buffer {
  return createHash("sha256").update(value).digest();
}

export function tokenDigest(value: string): string {
  return digest(value).toString("hex");
}

export function checkToken(token: string | null | undefined): boolean {
  if (!token || !tokenConfigured()) return false;
  const actual = digest(token);
  const expected = digest(process.env.API_TOKEN as string);
  return timingSafeEqual(actual, expected);
}

export function checkTokenDigest(hex: string | null | undefined): boolean {
  if (!hex || !/^[a-f0-9]{64}$/i.test(hex) || !tokenConfigured()) return false;
  return timingSafeEqual(Buffer.from(hex, "hex"), digest(process.env.API_TOKEN as string));
}
