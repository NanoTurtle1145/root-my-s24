# DirtyFrag 引擎接入方案（alpha 线）

> 状态：**已真机证伪 —— S9280 全系不可用（2026-09-27）**。
> 结论与完整证据链见
> `~/项目/samsung_root_research/02_exploit工程/dirtyfrag-s9280/FEASIBILITY_VERDICT.md`。
> 目标渠道 `alphaN`（引擎试验），与 `betaN`（机型适配）区分。
> 关联参考：`~/项目/samsung_root_research/03_参考研究/dirtyfrag/`（**只读参考**）

## 0x00-1 实测结论（先看这个）

**DirtyFrag 的 xfrm-ESP 页缓存写原语在 S9280 上结构性不可达，不是调参问题。**

- 判据一（ESP 侧全对）：内核 trailer 规则 `padlen + 2 + alen >= elen` 推出的
  "`desired[14] >= 14` 的块必报 ProtoError"预测为 **31**，实测 `XfrmInStateProtoError`
  增量正好 **31**；无声通过预测 62、实测 62。⇒ HMAC/密钥/IV/布局/authsize=16 全部正确，
  包确实被解密，解密出的就是我们的明文 —— 但页缓存 md5 不变。
- 判据二（发送路径）：剥离 IPsec 后的六种发送形态（splice 16B / splice 整页 /
  sendfile / vmsplice / MSG_ZEROCOPY ×2）**全部是拷贝**。
- 判据三（设备内核反汇编）：`udp_sendpage` 走 `iov_iter_bvec` + `udp_sendmsg`（不是
  `ip_append_page`）；`__ip_append_data` **没有 MSG_SPLICE_PAGES(BIT27) 判定、
 完全不调用 `skb_append_pagefrags`**，只会 `sk_alloc_send_pskb` + `sk_page_frag_refill`
  自己分配 frag 页再拷贝。⇒ 页缓存页永远进不了 skb。
- 判据四（备选路）：`CONFIG_AF_RXRPC` 未编入（`nm | grep -c rxrpc` = 0），
  CVE-2026-43500 那条路也不存在。CZB2（6.1.128）逐项相同。

根因：**`MSG_SPLICE_PAGES` 语义是 Linux 6.5 才有的**；6.1 的 UDP 发送路径只会拷贝。
参考实现自检串写的是 `SM-S938B / 6.6.98-android15-8-…`（**6.6**），与这个结论自洽。
LKML 上内核开发者对 6.1 PoC 的质疑（"6.1 没有 MSG_SPLICE_PAGES，你确定不是走了
RxRPC 那条路？"）说的正是这件事。

⇒ 下面 0x01–0x08 的原方案保留作为**设计记录**；对 S9280 的落地方式改为
**"运行时探针 + 明确拒绝执行"**（见 0x07）。

## 0x00 为什么再加一个引擎

现有 GhostLock 链（CVE-2026-43499）是**竞态型**：`triggered=0` 是常态，需要 Shizuku/ADB 配对把
payload 以 shell 域跑起来，root 仅 per-boot，且跑完机器处于脆弱态（解锁可能卡死）。

DirtyFrag（CVE-2026-43284，xfrm-ESP 页缓存写）是**确定性**逻辑漏洞，且：

| 维度 | GhostLock（现有） | DirtyFrag（新增） |
| --- | --- | --- |
| 触发 | 竞态，概率 | 确定性，失败不 panic |
| 前置权限 | 需要 Shizuku/ADB 配对 | **app 级 API（IpSecManager），不需要任何特殊权限** |
| 持久性 | per-boot，需手动重跑 | 可**开机自动恢复**（LOCKED_BOOT_COMPLETED） |
| 副作用 | 短期内核脆弱态 | **页缓存污染**（须 drop_caches / 重启）+ 加载 permissive LKM |

两者适用条件**互相独立**（各自的内核补丁状态不同），所以不是替代而是**互补**：先试 DirtyFrag，
不可用再退回 GhostLock。

## 0x01 许可证红线（重要）

参考仓库及其上游（`2253845067/DFRoot`、`V4bel/dirtyfrag`、`lsposed/lspromise`、
`polygraphene/DFReroot`、`combeng6th/DirtyInit`）**全部没有 LICENSE**：

- ❌ 不把它们的任何代码/产物放进本仓库
- ❌ 不把它们的 APK/ko 作为发布物分发
- ✅ 只读参考其**设计思路与接口形态**，实现全部自己写
- ✅ 一次性本地可行性验证可以借用其产物，但不入库、不发布

## 0x02 架构：引擎抽象

```
rootmy/
  RootViewModel.kt            ← 现有流程编排（保持：Shizuku、固件选择、ksud、遥测都在这层）
  engine/
    RootEngine.kt             ← 接口：probe(): EngineAvailability / run(callbacks): EngineResult
    GhostLockEngine.kt        ← 现有 payload 路径，包成 Engine（行为不变）
    DirtyFragEngine.kt        ← 新引擎（alpha，默认关闭）
  dirtyfrag/
    DfDeviceCheck.kt          ← 启动自检：机型/KMI/6 条路径/SELinux 标签/钩子符号
    DfIpsec.kt                ← IpSecManager 建 UdpEncapsulationSocket + ESP transform
    DfPatch.kt                ← 页缓存块改写（IV 计算、splice、目标 lib 定位）
    DfNative.kt               ← JNI 入口
  boot/
    DfBootReceiver.kt         ← LOCKED_BOOT_COMPLETED / BOOT_COMPLETED 自动恢复
```

编排策略（`RootViewModel`）：

1. `DirtyFragEngine.probe()` → 自检全过 **且** 开关已开 → 走它
2. 否则 / 失败 → 回落 `GhostLockEngine`（现有逻辑一行不改）
3. UI 显示当前用的是哪个引擎、自检报告、失败原因

## 0x03 需要的资产（`app/src/main/assets/`）

| 资产 | 来源 | 说明 |
| --- | --- | --- |
| `dirtyfrag-android14-6.1.ko` | **自己用 DDK 编**（`ghcr.io/ylarod/ddk-min:android14-6.1`），对 `vmlinux_dzh3.elf` 做符号审计 | 写进 `/vendor/lib64/libstagefrighthw.so` 后由 vendor_modprobe 加载；permissive 用途 |
| 钩子 shellcode（libc / libc++ 两份） | **自己写**（汇编，按参考的接口形态：patch 到 `__libc_init` / ostream sentry） | 运行时从 ELF 解析符号偏移，不硬编码 |
| `splicehelper` | 自己写 | crash_dump64 跳板，用 CBC 原语改写后读 vendor 库页 |
| ksud | 用我们自己的 3.3.0(32601) | **需补 3 个参数**：`--stage-from` / `--soft-reboot` / `--ro-partitions`（参考实现固定传这三个，否则参数解析失败） |

## 0x04 设备门槛自检（照参考的 DeviceCheck 思路自己实现）

启动/运行前必须全过，任一不过就**拒绝动手**（避免 patch 到一半失败）：

```
机型/KMI     : SM-S9280 / 6.1.145-android14-11-3254743-abS9280ZCS6DZH3 → android14-6.1
必需路径     : /vendor/lib64/libstagefrighthw.so   (vendor_file 标签)
               /apex/com.android.runtime/bin/crash_dump64 (crash_dump_exec 标签)
               /system/lib64/libc.so → apex .../bionic/libc.so   (真身在 APEX)
               /system/lib64/libc++.so   (真文件，非链接)
               /vendor/bin/modprobe → toolbox (applet)
               /system/bin/logcat
钩子符号     : libc: __libc_init ✔   libc++: _ZNSt3__113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_ ✔
SELinux      : 期望 Enforcing（干净基线）
```

> 2026-09-13 已在 SM-S9280/DZH3 上实测：以上全部满足 ✔

## 0x05 设置项（默认关闭）

- **DirtyFrag 引擎（alpha）** —— 开关，默认关
- **开机自动恢复 root** —— 开关（仅 DirtyFrag 引擎支持）
- **运行后自动 drop_caches** —— 默认开（页缓存污染清理）
- 自检报告入口（关于页/设置页），展示 0x04 的逐项结果

## 0x06 风险与安全约束

1. **页缓存污染**：跑完必须 `echo 3 > /proc/sys/vm/drop_caches` 或重启，否则系统行为可能异常
2. **permissive LKM**：会把 SELinux 置为 Permissive，重启清除；UI 必须明确告知
3. **只读分区保护**：不写 `/system`（EROFS），走 bind-mount 方式（参考实现的做法）
4. **默认不给 adb shell 提权**：不传 `--allow-shell`（与参考实现一致，安全性优先）

## 0x07 里程碑（alpha 线，按实测结论修订）

| 版本 | 内容 | 验收 |
| --- | --- | --- |
| `alpha1` | 设备自检页 + 引擎开关（不做实际注入） | ✅ 已完成（commit `54591be`） |
| `alpha2` | **运行时可行性探针** + 不支持时明确拒绝执行 | 探针在 6.1 机型上判定"不可达"并拒绝跑链，不污染 `/apex`、`/vendor`、`/system` 页缓存 |
| `alpha3` | 仅当探针判定可达时（≥6.5 内核语义的机型）才提供引擎，失败回落 GhostLock | 探针放行时才走链；否则一律 GhostLock |

**设计要点（alpha2 的探针）**：自研 `page_share_probe.c`
（`~/项目/samsung_root_research/02_exploit工程/dirtyfrag-s9280/tools/`），
用普通 UDP 在几百毫秒内判定"页缓存页能否进 skb"。
再加一条自校准：对可控测试文件写入后 **pread 回读比对**，把"发包成功"和
"真的写进去了"区分开（本次排查正是靠这两点才定位到根因）。

## 0x08 已解决 / 待定

1. ~~内核补丁状态最终判定~~ → **已定论**：设备内核确实未修补
   （`esp_input` 无 shared-frag 检查、`ip_append_page` 无 `SKBFL_SHARED_FRAG` 写入），
   但**这没有意义**——发送路径根本不产生共享 frag，原语不可达。
2. ksud 三个新参数（`--stage-from` / `--soft-reboot` / `--ro-partitions`）倾向自己实现
   （许可证问题）。**该工作已被本次结论冻结**：DirtyFrag 引擎不上 S9280，
   相关 ksud 补丁与 LKM 只作为研究留档。
3. GhostLock 保留为 S9280 的**唯一引擎**（原"回退"定位升级为"主引擎"）。
