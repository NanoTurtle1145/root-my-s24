# RootMyS9280

![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Platform](https://img.shields.io/badge/platform-Android%2016%2B-green)
![Language](https://img.shields.io/badge/language-Kotlin%2FCompose-purple)

**免解锁 root Samsung Galaxy S24 Ultra (SM-S9280 国行/港版/台版)** —— 基于内核漏洞 **CVE-2026-43499** 的安全研究项目。

> ⚠️ **安全研究声明**：本项目仅用于**安全研究与自有设备维护**。使用内核漏洞提权存在导致系统崩溃、数据丢失、设备变砖的风险，使用者需自行承担一切后果。请勿用于非法用途。

## ✨ 特性

- 🔓 **免解锁 bootloader**：不刷 BL、不升 rev bit，bootloader 保持锁定
- 🛡️ **不熔断 KNOX**：e-fuse 状态保持原样（可搭配 KnoxPatch 恢复 Secure Folder 等 KNOX 功能）
- ⚡ **半持久化**：每次重启后运行一次 App 即可重新加载 KernelSU 驱动
- 🧩 **KernelSU 生态**：支持 Zygisk-Next / LSPosed / KnoxPatch 等模块

## 📱 支持设备

| 项目 | 值 |
|---|---|
| 机型 | Samsung Galaxy S24 Ultra |
| 型号 | SM-S9280（国行 CHC / 港版 TGY / 台版 BRI） |
| 固件 | S9280ZCS6DZF2（One UI 8.5） |
| 内核 | 6.1.145-android14-11-3254743-abS9280ZCS6DZF2 |

> 同一 e3q 平台的 S928U1 DZF2 已验证同类漏洞链（见致谢）。其他固件需重新验证内核常量。

## 🔬 原理

1. **CVE-2026-43499**：通过浏览器/应用域的竞态漏洞获得内核任意读写（管道页重排 + CFI/物理内存 oracle）
2. 借助内核任意写，伪造 `call_usermodehelper` work 注入系统 workqueue，以 **root 身份执行** payload
3. payload（su 守护进程）监听本地 socket，客户端连接后以 root 执行命令
4. **KernelSU late-load**：通过守护进程以 root 加载 `kernelsu.ko`（v3.2.5），由 KernelSU Manager 管理 root 与模块

## 🚀 使用流程

1. 安装并启动 [Shizuku](https://shizuku.rikka.app/)（无线/有线 ADB 授权）
2. 打开 RootMyS9280 → 点击 **开始 Root**（建议熄屏运行，降低内核竞态概率）
3. 等待 exploit 完成 → 自动执行 KernelSU late-load
4. 安装 [KernelSU Manager](https://github.com/tiann/KernelSU/releases)（v3.2.5）→ 强制停止后重开，显示「工作中 <LKM> [越狱模式]」
5. 安装模块：Zygisk-Next → LSPosed → KnoxPatch，重启 Zygote 后配置

> ⚠️ exploit 是概率性的，失败/重启后重试即可（成功率随尝试累加）。

## ⚠️ 注意事项

- **停在 DZF2 固件，不要升级**：新固件会修复 CVE-2026-43499 相关漏洞
- exploit 概率性成功：失败多试几次，必要时重启手机（成功标记：`exploit completed` + `retval=0 socket=1`）
- 运行期间建议熄屏（降低内核竞态导致的崩溃概率）
- 每次重启手机后需要重新运行一次"开始 Root"以加载 KernelSU 驱动

## 🛠️ 构建

```sh
# 需要 Android NDK r29（构建原生载荷）
export ANDROID_NDK_HOME=/path/to/android-ndk-r29

# 1. 构建 CVE-2026-43499 载荷（在 fusion-s24u 适配工程中）
cd fusion-s24u && make TARGET=e3q-S9280ZCS6DZF2 stable

# 2. 将产物放入 App assets：
#    cve-2026-43499-app.stable.so → app/src/main/assets/cve-2026-43499
#    cve-2026-43499-root          → app/src/main/assets/cve-2026-43499-root
#    ksud（含 kernelsu.ko）        → app/src/main/assets/ksud-selected

# 3. 构建 App
./gradlew assembleDebug
```

## 📦 依赖与致谢

- [CVE-2026-43499](https://github.com/IonStack/CVE-2026-43499) 安全研究（IonStack / NebuSec）
- [Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) 免解锁 root 参考工程
- [KernelSU](https://github.com/tiann/KernelSU)（GPL-2.0）
- [Zygisk-Next](https://github.com/Dr-TSNG/ZygiskNext)
- [LSPosed](https://github.com/LSPosed/LSPosed)（GPL-3.0）
- [KnoxPatch](https://github.com/salvogiangri/KnoxPatch)

## 📄 License

[GNU General Public License v3.0](LICENSE)
