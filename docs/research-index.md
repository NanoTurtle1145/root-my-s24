# S9280 免解锁 Root 研究资料索引

> 整理时间：2026-08-17。按"研究阶段"归档，方便回看。

## 一、固件与内核（阶段 1）

| 文件 | 说明 |
|---|---|
| `s9280_port/boot_dzf2.img.lz4` | 从 AP 固件提取的 boot 分区原始镜像（22MB） |
| `s9280_port/boot_dzf2.img` | 解压后的 boot 镜像（100MB，可用 magiskboot unpack） |
| `s9280_port/kernel_dzf2_raw` | 解压后的内核映像（38MB，zImage 原始字节） |
| `s9280_port/vmlinux_dzf2.elf` | **核心资产**：vmlinux-to-elf 恢复的 ELF，含全部符号（43MB） |
| `s9280_port/p0_fingerprint_dzf2.h` | KASLR 滑动指纹（32 行 × 8 qword，设备验证 32/32） |

固件版本：S9280ZCS6DZF2（One UI 8.5，2026-06），内核 `6.1.145-android14-11-3254743-abS9280ZCS6DZF2`。

## 二、Exploit 工程（阶段 3）

| 路径 | 说明 |
|---|---|
| `s9280_port/fusion-s24u/` | exploit 源码工程（IonStack 参考实现的 S24 版） |
| `.../src/targets/e3q-S9280ZCS6DZF2/target.h` | **S9280 适配配置**（修正的偏移/常量） |
| `.../build/e3q-S9280ZCS6DZF2/cve-2026-43499-app.stable.so` | 最终载荷（104128B，App 内置） |
| `.../build/e3q-S9280ZCS6DZF2/cve-2026-43499-root` | root helper（27072B，su_daemon） |
| `s9280_port/fusion_s24u.tar.gz` | 工程打包备份 |

## 三、设备端诊断工具（阶段 4 排坑）

| 文件 | 说明 |
|---|---|
| `s9280_port/pipe_test.c` + 二进制 | F_SETPIPE_SZ 行为诊断（配额/限制判定） |
| `s9280_port/ksu_test.c` + 二进制 | KernelSU 驱动探测（reboot-magic syscall + ioctl） |
| `s9280_port/pipe_android61.c` | Android 6.1 内核 pipe.c 源码（对照反汇编） |
| `s9280_port/last_kmsg_panic.log` | **关键崩溃日志**：list_del BUG（worklist 竞态证据） |

## 四、参考研究（阶段 0）

| 路径 | 说明 |
|---|---|
| `~/rootmygalaxy/Root-My-Galaxy/` | S25U 免解锁 root 参考工程（App 端） |
| `~/rootmygalaxy/Root-My-Galaxy-Payloads/` | 载荷仓库：PORTING.md、S928U1.md、各机型 kdp 模块 |
| `.../kernelsu/` | KernelSU v3.2.5 各机型变体（e3q-S928U1-kdp 等） |
| `~/rootmygalaxy/CyberMeowfia/` | IonStack 源码（漏洞原理） |
| `~/rootmygalaxy/coolapk-s9280-solution/` | 酷安工具（社区有成功案例；其 16/16 失败在 preparing-memory 更可能是环境问题——残留进程污染 pipe_bufs 配额，而非偏移错误） |
| `~/root_research/*.md / *.json` | fusion_s24u.md、od4_*（odin4 分析）、rmgp_*（RMG issues） |

## 五、工具链（阶段 1/3）

| 路径 | 说明 |
|---|---|
| `~/sam85/tools/` | samloader（固件下载）、odin4（Thor 协议刷机，含 4 处本地修复）、magiskboot、adb |
| `~/sam85/scripts/` | flash.sh / repack_kernel.sh / make_abl_only.sh 等 |
| `s9280_port/android-ndk-r29/` | NDK r29（构建 payload / 诊断工具） |
| `s9280_port/android-14/` | build-tools（apksigner） |
| `s9280_port/s9280.keystore` | 签名密钥（pass: s9280root） |

## 六、发布物

| 路径 | 说明 |
|---|---|
| `~/AndroidStudioProjects/RootMyS9280/` | App 工程（v1.6，已开源） |
| `s9280_port/modules/` | Zygisk-Next / LSPosed / KnoxPatch 模块 |
| `s9280_port/blog_images/` + `blog_s9280_root.md` | 博客与配图 |
| `s9280_port/KernelSU_v3.2.5_32525-release.apk` | KernelSU Manager（官方） |

## 七、关键结论备忘

1. **S9280 与 S928U1 内核符号 16/17 一致**，唯一差异 `kmalloc_caches`：0x176cbb8（国行）vs 0x176c6f8（美版）
2. **F_SETPIPE_SZ EPERM 不是内核限制**：是残留 `cve43499-hold` 进程污染 uid 2000 的 pipe_bufs 配额（这也解释了酷安工具 preparing-memory 阶段 16/16 失败——同为环境问题而非其偏移错误；社区有成功案例佐证）
3. **worklist 竞态**：注入 workqueue 时显示驱动并发入队 → 孤儿 work → cancel_work_sync 时 `__list_del_entry_valid` BUG → panic（已用"注入前重查"修复）
4. **KNOX 完好的判断依据**：`ro.boot.warranty_bit=0` + `verifiedbootstate=green`（官方固件刷机不熔断）
5. 普通 GKI kernelsu.ko 可在国行加载（version 32525），kdp 变体为备选
