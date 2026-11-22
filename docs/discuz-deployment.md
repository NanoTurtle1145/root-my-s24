# 论坛迁移到 Discuz! X3.5（2026-11-22）

原来 `rms24_api/` 里自研的讨论区（`forum.php` + `forum_db.php`）已换成 **Discuz! X3.5**，
App 的「论坛」入口保持不变，仍然走 `forum_gate.php` 换票据，只是票据现在换成 Discuz 单点登录。

## 部署位置

| 项 | 值 |
|---|---|
| 论坛地址 | `https://blog.nanoturtle.cn/forum/` |
| 后台 | `https://blog.nanoturtle.cn/forum/admin.php` |
| 代码 | Discuz X3.5（gitee `Discuz/DiscuzX` tag `20250205`） |
| 数据库 | 复用 `rootmys24` 库，表前缀 `cdb_`（与原有 `forum_*` / `run_logs` 共存） |
| PHP | 8.2.35（bt 面板 `/www/server/php/82`） |
| Web 服务器 | nginx + 现有 `ntblog.cn.conf`，无新增 vhost / 证书（`blog.nanoturtle.cn` 已在 SAN 里） |

## 自动安装方式（无浏览器）

Discuz 安装器是分步 + 最后一页用 JS 拉 AJAX 的，这里用脚本模拟：

1. `GET /forum/install/` → 同意协议 → 环境检查 → 「全新安装（含 UCenter Server）」
2. 填 `dbinfo[*]` + `admininfo[*]` 提交（表单字段见 `install/include/install_var.php`）
3. 从返回页里取出 `allinfo`（base64 序列化的安装参数），依次调用
   `index.php?method=do_db_init` → `do_db_data_init` → `do_db_ext_info`（`ext_info`）
4. 完成后把 `install/` 与 `uc_server/install/` 移出 web 根目录（备份在 `ntblog_cn/_dz_install_backup`）

## PHP 8.2 兼容修补

X3.5 里 `source/function/function_core.php` 的

```php
$havedomain = implode('', $_G['setting']['domain']['app']);
```

在 `domain.app` 是字符串时 PHP 8 会抛 `TypeError`（导致整站 503 / System Error，
首次访问时是 `forumstatus=0` 的提示页）。已改成 `is_array(...) ? implode(...) : (string)...`。

## 数据迁移

老讨论区数据量很小（1 用户 / 2 主题 / 4 帖），用一次性脚本导入：

- `forum_users` → `cdb_common_member`（用户名 `NanoTurtle`，uid=4），
  `cdb_rms24_map` 记录 `install_hash → uid`（`a4c0ffab5939`、`f3f66cdfe483` 都指向它）
- `forum_threads`/`forum_posts` → `cdb_forum_thread`/`cdb_forum_post`（板块 fid=2「综合讨论」；
  X3.5 的 `forum_post` **不是自增主键**，pid 要先插 `forum_post_tableid` 再取 `lastInsertId()`）
- 帖内 Markdown 按纯文本转义 + `nl2br()`（Discuz 不走 Markdown）

## App 单点登录（SSO）

```
App → POST rms24_api/forum_gate.php（口令）→ {url: /forum/sso.php?t=<票据>}
浏览器 → /forum/sso.php?t=…
         ├─ 验签（HMAC，payload={exp,i}，与旧方案同一套）
         ├─ install(`i`) → cdb_rms24_map → uid；没有就建 Discuz 会员 rms_<前6位>
         └─ Discuz setloginstatus() 写 auth cookie → 302 回 /forum/
```

`forum/sso.php` 里所有变量都用 `rms_` 前缀/常量：Discuz 的 include 会在同一作用域
创建 `$install`、`$username` 等变量，之前就踩过这个（会员名被写成了 `rms_`）。

旧的自研论坛代码保留在 `/rms24_api/forum.php`（未删），作为回滚/存档。

## 运维备注

- 管理员初始账号 `admin`，密码在部署时设置，**上线后应立刻在后台改掉**
- 论坛设置（板块名、时区 +8、`forumstatus=1`、`regstatus=1`）已写入 `cdb_common_setting`
- Discuz 连接 MySQL 时会 `SET sql_mode=''`；用 PDO 手工改数据时也要先 `SET SESSION sql_mode=''`，
  否则 `common_member_profile.bio` 这类无默认值列会报 1364

## CSS 不加载（`style__common.css` 404）

装完后首页能开但样式全丢，HTML 里是 `data/cache/style__common.css`（STYLEID 为空）。
根因：`data/sysdata/` 里的 Discuz 缓存没建全（安装器最后一步的 AJAX 链我们只跑到 `ext_info`），
`loadcache('style_default')` 取不到东西 → `define('STYLEID', '')` → CSS 文件名缺 `1`。

修法（在论坛根目录跑一次全量缓存）：

```php
define('IN_DISCUZ', true);
define('DISCUZ_ROOT', '/www/wwwroot/ntblog_cn/forum/');
$_SERVER['HTTP_HOST']='blog.nanoturtle.cn';
$_SERVER['REQUEST_URI']='/forum/'; $_SERVER['SCRIPT_NAME']='/forum/index.php';
require DISCUZ_ROOT.'source/class/class_core.php';
$discuz = C::app(); $discuz->init();
require_once libfile('function/cache');
updatecache();          // 空参 = 全量，别只传 'setting'
```

之后 HTML 变成 `data/cache/style_1_common.css`，`data/cache/style_1_*.css` 由模板实时生成并 200。

> 注意缓存目录是 `data/sysdata/`（不是 `data/cache/`），清缓存别清错地方。
