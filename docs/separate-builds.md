# 分系列打包（separate 版本）

单包约 48 MB，其中 **58.7 MB 是 KSU 驱动**、5.7 MB 是 cve 载荷——而任一台设备只用得到其中
**一个驱动 + 若干载荷**。本方案按机型系列把同一条代码编译成多个 APK，一个 release 里放多个
安装包，用户按自己的系列下载。

## 1. 产物

| flavor | 覆盖系列 | 体积 | 版本名 |
|---|---|---|---|
| `all` | 全部（= 原完整包） | **48.3 MB** | `3.5.2-beta.221` |
| `s24` | S24 系列（S9210/S9260/S9280/S928B） | **30.7 MB** | `3.5.2-beta.221-s24` |
| `s25` | S25 系列（S9310/S9360/S9370/S9380） | **7.6 MB** | `3.5.2-beta.221-s25` |
| `s23` | S23 系列（S9110/S9160/S9180） | **7.8 MB** | `3.5.2-beta.221-s23` |
| `s26` | S26 系列（S9420/S9470/S9480） | **5.8 MB** | `3.5.2-beta.221-s26` |
| `fold` | 折叠屏（Z Flip / Z Fold / F 系列） | **17.3 MB** | `3.5.2-beta.221-fold` |
| `w` | 心系天下 W 系列 | **12.9 MB** | `3.5.2-beta.221-w` |

**`versionCode` 与 `applicationId` 全系列一致**，签名相同 → 不同系列的包可以互相覆盖安装
（例如误装了 `all` 再装 `s25` 会直接升级，不需要卸载）。

`s24` 最大是因为它要带 4 个 3.3.0 专属驱动（`ksud-dzf2/dzh3/czb2-32601`，各 5.1 MB）
与 BakaSU 资产（`bakasu-manager.apk` 9.4 MB + `ksud-bakasu-dzh3` + bakasu root helper）。

## 2. 资产怎么分

`tools/split_assets.py` 从 `RootViewModel.FirmwareVersion` 的条目反推每个系列需要哪些资产，
再分发到 `app/src/<flavor>/assets/`（硬链接，不占额外磁盘；脚本幂等，可重复跑）：

```
app/src/main/assets/        全系列共用：cve-2026-43499-root、许可证、旧默认载荷
app/src/<flavor>/assets/    该系列专属：cve 载荷 + ksud 驱动
app/src/all/assets/         全部受管资产（74 个）
```

特殊规则（写在脚本的 `EXTRA` 里）：

- `ksud-czb2-32601` / `ksud-dzf2-32601` / `ksud-dzh3-32601`：服务端 `payloads.json` 的
  `ksu` 选项会按构建号点名它们，而 `KsuOption.asset` 是「APK assets 里的资产名」，
  所以**必须内置** → 只有 `s24`（及其全量包）带。
- `bakasu-manager.apk` / `ksud-bakasu-dzh3` / `cve-2026-43499-root-bakasu`：BakaSU 模式只支持
  SM-S9280 / DZH3 → 只有 `s24` 带。
- `dfr_lkm-android12-5.10.ko`：DirtyFrag 的 LKM 只随 5.10 内核提供 → 只有 `w` 带。

**新增/更新载荷后必须重跑脚本**：

```sh
python3 tools/split_assets.py --check   # 先看分发计划
python3 tools/split_assets.py           # 执行（幂等）
```

## 3. 运行时行为

- `RootViewModel.packagedSeries`：由 `BuildConfig.SERIES` 得出；`all` 为 `null`（不限系列）。
- `RootViewModel.availableFirmwareVersions`：分系列包只返回本系列条目，固件选择页用它渲染。
- 分系列包**隐藏**机型系列筛选芯片（只有一个系列，没必要）；
  若当前设备机型与包内系列不符，页面顶部给出明确提示（`firmware_wrong_series_pack`），
  避免用户误以为"不支持"。
- 全量包 `all` 行为与拆分前完全一致（无版本后缀、无过滤）。

## 4. 构建与发布

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# 单个
./gradlew :app:assembleS25Release -Prms24Channel=beta
# 全部（产物在 app/build/outputs/apk/<flavor>/release/app-<flavor>-release.apk）
./gradlew :app:assembleAllRelease :app:assembleS24Release :app:assembleS25Release \
          :app:assembleS23Release :app:assembleS26Release :app:assembleFoldRelease \
          :app:assembleWRelease -Prms24Channel=beta
```

发布时把它们作为**同一个 GitHub release 的多个 asset** 上传，命名建议：

```
RootMyS24-v<版本>-build<code>-all.apk
RootMyS24-v<版本>-build<code>-s24.apk
…
```

## 5. 更新通道（已按系列打通）

- **客户端**：`UpdateChecker` 请求 `update.php?series=<BuildConfig.SERIES 小写>`，
  响应里的 `series` 字段可看出服务端实际给了哪个（回退到 `all` 时也是 `all`）。
- **服务端** `update.php`：清单支持 `channels` 映射
  ```json
  { "versionName": "...", "versionCode": 223,
    "channels": { "all": {"file": "...-all.apk", "sha256": "..."},
                  "s24": {"file": "...-s24.apk", "sha256": "..."} } }
  ```
  请求的系列不存在 → 回退 `all`；清单仍是旧格式（顶层 `file`）→ 任何系列都返回它（向后兼容）；
  `series` 参数先做 `^[a-z0-9]{1,12}$` 清洗；`size` 由服务端实时算，不信清单。
  （已在隔离环境实测：命中 / 回退 / 非法参数三条路径都正确。）
- **发布脚本** `tools/release_flavors.py`：一次构建多个 flavor → 校验版本号与签名 →
  上传到同一个 GitHub release 的多个 asset → 上传 APK 与 `latest.json` 到服务器 `dl/`。
  ```sh
  python3 tools/release_flavors.py --channel beta --dry-run   # 只构建+校验+生成清单
  python3 tools/release_flavors.py --channel beta             # 正式发布（GitHub + 服务器）
  python3 tools/release_flavors.py --channel beta --skip-build  # 复用已有产物
  ```

## 6. 还没做的

1. **CI 校验**：加一步 `split_assets.py --check`，防止新增载荷后忘了重新分发。
2. **服务器 dl/ 体积**：每个版本会多 7 个 APK（合计约 80MB），需要定期清理旧版本。
