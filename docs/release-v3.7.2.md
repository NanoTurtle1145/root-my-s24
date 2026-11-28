# RootMyS24 v3.7.2

把 **SM-S9280 国行 DZJ3** 的 98260 载荷从「未测试」提升为「已实测」，
DZJ3 条目从此进入 s24 官方系列包（不再需要 all/exp 包）。

## 变更

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
