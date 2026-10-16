import { checkToken } from "./auth";
import { extract } from "./extract";
import { pushEmail, pushWecom, type Report } from "./notify";
import { add } from "./store";

export interface ReportResult {
  status: number;
  body: Record<string, unknown>;
}

function clip(value: unknown, max: number, fallback = ""): string {
  if (typeof value !== "string") return fallback;
  const text = value.trim();
  return text.length <= max ? text : text.slice(0, max);
}

function optional(value: unknown, max: number): string | undefined {
  const text = clip(value, max);
  return text || undefined;
}

function settled(result: PromiseSettledResult<string>): string {
  if (result.status === "fulfilled") return result.value;
  const reason = result.reason instanceof Error ? result.reason.message : String(result.reason);
  return `error: ${reason}`.slice(0, 300);
}

export async function handleReport(input: { token: string | null; body: unknown }): Promise<ReportResult> {
  if (!checkToken(input.token)) return { status: 401, body: { error: "unauthorized" } };
  if (!input.body || typeof input.body !== "object" || Array.isArray(input.body)) {
    return { status: 400, body: { error: "bad json" } };
  }
  const raw = input.body as Record<string, unknown>;
  const content = clip(raw.content, 4000);
  if (!content) return { status: 400, body: { error: "content required" } };

  let receivedAt = clip(raw.receivedAt, 40);
  if (!/\d/.test(receivedAt)) receivedAt = new Date().toISOString();
  const { code, platform } = extract(content);
  const report: Report = {
    device: clip(raw.device, 64, "unknown") || "unknown",
    phone: optional(raw.phone, 32),
    sim: optional(raw.sim, 64),
    sender: clip(raw.sender, 64, "-") || "-",
    content,
    receivedAt,
    code,
    platform,
  };

  const fresh = await add(report);
  if (!fresh) {
    return { status: 200, body: { ok: true, duplicate: true, code, platform, wecom: "skipped", email: "skipped" } };
  }
  const [wecom, email] = await Promise.allSettled([pushWecom(report), pushEmail(report)]);
  return {
    status: 200,
    body: { ok: true, code, platform, wecom: settled(wecom), email: settled(email) },
  };
}
