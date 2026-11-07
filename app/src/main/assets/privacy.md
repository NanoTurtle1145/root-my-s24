# RootMyS24 隐私政策

更新：2026-11-07 · 作者：NanoTurtle · 适用：`cn.nanoturtle.rootmys9280`

---

## 0x00 先说清楚

RootMyS24 是个临时 root 工具，不是来收你数据的。

我自己也是个学生，没那么多心思搞用户画像。写这份东西，主要是因为 App 会把运行日志传到我自己搭的服务器上，而且服务器会记到 IP，那就把这个事讲明白。

---

## 0x01 我会拿到什么

只有在**你允许的前提下**，App 才会把这些发出去：

- **运行日志**：一次 root 的完整过程，包括固件、内核、用的 payload、每一步的状态和报错
- **设备信息**：机型、设备代号、Android 版本、SDK、App 版本、包名、APK 签名证书哈希
- **安装标识**：首次启动时随机生成的 UUID，存在本机。不是 IMEI、不是序列号、不是 Android ID，也跨不到别的 App
- **网络元数据**：IP 地址、User-Agent、请求时间。这个是服务器自动记的

**不会碰的**：通讯录、短信、通话记录、照片文件、位置、IMEI、序列号、广告 ID、账号。App 里没有任何广告或统计 SDK。

---

## 0x02 日志怎么共享

首次启动会问你一次，之后在 **设置 → 隐私 → 日志共享** 里随时改：

- **始终**：每次跑完自动传
- **手动**（默认）：每次跑完问你一下
- **从不**：只留在本机，不联网

默认是「手动」，不会偷偷传。

---

## 0x03 拿这些干什么

1. 看你 root 失败在哪一步、哪个机型、哪个内核
2. 适配新机型和固件
3. 算成功率，看看 payload 和流程要不要改
4. 服务器限流，防止被刷

就这些，没了。不卖、不打广告、不做画像。

---

## 0x04 不会干的事

- 不会用这些数据做广告
- 不会卖给第三方
- 不会把日志原文、IP、城市、安装标识放到公开页面上

公开的统计图只放聚合数据：次数、机型分布、成功率这些。

---

## 0x05 IP 地址

服务器会记录请求的 IP，这是任何网站都会做的事。用途只有两个：限流（防止有人刷接口）和地区聚合统计（比如「哪个国家的用户多」）。

IP 不会公开，也不会拿去做别的事。

---

## 0x06 保存和删除

日志存在我自己搭的服务器（`blog.nanoturtle.cn`），**目前没有自动清理机制**，会一直留着用于排查和统计。

如果你想让自己的日志被删掉：

- 打开 设置 → 隐私，那里能看到你的安装标识
- 到 [GitHub Issues](https://github.com/NanoTurtle1145/root-my-s24/issues) 找我，把安装标识发我
- 我会手动删掉对应记录

清除应用数据会重置安装标识，之前的日志就关联不到你的手机了。

---

## 0x07 权限是干嘛的

| 权限 | 干嘛用 |
|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | 检查更新、在你允许时传日志 |
| `POST_NOTIFICATIONS` | 显示 root 流程的进度 |
| `WAKE_LOCK` | 跑的时候保持唤醒，别中途睡着 |
| `REQUEST_INSTALL_PACKAGES` | 装 KernelSU 管理器、装更新 |
| `QUERY_ALL_PACKAGES` | 查有没有装 KernelSU、Shizuku 这些组件 |
| `FOREGROUND_SERVICE` | root 流程跑在前台服务里 |

---

## 0x08 第三方

- **GitHub**：检查更新、下载 Release，受 GitHub 自己的隐私政策管
- **Shizuku**：本地授权执行 shell，不上传数据
- 除了上面这些和法律要求，不会给任何第三方

---

## 0x09 你的权利

- 关掉日志上传、清本地日志
- 清应用数据重置安装标识
- 找我删服务器上的日志

---

## 0x0A 变更和联系方式

政策改了会发在 GitHub 和博客，App 里也会提示。继续用就是接受新版。

- GitHub：<https://github.com/NanoTurtle1145/root-my-s24/issues>
- 博客：<https://blog.nanoturtle.cn>

---

## English

### 0x00 First of all

RootMyS24 is a temporary root tool. It is not here to collect your data.

I am a student, not a company. I wrote this because the app uploads run logs to my own server, and the server sees your IP. So I should explain it clearly.

### 0x01 What I get

Only **if you allow it**:

- **Run log**: the whole process of one root attempt — firmware, kernel, payload, each step, errors
- **Device info**: model, device codename, Android version, SDK, app version, package name, APK signing certificate hash
- **Install ID**: a random UUID made on first launch and stored on your phone. Not IMEI, not serial number, not Android ID. It cannot follow you to other apps
- **Network metadata**: IP address, User-Agent, request time. The server records these automatically

**What I never touch**: contacts, SMS, call logs, photos/files, location, IMEI, serial number, advertising ID, accounts. There is no ad or analytics SDK in the app.

### 0x02 Log sharing

You are asked once on first launch. Change it anytime in **Settings → Privacy → Log sharing**:

- **Always**: upload after every run
- **Manual** (default): ask you after every run
- **Never**: keep it on your phone, no network

The default is manual. Nothing is uploaded silently.

### 0x03 What I use it for

1. See where your root failed, on which device and kernel
2. Support new devices and firmware
3. Measure success rate and improve payloads
4. Rate limiting on the server, to stop abuse

That is all. No selling, no ads, no profiling.

### 0x04 What I never do

- No ads with this data
- No selling to third parties
- No raw logs, IPs, cities or install IDs on public pages

Public charts show aggregate numbers only: counts, device distribution, success rate.

### 0x05 IP address

The server records the request IP, like any website does. It is used for two things only: rate limiting and aggregate region stats (for example, how many users per country).

The IP is not public and not used for anything else.

### 0x06 Storage and deletion

Logs live on my own server (`blog.nanoturtle.cn`). **There is no automatic cleanup right now** — they stay for diagnostics and statistics.

If you want your logs deleted:

- Open Settings → Privacy to see your install ID
- Open a [GitHub Issue](https://github.com/NanoTurtle1145/root-my-s24/issues) and send me the ID
- I will delete the matching records by hand

Clearing app data resets the install ID, so old logs can no longer be linked to your phone.

### 0x07 Permissions

`INTERNET` / `ACCESS_NETWORK_STATE` (update checks, log upload when allowed), `POST_NOTIFICATIONS` (progress), `WAKE_LOCK` (stay awake during a run), `REQUEST_INSTALL_PACKAGES` (install KernelSU manager and updates), `QUERY_ALL_PACKAGES` (detect installed KernelSU/Shizuku components), `FOREGROUND_SERVICE` (run the root flow).

### 0x08 Third parties

- **GitHub**: update checks and Release downloads, under GitHub's own privacy policy
- **Shizuku**: local authorization for shell commands, nothing uploaded
- Apart from these and legal requirements, nothing goes anywhere

### 0x09 Your rights

- Turn off log upload and clear local logs
- Clear app data to reset the install ID
- Ask me to delete server-side logs

### 0x0A Changes and contact

Updates go to GitHub and the blog, with an in-app notice. Continued use means you accept the new version.

- GitHub: <https://github.com/NanoTurtle1145/root-my-s24/issues>
- Blog: <https://blog.nanoturtle.cn>

---

NanoTurtle,2026/11/07
