import { afterEach, describe, expect, it } from "vitest";
import { checkToken, checkTokenDigest, tokenConfigured, tokenDigest } from "@/lib/auth";

const TOKEN = "test-token-value-1";

afterEach(() => {
  delete process.env.API_TOKEN;
});

describe("auth", () => {
  it("接受足够长的口令", () => {
    process.env.API_TOKEN = TOKEN;
    expect(tokenConfigured()).toBe(true);
    expect(checkToken(TOKEN)).toBe(true);
    expect(checkToken(`${TOKEN}x`)).toBe(false);
    expect(checkTokenDigest(tokenDigest(TOKEN))).toBe(true);
    expect(checkTokenDigest("ab".repeat(32))).toBe(false);
  });

  it("拒绝占位符和过短口令", () => {
    process.env.API_TOKEN = "change-me-to-a-long-random-string";
    expect(checkToken("change-me-to-a-long-random-string")).toBe(false);
    process.env.API_TOKEN = "short-token";
    expect(tokenConfigured()).toBe(false);
    expect(checkToken("short-token")).toBe(false);
  });
});
