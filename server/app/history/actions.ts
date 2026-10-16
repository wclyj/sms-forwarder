"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { checkToken, tokenDigest } from "@/lib/auth";

export async function login(formData: FormData) {
  const token = String(formData.get("token") ?? "");
  if (!checkToken(token)) redirect("/history?error=1");
  cookies().set("sf_token", tokenDigest(token), {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 60 * 60 * 24 * 30,
  });
  redirect("/history");
}

export async function logout() {
  cookies().delete("sf_token");
  redirect("/history");
}
