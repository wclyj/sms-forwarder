import { cookies } from "next/headers";
import { checkToken, checkTokenDigest, tokenConfigured } from "@/lib/auth";
import { list } from "@/lib/store";
import { login, logout } from "./actions";

export const dynamic = "force-dynamic";
export const metadata = { title: "验证码记录" };

const box: React.CSSProperties = { fontFamily: "sans-serif", padding: 24, lineHeight: 1.5, maxWidth: 960 };

export default async function Page({ searchParams }: { searchParams: { token?: string; error?: string } }) {
  if (!tokenConfigured()) {
    return (
      <main style={box}>
        <h1>验证码记录</h1>
        <p>服务器还没有配置有效的 API_TOKEN。请在环境变量里填一个至少 16 位的随机字符串。</p>
      </main>
    );
  }

  const cookie = cookies().get("sf_token")?.value;
  const authed = checkTokenDigest(cookie) || checkToken(searchParams.token);
  if (!authed) {
    return (
      <main style={box}>
        <h1>验证码记录</h1>
        {searchParams.error === "1" ? <p>API_TOKEN 不正确。</p> : <p>输入服务端配置的 API_TOKEN 后查看最近的短信。</p>}
        <form action={login} style={{ display: "grid", gap: 8, maxWidth: 360 }}>
          <input
            name="token"
            type="password"
            autoComplete="current-password"
            placeholder="API_TOKEN"
            required
            style={{ padding: 8 }}
          />
          <button type="submit" style={{ padding: 8 }}>
            进入
          </button>
        </form>
      </main>
    );
  }

  const rows = await list();
  return (
    <main style={box}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
        <h1 style={{ margin: 0 }}>验证码记录（最近 {rows.length} 条）</h1>
        <form action={logout}>
          <button type="submit">退出</button>
        </form>
      </div>
      {rows.length === 0 ? (
        <p>还没有记录。</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table border={1} cellPadding={6} style={{ borderCollapse: "collapse", width: "100%" }}>
            <thead>
              <tr>
                <th>时间</th>
                <th>设备</th>
                <th>号码/卡</th>
                <th>平台</th>
                <th>验证码</th>
                <th>原文</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${row.receivedAt}-${row.sender}-${index}`}>
                  <td>{row.receivedAt}</td>
                  <td>{row.device}</td>
                  <td>{row.phone ?? row.sim ?? "-"}</td>
                  <td>{row.platform ?? "-"}</td>
                  <td>
                    <b>{row.code ?? "-"}</b>
                  </td>
                  <td style={{ wordBreak: "break-all" }}>{row.content}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
