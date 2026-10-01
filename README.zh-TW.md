<p align="center">
  <img src="docs/banner.png" alt="RootMyS24 - 比那名居天子手持 S24 Ultra" width="100%">
</p>

<h1 align="center">RootMyS24</h1>

<p align="center">
  免解鎖 root · Samsung Galaxy S24 系列（S9210 / S9260 / S9280）+ 國行 Z Fold6<br>
  基於核心漏洞 <strong>CVE-2026-43499</strong> 的安全研究專案
</p>

<p align="center">
  <a href="https://github.com/NanoTurtle1145/root-my-s24/releases"><img src="https://img.shields.io/badge/version-3.5.0-1E88E5?style=flat-square" alt="Version 3.5.0"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-1E88E5?style=flat-square" alt="GPL-3.0"></a>
  <a href="https://github.com/NanoTurtle1145/root-my-s24"><img src="https://img.shields.io/badge/platform-Android-1E88E5?style=flat-square" alt="Android"></a>
</p>

<p align="center">
  <a href="README.md">简体中文</a> · <b>繁體中文</b> · <a href="README.en.md">English</a>
</p>

> 安全研究宣告：本專案僅用於安全研究與自有裝置維護。使用核心漏洞提權存在導致系統崩潰、資料丟失、裝置變磚的風險，使用者需自行承擔一切後果。請勿用於非法用途。

---

## 使用情況

> 以下為**匿名聚合**統計（資料來自 App 內可選的執行日誌共享）：不含 IP、城市或任何裝置標識。
> 圖表由專案伺服器動態生成，隨資料自動更新。

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=summary" alt="RootMyS24 使用統計" width="100%">
</p>

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=country" alt="使用者地區分佈" width="100%">
</p>

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=outcome" alt="執行結果構成" width="100%">
</p>

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=firmware" alt="韌體建置分佈" width="100%">
</p>

## 特性

- 免解鎖 bootloader：不刷 BL、不升 rev bit，bootloader 保持鎖定
- 不熔斷 KNOX：e-fuse 狀態保持原樣（可搭配 KnoxPatch 恢復 Secure Folder 等 KNOX 功能）
- 半持久化：每次重啟後執行一次 App 即可重新載入 KernelSU 驅動
- 支援 KernelSU 生態：Zygisk-Next / LSPosed / KnoxPatch 等模組
- **KernelSU 驅動版本可選**（機型頁獨立分頁）：**3.3.0（推薦）** 或 3.2.5；版本清單由服務端線上下發，不適用的韌體自動回退預設驅動
- **SELinux 隱藏（3.3.0 獨有）**：管理器「SELinux 狀態」顯示**強制執行**，而核心實際處於寬容模式，相關修改更難被檢測（3.2.5 無此能力，會顯示「寬容模式」）
- 「已實測」標記**線上獲取**：機型卡片的實測狀態由服務端下發，無需等 App 更新即可調整
- 現代 Material 3 介面：動態 ambience 頭部、懸浮導航、多語言
- 首次啟動必讀指南：說明臨時 root 不解鎖 bootloader、重啟即恢復，並警告不要用 KernelSU 永久安裝（鎖定 BL 上重啟可能變磚）；關於頁可隨時再看
- 執行日誌共享（可選）：始終提供 / 每次詢問 / 不提供，設定裡隨時可改，用於診斷新韌體上的失敗
- 除錯模式：設定頁點版本號七下開啟，可測日誌收集端連通性、檢視機器標識、重顯首次引導

## 支援裝置

| 機型 | 韌體 | 核心 | 狀態 |
|---|---|---|---|
| SM-S9280（國行 DZF2） | S9280ZCS6DZF2 | 6.1.145 | 已實測成功（基線） |
| **SM-S9280（國行 DZH3）** | **S9280ZCS6DZH3** | **6.1.145** | **已實測成功（KernelSU 3.3.0 適配完成）** |
| SM-F9580（國行 Z Fold6） | 共用 DZF2 載荷 | 同 GKI 建置號 | 酷安使用者實測成功 |
| SM-S9280（港版/臺版 DZE2） | S9280ZHS6DZE2 | 6.1.145 | 已實測成功（港臺同建置號共用載荷） |
| SM-S9280（國行 DZG1） | S9280ZCS6DZG1 | 6.1.145 | 併入 DZF2 載荷（v2.5.6 起） |
| SM-S9280（港版/臺版 DZH3） | S9280ZHS6DZH3 | 6.1.145 | 併入 DZE2 載荷（與外版共用） |
| SM-S9280（港版/臺版 CZA1） | S9280ZHS4CZA1 | 6.1.128 | 已定標（港臺共用） |
| SM-S9260（臺版 DZG1） | S9260ZHS6DZG1 | 6.1.145 | 單裝置實測成功（港臺 DZE2 載荷，見 issue #3） |
| SM-S9210（國行 BYH7，One UI 7） | S9210ZCU4BYH7 | 6.1.99 | 已定標，待真機驗證 |
| SM-S9310/S9360/S9380（國行 S25，One UI 8.5） | S9310ZCSCCZG1 | 6.6.98 | 已定標（Android 15 核心，獨立載荷） |

> 同平臺（e3q/e1q）同建置號韌體核心符號一致，可直接通用；港版與臺版同建置號共用同一載荷。跨建置號需逐符號對比修正；跨平臺/跨大版本需完整重新定標，且漏洞可能已被修復。適配方法與各目標定標報告見[文件導航](#文件導航)。
>
> **國行 DZF2 載荷自 v3.5.0 起覆蓋 DZE2–DZH3**（同一條 6.1.145 核心線）；外版 DZE2 載荷覆蓋 DZE2–DZG1。

## KernelSU 驅動版本

機型頁有獨立的「KernelSU 驅動版本」分頁，切換它只決定**載入哪個 KernelSU 驅動**，exploit 載荷本身與版本無關。

| 版本 | 說明 |
|---|---|
| **3.3.0（32601）** | **推薦**。支援 SELinux 隱藏——管理器「SELinux 狀態」顯示**強制執行**，核心實際處於寬容模式 |
| 3.2.5（32525） | 回退用。Root 功能正常，但**無法隱藏 SELinux 修改**，管理器會顯示「寬容模式」 |

版本清單由服務端線上下發（第一項即當前推薦），且只對**提供該驅動的韌體**生效——不適用的韌體會自動回退到自身預設驅動，並在分頁下方提示。

> 驅動只認可**官方簽名**的 KernelSU Manager。請安裝官方 v3.3.0；裝其它簽名的版本會顯示「未安裝」，此時首頁只提供「越獄」按鈕而非「工作中 [越獄模式]」。

## 使用流程

1. 安裝 App，授權方式（v2.5.5 起無線除錯為實驗性開關，預設關閉）：
   - **Shizuku**（預設）：安裝並啟動 Shizuku（無線/有線 ADB 授權）
   - **無線除錯**（實驗性）：設定頁開啟「無線除錯授權」後，主頁支援通知配對 / 配對碼 / 直連
2. 選擇目標韌體版本（按地區分組：國行 / 港版臺版），並在「**KernelSU 驅動版本**」分頁里選好版本（預設 3.3.0），點選「開始 Root」（建議熄屏執行，降低核心競態機率）
3. 等待 exploit 完成，自動執行 KernelSU late-load
4. 安裝 **官方 KernelSU Manager v3.3.0**，跑完後首頁顯示「**工作中 [越獄模式] · 版本 32601-2**」

> exploit 是機率性的，失敗/重啟後重試即可（成功率隨嘗試累加）。成功標記：`exploit completed` + `retval=0 socket=1`。

---

## 文件導航

完整文件中心見 [docs/README.md](docs/README.md)。

### 入門與使用

- [docs/release-v3.5.0.md](docs/release-v3.5.0.md) —— 最新發布說明（KernelSU 3.3.0 適配 / 完整改動清單）
- [docs/release-v3.4.0.md](docs/release-v3.4.0.md) —— v3.4.0 釋出說明
- [docs/forum-and-admin.md](docs/forum-and-admin.md) —— 討論區與管理端說明
- [docs/auth-plan.md](docs/auth-plan.md) —— 授權方案規劃（Shizuku / 無線除錯各方案狀態）
- [docs/release-v2.5.5.md](docs/release-v2.5.5.md) —— v2.5.5 釋出說明（無線除錯收斂為實驗性開關）
- [docs/release-v2.2.md](docs/release-v2.2.md) —— v2.2 釋出說明（歷史）

### 技術原理

- [docs/technical-principles.md](docs/technical-principles.md) —— 本 App 技術原理（漏洞成因 / 利用鏈 / 為什麼免解鎖 / 風險）
- [研究倉庫 VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md) —— 完整漏洞原理剖析（研究筆記，權威）

### 韌體適配與定標

- **[docs/adaptation-guide-full.md](docs/adaptation-guide-full.md) —— 完整適配指南（手把手，照著做即可；含判據與失敗分類）⭐**
- [docs/adaptation-guide.md](docs/adaptation-guide.md) —— 適配方法論（舊版，偏原理）
- [docs/dze2-target-complete.md](docs/dze2-target-complete.md) —— 港版 DZE2 定標報告
- [docs/dzg1-target-complete.md](docs/dzg1-target-complete.md) —— 國行 DZG1 定標報告
- [docs/cza1-target-complete.md](docs/cza1-target-complete.md) —— 港版 CZA1 定標報告
- [docs/oneui7-adaptation-report.md](docs/oneui7-adaptation-report.md) —— One UI 7 (BYH7) 可行性報告
- [docs/byh7-target-complete.md](docs/byh7-target-complete.md) —— BYH7 定標完成報告

### 執行日誌與故障定位

- [docs/run-log-analysis.md](docs/run-log-analysis.md) —— 成功/失敗日誌逐行對比與判讀
- [docs/sm-s9380-rmg-root-experience.md](docs/sm-s9380-rmg-root-experience.md) —— SM-S9380 實戰經驗歸檔

### 研究資料

- [docs/research-index.md](docs/research-index.md) —— 研究資料全量索引
- [docs/blog-s9280-root.md](docs/blog-s9280-root.md) —— 部落格：S24 Ultra 國行免解鎖 Root 實踐
- [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research) —— 研究倉庫（韌體/exploit 工程/定標報告權威映象）

## 技術原理（概要）

本 App 基於核心漏洞 **CVE-2026-43499**（rtmutex 核心棧 use-after-free）：通過 PI futex 鏈死鎖回滾觸發錯誤清理路徑，留下指向已釋放核心棧的懸垂指標；複用該棧偽造 `rt_mutex_waiter` 後獲得實體記憶體讀寫，依次繞過 KASLR、降級 SELinux、以 root 執行輔助程式，最終 late-load KernelSU 驅動。全程不改動持久化分割槽，bootloader 保持鎖定、KNOX e-fuse 不熔斷。

> 完整原理（成因 / 利用鏈 / 防護繞過 / 原始碼對照）見 [docs/technical-principles.md](docs/technical-principles.md) 與研究倉庫 [VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md)。

## 注意事項

- **停在已適配韌體，不要升級**：新韌體會修復 CVE-2026-43499 相關漏洞
- exploit 機率性成功：失敗多試幾次，必要時重啟手機
- 執行期間建議熄屏（降低核心競態導致的崩潰機率）
- 每次重啟手機後需要重新執行一次「開始 Root」以載入 KernelSU 驅動
- **跑完可以正常用，但別久留**：exploit 會留一個程式佔住幾塊已回收的核心頁（`stability keeper`）——這些頁在核心記帳裡仍是空閒的，所以高記憶體壓力下仍有小機率被撞上而重啟。**日常使用通常沒問題**（早期"解鎖即黑屏重啟"的問題已由提前 fork keeper 修復），但建議拿到 root 後把要做的事做完就重啟，不要在長時間高負載場景（遊戲、大量後台任務、反覆鎖屏解鎖）裡停留。不管成功還是失敗都一樣——失敗時核心記憶體也已被改寫。
- **跑之前先重啟**：上一次失敗後不重啟就接著跑，往往只會一直失敗（殘留狀態未清理）。
- **管理器必須用官方 KernelSU v3.3.0**：核心驅動只認官方簽名的管理器，裝其它簽名的版本會顯示「未安裝」。
- **系統負載高時成功率驟降**：剛開機、後台在跑重活（下載/遊戲/錄屏）時核心競態更容易失敗甚至崩潰，建議等 `load` 降到個位數再跑。

## 建置

```sh
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:assembleRelease  # release APK（需自行配置簽名）
```

版本號仍由 git 推導（`versionCode` = 提交數，`versionName` = 最近的 tag），但**預釋出渠道**會給
versionName 追加一個字尾，方便把 beta 包與正式釋出區分開（關於頁/設定頁顯示、APK manifest、上傳的執行日誌裡的 appVersion 都會帶上）：

```sh
./gradlew :app:assembleRelease -Prms24Channel=alpha1   # → 3.5.0-alpha1 (157)  引擎試驗包
./gradlew :app:assembleRelease -Prms24Channel=alpha2   # → 3.5.0-alpha2 (157)
./gradlew :app:assembleRelease -Prms24Channel=beta1    # → 3.5.0-beta1  (157)  機型/韌體適配預覽
./gradlew :app:assembleRelease -Prms24Channel=beta2    # → 3.5.0-beta2  (157)
./gradlew :app:assembleRelease -Prms24Channel=stable   # 正式版 → 3.5.0 (157)
```

渠道語義（別混用）：

| 渠道 | 含義 | 典型內容 |
| --- | --- | --- |
| `alphaN` | **引擎/框架試驗**（內部 + 自願測試者） | 換用新漏洞鏈（如 DirtyFrag 引擎）、重做注入流程 |
| `betaN` | **機型/韌體適配預覽** | 新增韌體載荷、某機型 ksud 適配 |
| `stable` | 正式釋出 | 只收已實測通過的改動 |

產物按 `RootMyS24-v{版本}-{渠道}-build{versionCode}.apk` 命名（如
`RootMyS24-v3.5.0-alpha1-build157.apk`），一眼能看出某臺機器裝的是哪一版、屬於哪條線。
渠道字尾**不改變版本號本身**（versionCode 與 tag 推匯出的基礎版本都不動）。

載荷（exploit / root helper / ksud）已內建在 `app/src/main/assets/`。載荷建置鏈屬開發者職責，不在本倉庫範圍。

## 依賴與致謝

- [CVE-2026-43499](https://github.com/IonStack/CVE-2026-43499) 安全研究（IonStack / NebuSec）
- [Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) 免解鎖 root 參考工程
- [KernelSU](https://github.com/tiann/KernelSU)（GPL-2.0）
- [Zygisk-Next](https://github.com/Dr-TSNG/ZygiskNext)
- [LSPosed](https://github.com/LSPosed/LSPosed)（GPL-3.0）
- [KnoxPatch](https://github.com/salvogiangri/KnoxPatch)
- [Vector](https://github.com/JingMatrix/Vector) 介面模板（Material 3 / ambience）

## License

[GNU General Public License v3.0](LICENSE)
