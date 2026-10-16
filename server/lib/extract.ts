export interface Extracted {
  code: string | null;
  platform: string | null;
}

// 英文关键词按单词匹配，避免 encode / barcode 里的 code 被当成验证码短信。
const KW =
  /(验证码|校验码|动态码|确认码|激活码|驗證碼|verification code|security code|(?<![A-Za-z])(?:code|otp|pin)(?![A-Za-z]))/i;

export function extractPlatform(text: string): string | null {
  const m = text.match(/[【\[]([^】\]]{1,20})[】\]]/);
  return m ? m[1].trim() : null;
}

export function extractCode(text: string): string | null {
  if (!text) return null;
  const kw = text.match(KW);
  if (!kw || kw.index === undefined) return null;
  const isCand = (s: string) => /\d/.test(s) && s.length >= 4 && s.length <= 8 && !/^1[3-9]\d{9}$/.test(s);
  const re = /(?<![A-Za-z0-9])([A-Za-z0-9]{4,8})(?![A-Za-z0-9])/g;
  const cands: { v: string; dist: number }[] = [];
  let m: RegExpExecArray | null;
  const kwPos = kw.index;
  const kwEnd = kw.index + kw[0].length;
  while ((m = re.exec(text))) {
    const v = m[1];
    if (!isCand(v)) continue;
    const dist = m.index >= kwEnd ? m.index - kwEnd : kwPos - (m.index + v.length);
    const pure = /^\d+$/.test(v) ? 0 : 5;
    cands.push({ v, dist: Math.abs(dist) + pure });
  }
  if (!cands.length) return null;
  cands.sort((a, b) => a.dist - b.dist);
  return cands[0].v;
}

export function extract(text: string): Extracted {
  return { code: extractCode(text), platform: extractPlatform(text) };
}
