import { NextRequest, NextResponse } from "next/server";
import { rateLimit } from "@/lib/rate";
import { handleReport } from "@/lib/report";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function clientIp(req: NextRequest): string {
  const forwarded = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  return forwarded || req.ip || "local";
}

export async function POST(req: NextRequest) {
  if (!rateLimit(clientIp(req))) {
    return NextResponse.json({ error: "rate limited" }, { status: 429 });
  }
  const token =
    req.headers.get("x-api-token") ?? req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? null;
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "bad json" }, { status: 400 });
  }
  const result = await handleReport({ token, body });
  return NextResponse.json(result.body, { status: result.status });
}
