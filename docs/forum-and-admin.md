# 讨论区与服务端管理台

> 更新日期：2026-09-28
> 对应文件：`server/forum_gate.php`、`server/forum.php`、`server/forum_db.php`、`announce.php`，以及服务器上的 `admin.php`（不入库）

这份文档说明 RootMyS24 讨论区的**准入设计、数据结构、审核方式**，以及管理台的构成。
原则一句话：**APK 里不能有任何秘密**——开源项目的"口令"如果进了客户端，反编译即公开。

---

## 1. 准入设计：口令 → 短期票据 → 会话

```
App（关于 → 讨论区）          服务端                       浏览器/WebView
  │ 输入口令                   │                              │
  ├── POST forum_gate.php ────►│ 比对 HMAC(口令) 与存储值      │
  │                            │ 限速：同 IP+安装标识 10 分钟 20 次
  │◄── {ok, url: forum.php?t=} ─┤ 票据 = b64(payload).HMAC    │
  │                            │                              │
  ├───── 打开该 URL（WebView）─────────────────────────────────►│
                                                              │ 自己验签（同一把 hmac_key）
                                                              │ 下发会话 cookie rms24f（12h 滑动）
```

| 要素 | 说明 |
|---|---|
| 口令存储 | 服务端只存 `hash_hmac('sha256', 口令, hmac_key)`，不存明文；改口令不用发版 |
| 票据 | `base64url({"exp":…,"i":"<install 哈希前12位>"}).HMAC`，默认 30 分钟 |
| 会话 cookie | `rms24f`，结构与票据相同、12 小时滑动续期——避免 WebView 里票据到期就被踢出去 |
| 限速 | 口令校验按 IP+安装标识限速；发帖另按标识与 IP 双维度限速 |
| 身份 | 匿名代号 = 票据里的 `i`（不可反查），发帖可另填昵称 |
| 客户端离线校验 | **不做**——那等于把可爆破的校验物塞进 APK |

部署 `forum_secret.php`（服务器上执行一次，口令换成自己的）：

```bash
php -r '$t="你的口令"; $k=bin2hex(random_bytes(32));
  file_put_contents("forum_secret.php", "<?php\nreturn [\n  \"token_hash\" => \"".hash_hmac("sha256",$t,$k)."\",\n  \"hmac_key\" => \"$k\",\n  \"ticket_ttl\" => 1800,\n  \"session_ttl\" => 43200,\n];\n");
  echo "ok\n";'
```

数据库凭据单独放 `db_secret.php`（`forum_db.php` 优先读它，其次读 `forum_secret.php['db']`）：

```php
<?php
return [
  'dsn'  => 'mysql:host=127.0.0.1;port=3306;dbname=rootmys24;charset=utf8mb4',
  'user' => '…',
  'pass' => '…',
];
```

两个文件都 **chmod 600**，且都不进仓库。

---

## 2. 数据结构（首次访问自动建表）

| 表 | 用途 | 关键字段 |
|---|---|---|
| `forum_users` | 账号 | `username` 唯一、`pass_hash`（password_hash）、`role`、`banned`、`bio`、`token_version`、计数 |
| `forum_threads` | 主题 | `topic` 分类、`user_id`（可空=匿名）、`pinned/locked/hidden` 审核位、`views`、`likes`、`posts` |
| `forum_posts` | 楼层 | `thread_id`、`user_id`、`body`（纯文本）、`likes`、`hidden`、`ip_hash`（仅风控） |
| `forum_likes` | 点赞 | `(post_id, user_id)` 主键，唯一约束天然防重复点赞 |
| `forum_bans` | 匿名标识封禁 | `install_hash` 唯一键 + `reason` |
| `forum_rate` | 限速计数 | `bucket`、`rkey`、`created_at` |

建表由 `rms24_schema()` 幂等完成：**部署 = 上传文件**，不需要手工执行 SQL。

---

## 3. 用户系统

两层身份并存：**准入层**（口令票据，决定"能不能进"）与**账号层**（用户名密码，决定"以谁发言"）。

| 能力 | 说明 | 实现要点 |
|---|---|---|
| 注册 | 需要口令票据；用户名 2–16 字（中文/字母/数字/`_`/`-`，非纯数字，保留字拒绝） | `password_hash(PASSWORD_DEFAULT)`，密码 6–72 位 |
| 登录 / 退出 | 登录态 = HMAC 签名的 `rms24u` cookie（30 天） | payload `{uid, tv, exp}`，`tv` = `token_version` |
| 强制下线 | 管理员"强制下线"或用户改密码 → `token_version + 1` | 旧 cookie 立即失效，无需服务端 session 表 |
| 封禁 | 管理员封禁后**登录直接被拒**（附理由）；未登录仍可浏览 | 另一种封禁是按匿名标识封（`forum_bans`） |
| 昵称与头像 | 显示用户名；头像由用户名派生色相 + 首字（不依赖外部资源、无需上传） | `forum_avatar()` |
| 个人主页 | `?v=u&id=N` 公开资料：简介、加入时间、主题/发言/获赞计数、最近主题与回复 | `?v=me` 是本人的设置页 |
| 点赞 | 每个账号对每楼最多一个赞，可取消 | `forum_likes` 唯一键 + 计数冗余字段 |
| 自我管理 | 可删除自己的主题/回复（软隐藏，不破坏楼层连续性与审核记录） | 需要登录且为作者本人 |
| 匿名发言 | 未登录也能发帖，署名"匿名 xxxxxxxx"（install 哈希前 8 位，不可反查） | 兼容旧数据：`user_id` 为空即为匿名 |

**第一个注册的账号自动成为管理员**（方便单人站长建站后立刻使用），之后注册的都是普通用户。

## 3. 安全与防滥用

- **XSS**：渲染是"先整体转义、再套用极小 Markdown 子集"（围栏代码块、`行内代码`、**粗体**、
  *斜体*、> 引用、链接、换行），任何情况下都不允许原始 HTML 通过；标题/昵称先 `strip_tags`、再限长
  （标题 4–80、正文 4–8000）。
- **CSP**：`script-src 'nonce-…'`（每次请求随机），因此页面里**没有内联事件属性**——删除确认、
  下拉跳转等都由带 nonce 的脚本接管。
- **CSRF**：隐藏字段 `csrf = HMAC('csrf|' + 安装代号)`，校验后才落库（封禁判断在它之前，
  以免被封禁者拿到误导性的 `badcsrf`）。
- **重复提交**：所有写操作 POST → 302 重定向（PRG），刷新不会重复发帖。
- **限速**：同标识 20 秒内不能连发主题、15 秒内不能连发回复；主题 6 条/小时、
  回复 40 条/小时，并有按 IP 的上限（`forum_rate` 表跨进程生效）。
- **响应头**：`X-Frame-Options: DENY`、`Content-Security-Policy: default-src 'none'`、
  `Referrer-Policy: no-referrer`、`X-Content-Type-Options: nosniff`、`Cache-Control: no-store`。

---

## 4. 管理台（`admin.php`，服务器端专用）

- **设计系统**：一套 CSS 令牌（暗色为默认、随系统切亮色），吸顶顶栏 + 吸顶分区导航
  （滚动到哪段点亮哪个 chip），卡片/表格/折叠区/胶囊标签统一风格，窄屏自适应。
- **既有功能全部保留**：运行日志列表与筛选、日志详情、按固件/机型统计、访问来源地图
  （天地图/OSM 切换）、国家地区分布、公告管理（直接改 `announcements.json`，App 免发版）、
  问题反馈、异常行为审计。
- **讨论区管理（新增 `#forum` 段）**：
  - 概览：主题数 / 发言数 / 今日新增 / 已隐藏 / 封禁数
  - 筛选：按标题或安装标识搜索，按 全部/正常/已隐藏/已锁定/已置顶 过滤，分页
  - 主题操作：置顶、锁定、隐藏、删除（连带回复）
  - 逐楼审核：进入某主题看全部楼层（含已隐藏），可隐藏/恢复/删除单楼，或直接封禁该标识
  - 封禁名单：查看与解除；手动封禁会同时写一条 `abuse_events`
  - **用户管理（`#users` 折叠区）**：账号列表（搜索用户名、按 全部/封禁/管理员 过滤）、
    封禁与解封（附理由）、设为管理员/降为普通用户、**强制下线**（递增 `token_version`）、
    删除账号（其主题与回复保留为匿名，讨论记录不丢）
- 审核动作全部 POST + PRG，操作结果以提示条呈现在 `#forum` 顶部。

---

## 5. 自部署清单

```
rms24_api/
├── forum_gate.php      # 口令闸门（接口）
├── forum.php           # 讨论区页面
├── forum_db.php        # 数据层与建表
├── forum_secret.php    # 口令哈希 + hmac_key（600，勿入库）
├── db_secret.php       # 数据库凭据（600，勿入库）
├── announce.php        # 公告接口
├── announcements.json  # 公告内容（后台可改）
└── admin.php           # 管理台（本仓库不含：内含数据库凭据与管理令牌）
```

App 侧无需任何改动：`Forum.kt` 只调用 `forum_gate.php` 并打开返回的 URL，
换论坛引擎/版式都不影响客户端。
