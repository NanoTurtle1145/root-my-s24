# 分系列打包（separate 版本）

单包约 48 MB，其中 **58.7 MB 是 KSU 驱动**、5.7 MB 是 cve 载荷——而任一台设备只用得到其中
**一个驱动 + 若干载荷**。本方案按机型系列 + **实测状态**把同一条代码编译成多个 APK，
一个 release 里放多个安装包。

## 1. 发布三个版本

| flavor | 内容 | 体积 | 版本名 |
|---|---|---|---|
| `all` | 全部载荷与驱动（= 原完整包，向后兼容） | 48.3 MB | `3.5.2-beta.224` |
| `s24` | S24 系列**已实测**载荷 + 对应驱动 | **8.6 MB** | `3.5.2-beta.224-s24` |
| `fold` | 折叠屏**已实测**载荷 + 对应驱动 | **5.9 MB** | `3.5.2-beta.224-fold` |
| `exp` | 全量载荷 + **默认打开「未经测试的载荷」开关**（测试者专用） | 48.3 MB | `3.5.2-beta.224-exp` |

- `versionCode` / `applicationId` 全系列一致、签名相同 → **可互相覆盖安装**
  （`all` ↔ `s24` ↔ `exp` 直接升级，不用卸载）。
- **`s25` / `s23` / `s26` / `w` 暂不发布**：这些系列目前**没有任何已实测载荷**，
  包内会没有载荷。等有实测条目（服务端 `payloads.json` 标 `tested=true`）后自动出现。

> 服务器端三个通道都保留（`all` / 各系列 / `exp`），便于出问题时切换/回滚。

## 2. 「已实测」怎么定

按 App 运行时同一套语义（`isTested()`）在**构建时快照**：

- 服务端 `payloads.json` 里 `tested = true` 优先；
- 清单里没有这条 → 回落 `RootViewModel.FirmwareVersion` 的代码默认值。

快照写在 `app/tested_payloads.json`（提交进仓库）。刷新：

```sh
python3 tools/split_assets.py --refresh-tested   # 从服务端拉最新状态并重新分发
```

> 目前快照结果：61 条里 **4 条已实测**（`DZF2` / `S9210DZI1` / `S928BEZIF` / `F741NDZE1`），
> 所以只有 `s24` 与 `fold` 有官方系列包。

## 3. 资产怎么分

`tools/split_assets.py` 从 `RootViewModel.FirmwareVersion` 的条目反推每个 flavor 需要哪些资产，
分发到 `app/src/<flavor>/assets/`（硬链接，不占额外磁盘；脚本幂等）：

```
app/src/main/assets/        全系列共用：root helper、许可证、旧默认载荷
app/src/<flavor>/assets/    该 flavor 专属的载荷 + 驱动
app/src/exp/assets/         与 all 相同（全量），区别只在默认开关与版本后缀
```

特殊规则（脚本里的 `EXTRA_ALL`）：`ksud-czb2/dzf2/dzh3-32601`（服务端 `ksu` 选项点名的
专属驱动）、BakaSU 三件套、DirtyFrag 的 5.10 LKM —— 只随 `all` 与 `exp`，
因为 `KsuOption.asset` 是「APK assets 里的资产名」，必须内置。

## 4. 运行时行为

- **`PACKAGED_ENTRIES`**：`split_assets.py` 生成 `app/series_entries.json`，Gradle 按 flavor
  注入逗号分隔的条目名；App 用它过滤固件列表 —— **列表里出现的条目，包里一定有对应载荷**。
- `exp` flavor：`BuildConfig.SERIES == "EXP"`，`untestedPayloadsDefault = true`，
  所以「未经测试的载荷」开关默认打开，测试者不用手动去设置里开。
- 分系列包装错系列时，固件选择页顶部给出明确提示（`firmware_wrong_series_pack`）。

## 5. 构建与发布

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# 发布集合（产物在 app/build/outputs/apk/<flavor>/release/）
./gradlew :app:assembleAllRelease :app:assembleS24Release \
          :app:assembleFoldRelease :app:assembleExpRelease -Prms24Channel=beta

# 一键发布：构建 → 校验版本/后缀/签名 → 同一个 GitHub release 多资产
#           → 上传 APK 与 latest.json 到服务器 dl/
python3 tools/release_flavors.py --channel beta --dry-run   # 只构建+校验+生成清单
python3 tools/release_flavors.py --channel beta             # 正式发布
```

产物命名：

```
RootMyS24-v<版本>-build<code>.apk          （all，保持旧命名）
RootMyS24-v<版本>-build<code>-s24.apk
RootMyS24-v<版本>-build<code>-fold.apk
RootMyS24-v<版本>-build<code>-exp.apk
```

## 6. 更新通道

- 客户端：`UpdateChecker` 请求 `update.php?series=<BuildConfig.SERIES 小写>`。
- 服务端 `update.php`：清单支持 `channels` 映射；请求的系列不存在 → 回退 `all`；
  旧格式清单 → 任何系列都返回它（向后兼容）；`series` 参数先做 `^[a-z0-9]{1,12}$` 清洗；
  `size` 由服务端实时算。
- `latest.json` 形如：

```json
{ "versionName": "...", "versionCode": 224,
  "channels": {
    "all":  { "file": "...-build224.apk",      "sha256": "...", "mirror": "..." },
    "s24":  { "file": "...-build224-s24.apk",  "sha256": "...", "mirror": "..." },
    "fold": { "file": "...-build224-fold.apk", "sha256": "...", "mirror": "..." },
    "exp":  { "file": "...-build224-exp.apk",  "sha256": "...", "mirror": "..." }
  } }
```

## 7. 待办

1. **CI 校验**：加一步 `split_assets.py --check`，防止新增载荷后忘了重新分发/刷新实测快照。
2. **服务器 `dl/` 体积**：每个版本多 4 个 APK（约 110 MB），需要定期清理旧版本。
3. `s25/s23/s26/w` 有实测条目后，把对应 flavor 加进 `RELEASE_FLAVORS`。
