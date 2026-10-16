export default function Home() {
  return (
    <main style={{ fontFamily: "sans-serif", padding: 24, lineHeight: 1.6 }}>
      <h1>短信验证码转发</h1>
      <p>服务运行中。手机通过 POST /api/report 上报验证码。</p>
      <p>
        <a href="/history">查看记录</a>
      </p>
    </main>
  );
}
