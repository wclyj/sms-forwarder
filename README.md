# 短信验证码转发

手机收到验证码短信后，上报到你自己的服务器，提取验证码和平台，再推送到企业微信群机器人和邮箱。

```
手机短信 → Android / iOS 快捷指令 → POST /api/report → 企业微信 + 邮箱
```

```
server/    Next.js 服务端，可部署到 Vercel
android/   安卓 App（Kotlin）
```

只在自己的手机和自己的服务器上使用。

## 服务端

把 `server/.env.example` 复制为 `server/.env.local`，或在 Vercel 项目设置里填写。

| 变量 | 说明 |
|---|---|
| `API_TOKEN` | 至少 16 位的随机字符串。示例里的占位符不能直接使用 |
| `WECOM_WEBHOOK_URL` | 企业微信群 → 添加群机器人 → Webhook 地址。不填则跳过 |
| `SMTP_HOST` / `SMTP_PORT` | 如 QQ 邮箱 `smtp.qq.com` / `465`，163 为 `smtp.163.com` / `465` |
| `SMTP_USER` / `SMTP_PASS` | 发件邮箱和 SMTP 授权码（邮箱设置里开启 SMTP 后获取，不是登录密码） |
| `MAIL_TO` | 收件邮箱 |
| `UPSTASH_REDIS_REST_URL` / `UPSTASH_REDIS_REST_TOKEN` | 可选。配置后历史记录写入 Upstash Redis |

本地：

```bash
cd server
npm install
npm test
npm run dev
```

打开 http://localhost:3000 。

部署到 Vercel：导入本仓库，Root Directory 选 `server`，填好环境变量后部署。

### 接口

`POST /api/report`

请求头：`x-api-token: <API_TOKEN>`，也支持 `Authorization: Bearer <API_TOKEN>`。

```json
{
  "device": "小米13",
  "sim": "SIM1(中国移动)",
  "phone": "13800000000",
  "sender": "10690000",
  "content": "【淘宝】您的验证码是384912",
  "receivedAt": "2026-10-16T13:00:00+08:00"
}
```

```bash
curl -X POST https://你的域名/api/report \
  -H "content-type: application/json" \
  -H "x-api-token: $API_TOKEN" \
  -d "{\"device\":\"test\",\"sender\":\"10086\",\"content\":\"【淘宝】您的验证码是384912\",\"receivedAt\":\"2026-10-16T13:00:00+08:00\"}"
```

成功时返回 `{"ok":true,"code":"384912","platform":"淘宝","wecom":"ok","email":"ok"}`。未配置的渠道是 `"skipped"`。同一条短信（发送方、正文、时间都相同）再次上报会返回 `"duplicate":true`，不会重复推送。同一 IP 每分钟最多 60 次。

正文最长保留 4000 字。英文关键词按单词匹配，`encode`、`barcode` 里的 `code` 不会被当成验证码。

### 历史记录

打开 `https://你的域名/history`，输入 API_TOKEN。通过后口令的 SHA-256 摘要放在 HttpOnly Cookie 里，30 天内不用重输。也可以直接访问 `/history?token=<API_TOKEN>`；查询参数会留在浏览器历史里，共用电脑时用页面输入。

不配置 Redis 时，记录在进程内存里，最多 200 条。Vercel 冷启动或多实例后，内存里的记录会空。需要保留历史时填 Upstash 的两个环境变量。

## 安卓 App

需要 JDK 17 和 Android Studio。用 Android Studio 打开 `android/`，等 Gradle 同步（AGP 8.5 需要 Gradle 8.7 或更高），然后 Build → Build APK。

1. 安装后打开，允许接收短信、读取手机状态、读取本机号码和通知。
2. 填写服务器地址、至少 16 位的 API_TOKEN、设备名称，点「保存并启动」。
3. 点「发送测试」。返回测试成功后，服务器历史里会出现一条平台为「测试」、验证码为 `123456` 的记录。

行为：

- 只转发包含验证码、校验码、动态码、确认码、激活码、驗證碼、verification、code、OTP、PIN 的短信。长短信会合并后再上报。
- 双卡从短信广播里读取 subscription id；读不到时再按卡槽号查，格式为 `SIM1(运营商)`。能读到本机号码时一并上报。
- 短信接收器是静态注册的，系统会唤醒 App。保存后会显示一条前台通知。已经填过服务器地址时，开机自动启动。
- 网络失败和 5xx 最多重试 3 次。口令错误等 4xx 不重试。
- 目标是 Android 14（`targetSdk 34`）。这是自用侧载应用，前台服务类型是 `specialUse`，不保证能上架 Play。
- 小米、华为、OPPO、vivo 还需要手动允许自启动、电池设为无限制，并关闭「验证码保护 / 通知类短信拦截」。有的系统只把验证码短信交给系统短信 App。

公网地址请用 `https://`。保存 `http://` 地址时会提示验证码将明文传输，便于局域网调试。

## iPhone 快捷指令

iOS 不允许 App 读取短信，用「快捷指令」自动化：

1. 打开「快捷指令」→「自动化」→「+」→「信息」。
2. 「信息包含」填 `验证码`。需要的话再分别建 `校验码` 和 `code`。选择「立即运行」，关闭「运行前通知」。
3. 新建空白自动化，添加「获取 URL 内容」：
   - URL：`https://你的域名/api/report`
   - 方法：`POST`
   - 头部：`x-api-token` = API_TOKEN，`Content-Type` = `application/json`
   - 请求体选 JSON：
     - `device`：例如 `iPhone15`
     - `sender`：快捷指令输入 → 发件人
     - `content`：快捷指令输入 → 内容
     - `receivedAt`：当前日期
     - `sim`：这张卡的手机号，用来区分
4. 保存后，用另一台手机给自己发一条含「验证码 123456」的短信。

部分 iOS 版本仍会要求点一次通知才能跑自动化。双卡 iPhone 区分不了是哪张卡收到的。

## 安全

- `API_TOKEN` 只放在服务器环境变量和手机里。服务端会拒绝短于 16 位的值，也会拒绝示例占位符。
- 历史页 Cookie 里是口令摘要，不是原文。
- 安卓关闭了备份，降低 Token 进入云备份的可能。
- 上报请求关闭了自动跳转，避免口令被带到别的域名。

## 开发

```bash
cd server
npm test
npm run build
```
