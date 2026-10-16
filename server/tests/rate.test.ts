import { beforeEach, describe, expect, it } from "vitest";
import { rateLimit, resetRateLimit } from "@/lib/rate";

beforeEach(() => {
  resetRateLimit();
});

describe("rateLimit", () => {
  it("窗口内超过上限后拒绝，过窗口后恢复", () => {
    for (let i = 0; i < 60; i++) expect(rateLimit("a", 60, 60_000, 1_000)).toBe(true);
    expect(rateLimit("a", 60, 60_000, 1_000)).toBe(false);
    expect(rateLimit("b", 60, 60_000, 1_000)).toBe(true);
    expect(rateLimit("a", 60, 60_000, 61_000)).toBe(true);
  });
});
