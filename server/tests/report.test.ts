import { beforeEach, describe, expect, it } from "vitest";
import { handleReport } from "@/lib/report";
import { history } from "@/lib/store";

const TOKEN = "test-token-value-1";

beforeEach(() => {
  history.length = 0;
  process.env.API_TOKEN = TOKEN;
  delete process.env.WECOM_WEBHOOK_URL;
  delete process.env.SMTP_HOST;
  delete process.env.SMTP_USER;
  delete process.env.SMTP_PASS;
  delete process.env.MAIL_TO;
  delete process.env.UPSTASH_REDIS_REST_URL;
  delete process.env.UPSTASH_REDIS_REST_TOKEN;
});

describe("handleReport", () => {
  it("拒绝错误口令，且不入库", async () => {
    const result = await handleReport({
      token: "wrong-token-value",
      body: { content: "【淘宝】验证码 384912" },
    });
    expect(result.status).toBe(401);
    expect(history).toHaveLength(0);
  });

  it("提取验证码并跳过未配置的推送", async () => {
    const result = await handleReport({
      token: TOKEN,
      body: {
        device: "小米13",
        sim: "SIM1",
        sender: "1069",
        content: "【淘宝】您的验证码是384912",
        receivedAt: "2026-10-16T13:00:00+08:00",
      },
    });
    expect(result).toEqual({
      status: 200,
      body: { ok: true, code: "384912", platform: "淘宝", wecom: "skipped", email: "skipped" },
    });
  });

  it("相同短信不重复推送", async () => {
    const body = { sender: "1069", content: "【淘宝】验证码 384912", receivedAt: "2026-10-16T13:00:00+08:00" };
    await handleReport({ token: TOKEN, body });
    const again = await handleReport({ token: TOKEN, body });
    expect(again.body.duplicate).toBe(true);
    expect(history).toHaveLength(1);
  });

  it("截断过长正文", async () => {
    const content = `验证码 123456 ${"啊".repeat(5000)}`;
    await handleReport({ token: TOKEN, body: { content } });
    expect(history[0].content.length).toBe(4000);
    expect(history[0].code).toBe("123456");
  });

  it("缺少正文", async () => {
    const result = await handleReport({ token: TOKEN, body: { device: "x" } });
    expect(result.status).toBe(400);
  });
});
