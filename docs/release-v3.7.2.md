# RootMyS24 v3.7.2

两块内容：**DZJ3 提升为已实测并进入 s24 官方包**，以及 **DZJ3 扩展到 SM-S9210（e1q）**。

## 变更一：DZJ3 提升为已实测

- `FirmwareVersion.S9280DZJ3`：`tested = false → true`
- 重新生成条目清单：s24 包 10 → **11 条**（新增 `S9280DZJ3`）
- all / exp 仍为 70 条（内容不变）
- 载荷本身无改动：
  - `app/src/main/assets/samtimer/targets/S9280ZCS6DZJ3/libtimer`
  - 定标说明见 `docs/port-98260-S9280ZCS6DZJ3.md`

## 使用

s24 包安装后即可在机型页直接看到 **SM-S9280 国行 DZJ3（One UI 8.5）**，
无需开启「未经测试的载荷」：引擎 98260 UAF、KSU 驱动 3.2.5。

> 注：DZJ3 走的是 per-target 98260 载荷；同一开机的第一次尝试失败需要真重启再试。

## 变更二：DZJ3 扩展到 SM-S9210（e1q）

社区仓库 [mrlihx/galaxy-s24-e1q-dzj3-temp-root](https://github.com/mrlihx/galaxy-s24-e1q-dzj3-temp-root)
在 SM-S9210 真机验证了同一套 DZJ3 载荷，并给出关键事实：

- `S9280ZCS6DZJ3`（e3q）与 `S9210ZCS6DZJ3`（e1q）**共用同一份内核镜像**
  （`6.1.145-android14-11-3254743`，changelist 与构建时间相同），59 个 offsets 常量无需修改；
- 上游 `build.sh` 原用 `-shared` 链接，产物 ET_DYN、入口为 0，直接 exec 会 SIGSEGV，
  必须 `-pie -fPIE`（本仓库的 DZJ3 载荷是静态 EXEC，无此问题）。

因此：

- 新增 `app/src/main/assets/samtimer/targets/S9210ZCS6DZJ3/libtimer`
  （与 S9280ZCS6DZJ3 同一份二进制）
- 机型条目 `S9280DZJ3` 描述改为「S24 全系 · 国行（S9280 / S9210）DZJ3」，
  按设备构建号自动选 target 目录
- SM-S9210（S24 国行）DZJ3 固件现在会被自动检测到并直接可用

> e3q 已有 RootMyS24 实测记录（PR #44）；e1q 由 mrlihx 真机验证（同一内核、同一 offsets），
> RootMyS24 尚未在 e1q 真机上跑过自己的 APK，失败请反馈日志编号。
