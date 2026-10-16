import { describe, it, expect } from "vitest";
import { extract, extractCode } from "@/lib/extract";

describe("extract", () => {
  it("淘宝", () => expect(extract("【淘宝】您的验证码是 384912，5分钟内有效。")).toEqual({ code: "384912", platform: "淘宝" }));
  it("码在关键词前", () => expect(extractCode("【京东】8364 是您的登录校验码，请勿泄露")).toBe("8364"));
  it("英文", () => expect(extract("[Google] G-123456 is your Google verification code.").code).toBe("123456"));
  it("英文 code", () => expect(extractCode("Your code is 7731")).toBe("7731"));
  it("字母数字", () => expect(extractCode("【Steam】您的验证码：A7K9Q2")).toBe("A7K9Q2"));
  it("忽略手机号", () => expect(extractCode("【银行】尊敬的13812345678用户，您的验证码为 552018")).toBe("552018"));
  it("无关键词", () => expect(extractCode("您的快递 123456 已到达")).toBeNull());
  it("多数字选最近", () => expect(extractCode("【美团】订单2024金额99，验证码 660123，10分钟有效")).toBe("660123"));
  it("无平台", () => expect(extract("验证码 9981").platform).toBeNull());
  it("encode 里的 code 不算关键词", () => expect(extractCode("请把订单 encode 成 123456")).toBeNull());
  it("繁体", () => expect(extract("【銀行】您的驗證碼是 102938")).toEqual({ code: "102938", platform: "銀行" }));
});
