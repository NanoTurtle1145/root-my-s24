# v3.4.0 发布记录（2026-09-27）

- **版本**：`versionName=3.4.0` / `versionCode=154`（`versionCode` = `origin/main` 提交数；`versionName` = tag）
- **渠道**：`stable`（`-Prms24Channel=stable`，不带 `-beta` 后缀）
- **产物**：`RootMyS24-v3.4.0-build154.apk`（24,929,332 字节）
- **sha256**：`8ae77d7b1af808b0b89b536ae73de03ae345c43d98d5f2206c253c15fc806742`
- **备份标签**：`v3.4.0`

## 新功能

- **公告（announcer）**：主页常驻显示；点卡片进详情页看全文；右上角叉叉单条关闭；
  详情页「全部恢复」一键找回。内容由服务端 `announcements.json` 下发，
  改文案/上下线**不需要发版**。已关闭名单存在本机，"全关掉也不会丢掉入口"。
- **讨论区入口**（关于 → 社区）：网页版讨论区。口令由**服务端**校验（只存 HMAC、
  按 IP+安装标识限速、下发短期签名票据）；客户端不放任何秘密，口令可在服务端随时轮换。
  服务端：`forum_gate.php`（换票据）+ `forum.php`（自验签，当前为壳，可换论坛引擎）。

## 修复

| 问题 | 修复 |
|---|---|
| 更新下载点一次没反应、再点也没反应 | 目标文件名唯一化（固定名会撞 `ERROR_FILE_ALREADY_EXISTS`）；入队失败如实报错 |
| 下载完成后没有安装弹窗 | 完成广播原本绑在设置页生命周期上；改为下载 id/sha256 落盘 + 查真实状态收尾 |
| 下载未校验 | 装前校验清单里的 `sha256`，不匹配拒绝安装 |
| 检查更新必失败 | `rememberCoroutineScope` 是主线程调度器 + 阻塞式 OkHttp → `NetworkOnMainThreadException`，改为 `withContext(IO)` |
| 成功 root 一次后永久卡在 `[2/5]` | `/data/local/tmp` 里的载荷变 root 属主、shell 覆写/删除都被拒；改为每 run 独立暂存目录 + 顺手自愈 |
| 运行期把 Shizuku 搞掉 | 运行期持有 partial wakelock（显示照样关，但不进 Doze）+ 竞态阶段结束立刻唤屏；Shizuku 掉了时自动改用已连接的无线调试通道继续 |
| 主页日志堆积 | 日志区移出主页；运行开始时自动跳转日志页 |

## 服务端状态

- `update.php` → `3.4.0 / 154`，`size`/`sha256` 与产物一致（客户端会校验）
- `announce.php` → 返回公告列表（后台 `admin.php → 公告管理` 可增删改、排序、设生效期）
- `forum_gate.php` / `forum.php` → 已部署；`forum_secret.php` 需自行生成口令哈希后生效

## 验证记录

- 构建：`./gradlew :app:assembleRelease -Prms24Channel=stable` → `versionCode=154, versionName=3.4.0`
- 服务器 APK：`content-length: 24929332`，`sha256` 与本端逐字符一致
- 真机：安装后 `versionName=3.4.0 / versionCode=154`，应用正常启动；主页公告常驻显示、
  点卡片进详情、叉叉可关闭、详情页可全部恢复（`fetched=4 dismissed=4` 的空白问题已复现并修复）
- 后台：`php -l admin.php` 无语法错误；带鉴权渲染 HTTP 200 无 PHP 报错；未登录仍 401；
  公告写入路径用 no-op 往返实测通过（302 + 文件被重写 + 接口仍正常）
