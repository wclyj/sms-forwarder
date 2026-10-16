import nodemailer from "nodemailer";

export interface Report {
  device: string;
  phone?: string;
  sim?: string;
  sender: string;
  content: string;
  receivedAt: string;
  code: string | null;
  platform: string | null;
}

function clip(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max)}…`;
}

function oneLine(s: string): string {
  return s.replace(/[\r\n]+/g, " ").trim();
}

export async function pushWecom(r: Report): Promise<string> {
  const url = process.env.WECOM_WEBHOOK_URL;
  if (!url) return "skipped";
  const md = [
    `### 验证码 <font color="warning">${r.code ?? "未识别"}</font>`,
    `> 平台：${r.platform ?? "-"}`,
    `> 设备：${oneLine(r.device)}`,
    `> 号码/卡：${oneLine(r.phone ?? r.sim ?? "-")}`,
    `> 发送方：${oneLine(r.sender)}`,
    `> 时间：${oneLine(r.receivedAt)}`,
    "",
    clip(r.content, 1500),
  ].join("\n");
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ msgtype: "markdown", markdown: { content: md } }),
    signal: AbortSignal.timeout(8000),
  });
  let payload: { errcode?: number } | null = null;
  try {
    payload = (await res.json()) as { errcode?: number };
  } catch {
    payload = null;
  }
  if (!res.ok) return `error ${res.status}`;
  if (payload && typeof payload.errcode === "number" && payload.errcode !== 0) return `error ${payload.errcode}`;
  return "ok";
}

export async function pushEmail(r: Report): Promise<string> {
  const { SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, MAIL_TO } = process.env;
  if (!SMTP_HOST || !MAIL_TO) return "skipped";
  if (!SMTP_USER || !SMTP_PASS) return "error: smtp incomplete";
  const port = Number(SMTP_PORT || 465);
  const transport = nodemailer.createTransport({
    host: SMTP_HOST,
    port,
    secure: port === 465,
    requireTLS: port === 587,
    auth: { user: SMTP_USER, pass: SMTP_PASS },
    connectionTimeout: 8000,
    socketTimeout: 8000,
  });
  const subject = `【${oneLine(r.platform ?? "验证码")}】${oneLine(r.code ?? "未识别")} - ${oneLine(r.device)}`.slice(0, 180);
  await transport.sendMail({
    from: SMTP_USER,
    to: MAIL_TO,
    subject,
    text: [
      `验证码：${r.code ?? "未识别"}`,
      `平台：${r.platform ?? "-"}`,
      `设备：${r.device}`,
      `号码/卡：${r.phone ?? r.sim ?? "-"}`,
      `发送方：${r.sender}`,
      `时间：${r.receivedAt}`,
      "",
      r.content,
    ].join("\n"),
  });
  return "ok";
}
