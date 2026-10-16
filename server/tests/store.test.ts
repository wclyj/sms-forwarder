import { beforeEach, describe, expect, it } from "vitest";
import type { Report } from "@/lib/notify";
import { add, history, list } from "@/lib/store";

const sample: Report = {
  device: "测试机",
  sender: "1069",
  content: "【淘宝】验证码 384912",
  receivedAt: "2026-10-16T13:00:00+08:00",
  code: "384912",
  platform: "淘宝",
};

beforeEach(() => {
  history.length = 0;
  delete process.env.UPSTASH_REDIS_REST_URL;
  delete process.env.UPSTASH_REDIS_REST_TOKEN;
});

describe("store", () => {
  it("按时间倒序保存，并忽略相同短信", async () => {
    expect(await add(sample)).toBe(true);
    expect(await add({ ...sample })).toBe(false);
    expect(await add({ ...sample, receivedAt: "2026-10-16T13:05:00+08:00" })).toBe(true);
    expect(history).toHaveLength(2);
    expect((await list())[0].receivedAt).toBe("2026-10-16T13:05:00+08:00");
  });
});
