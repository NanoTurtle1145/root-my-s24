# RootMyS9280

**免解锁 root Samsung Galaxy S24 Ultra (SM-S9280 国行/港版/台版)** —— 基于内核漏洞 CVE-2026-43499 的安全研究项目。

> 安全研究声明：本项目仅用于安全研究与自有设备维护。使用内核漏洞提权存在导致系统崩溃、数据丢失、设备变砖的风险，使用者需自行承担一切后果。请勿用于非法用途。

## 特性

- 免解锁 bootloader：不刷 BL、不升 rev bit，bootloader 保持锁定
- 不熔断 KNOX：e-fuse 状态保持原样（可搭配 KnoxPatch 恢复 Secure Folder 等 KNOX 功能）
- 半持久化：每次重启后运行一次 App 即可重新加载 KernelSU 驱动
- 支持 KernelSU 生态：Zygisk-Next / LSPosed / KnoxPatch 等模块

## 支持设备

| 项目 | 值 |
|---|---|
| 机型 | Samsung Galaxy S24 Ultra |
| 型号 | SM-S9280（国行 CHC / 港版 TGY / 台版 BRI） |
| 固件 | S9280ZCS6DZF2（One UI 8.5） |
| 内核 | 6.1.145-android14-11-3254743-abS9280ZCS6DZF2 |

> **其他机型/固件适配**：同一 e3q 平台（S24 系列）同代固件符号基本一致，理论可适配；
> 不同构建号需逐符号对比修正（本项目 S9280 vs S928U1 仅 `kmalloc_caches` 一个符号不同）；
> 跨平台/跨大版本需完整重新定标，且漏洞可能已被修复。适配方法见仓库文档与
> 适配流程：提取固件内核 → vmlinux-to-elf 恢复符号 → 与参考 target.h 逐项对比 → 生成目标配置 → 真机验证。

## 使用流程

1. 安装并启动 Shizuku（无线/有线 ADB 授权）
2. 打开 RootMyS9280 → 点击「开始 Root」（建议熄屏运行，降低内核竞态概率）
3. 等待 exploit 完成 → 自动执行 KernelSU late-load
4. 安装 KernelSU Manager（v3.2.5）→ 强制停止后重开，显示「工作中 <LKM> [越狱模式]」
5. 安装模块：Zygisk-Next → LSPosed → KnoxPatch，重启 Zygote 后配置

> exploit 是概率性的，失败/重启后重试即可（成功率随尝试累加）。

## 注意事项

- **停在 DZF2 固件，不要升级**：新固件会修复 CVE-2026-43499 相关漏洞
- exploit 概率性成功：失败多试几次，必要时重启手机（成功标记：`exploit completed` + `retval=0 socket=1`）
- 运行期间建议熄屏（降低内核竞态导致的崩溃概率）
- 每次重启手机后需要重新运行一次「开始 Root」以加载 KernelSU 驱动

## 构建

```sh
./gradlew assembleDebug    # debug APK
./gradlew assembleRelease  # release APK（需自行配置签名）
```

载荷（exploit / root helper / ksud）已内置在 `app/src/main/assets/`。载荷构建链属开发者职责，不在本仓库范围。

## 依赖与致谢

- [CVE-2026-43499](https://github.com/IonStack/CVE-2026-43499) 安全研究（IonStack / NebuSec）
- [Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) 免解锁 root 参考工程
- [KernelSU](https://github.com/tiann/KernelSU)（GPL-2.0）
- [Zygisk-Next](https://github.com/Dr-TSNG/ZygiskNext)
- [LSPosed](https://github.com/LSPosed/LSPosed)（GPL-3.0）
- [KnoxPatch](https://github.com/salvogiangri/KnoxPatch)

## License

[GNU General Public License v3.0](LICENSE)
