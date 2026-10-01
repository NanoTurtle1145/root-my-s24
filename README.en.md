<p align="center">
  <img src="docs/banner.png" alt="RootMyS24 - Tenshi Hinanawi holding an S24 Ultra" width="100%">
</p>

<h1 align="center">RootMyS24</h1>

<p align="center">
  Root without unlocking · Samsung Galaxy S24 series (S9210 / S9260 / S9280) + China Z Fold6<br>
  A security research project built on kernel vulnerability <strong>CVE-2026-43499</strong>
</p>

<p align="center">
  <a href="https://github.com/NanoTurtle1145/root-my-s24/releases"><img src="https://img.shields.io/badge/version-3.5.0-1E88E5?style=flat-square" alt="Version 3.5.0"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-1E88E5?style=flat-square" alt="GPL-3.0"></a>
  <a href="https://github.com/NanoTurtle1145/root-my-s24"><img src="https://img.shields.io/badge/platform-Android-1E88E5?style=flat-square" alt="Android"></a>
</p>

<p align="center">
  <b>English</b> · <a href="README.md">简体中文</a>
</p>

> **Security research notice:** This project is intended solely for security research and the
> maintenance of your own device. Privilege escalation through a kernel vulnerability carries the
> risk of system crashes, data loss and a bricked device. You bear full responsibility for any
> consequences. Do not use it for unlawful purposes.

---

## Usage

> The figures below are **anonymised aggregates** (from the App's optional run-log sharing):
> no IP addresses, cities or any device identifiers. The charts are generated dynamically by the
> project server and update automatically.

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=summary" alt="RootMyS24 usage statistics" width="100%">
</p>

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=country" alt="User region distribution" width="100%">
</p>

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=outcome" alt="Run outcome breakdown" width="100%">
</p>

<p align="center">
  <img src="https://blog.nanoturtle.cn/rms24_api/stats.php?svg=firmware" alt="Firmware build distribution" width="100%">
</p>

## Features

- **No bootloader unlock**: no BL flashing, no rev-bit bump — the bootloader stays locked
- **KNOX e-fuse untouched**: e-fuse state is left as-is (pair with KnoxPatch to restore Secure Folder and other KNOX features)
- **Semi-persistent**: just run the App once after each reboot to reload the KernelSU driver
- **KernelSU ecosystem**: Zygisk-Next / LSPosed / KnoxPatch and other modules
- **Selectable KernelSU driver version** (its own tab on the device page): **3.3.0 (recommended)** or 3.2.5.
  The version list is delivered online by the server; firmware that does not offer a given driver
  falls back to its own default automatically
- **SELinux hiding (3.3.0 only)**: the manager reports `SELinux status: Enforcing` while the kernel is
  actually permissive, making the modification harder to detect. 3.2.5 lacks this and reports
  "Permissive"
- **"Verified" marks fetched online**: the tested status shown on device cards comes from the server,
  so it can be adjusted without shipping a new App
- **Modern Material 3 UI**: dynamic ambience header, floating navigation, 19 languages
- **First-run guide**: explains that root is temporary and does not unlock the bootloader, that a
  reboot restores the original state, and warns against KernelSU's permanent install (which can brick
  a locked device). Re-readable from the About page at any time
- **Optional run-log sharing**: always / ask each time / never — changeable in Settings, used to
  diagnose failures on new firmware
- **Debug mode**: tap the version number seven times in Settings to test log-endpoint connectivity,
  view the machine identifier, or replay the first-run guide

## Supported devices

| Model | Firmware | Kernel | Status |
|---|---|---|---|
| SM-S9280 (China DZF2) | S9280ZCS6DZF2 | 6.1.145 | Verified working (baseline) |
| **SM-S9280 (China DZH3)** | **S9280ZCS6DZH3** | **6.1.145** | **Verified working (KernelSU 3.3.0)** |
| SM-F9580 (China Z Fold6) | shares the DZF2 payload | same GKI build | Verified by a Coolapk user |
| SM-S9280 (HK/TW DZE2) | S9280ZHS6DZE2 | 6.1.145 | Verified working (HK/TW share one payload) |
| SM-S9280 (China DZG1) | S9280ZCS6DZG1 | 6.1.145 | Merged into the DZF2 payload (since v2.5.6) |
| SM-S9280 (HK/TW DZH3) | S9280ZHS6DZH3 | 6.1.145 | Merged into the DZE2 (overseas) payload |
| SM-S9280 (HK/TW CZA1) | S9280ZHS4CZA1 | 6.1.128 | Calibrated (shared by HK/TW) |
| SM-S9260 (TW DZG1) | S9260ZHS6DZG1 | 6.1.145 | Verified on a single device (HK/TW DZE2 payload, see issue #3) |
| SM-S9210 (China BYH7, One UI 7) | S9210ZCU4BYH7 | 6.1.99 | Calibrated, awaiting device verification |
| SM-S9310/S9360/S9380 (China S25, One UI 8.5) | S9310ZCSCCZG1 | 6.6.98 | Calibrated (Android 15 kernel, separate payload) |

> Firmware sharing the same platform (e3q/e1q) and the same build number has identical kernel
> symbols and can be used interchangeably; HK and TW builds of the same number share one payload.
> Across build numbers every symbol must be compared and corrected; across platforms or major
> versions a full recalibration is required — and the vulnerability may already be patched.
> See the [documentation](#documentation) for the adaptation method and per-target calibration reports.
>
> **The China DZF2 payload covers DZE2–DZH3 since v3.5.0** (same 6.1.145 kernel line); the overseas
> DZE2 payload covers DZE2–DZG1.

## KernelSU driver version

The device page has a dedicated **"KernelSU driver version"** tab. Switching it only decides
**which KernelSU driver gets loaded** — the exploit payload itself is independent of the version.

| Version | Notes |
|---|---|
| **3.3.0 (32601)** | **Recommended.** Supports SELinux hiding — the manager reports `SELinux status: Enforcing` while the kernel is actually permissive |
| 3.2.5 (32525) | Fallback. Root works fine, but it **cannot hide SELinux modifications**; the manager reports "Permissive" |

The version list is delivered online by the server (the first entry is the current recommendation),
and each version only applies to **firmware that offers that driver** — other firmware falls back to
its own default and says so below the tab.

> The driver only trusts a KernelSU Manager with the **official signature**. Install the official
> v3.3.0; a manager signed with any other key shows "Not installed", and the home screen then offers
> only a "Jailbreak" button instead of "Working [Jailbreak mode]".

## How to use

1. Install the App and pick an authorisation method (wireless debugging is an experimental toggle,
   off by default, since v2.5.5):
   - **Shizuku** (default): install and start Shizuku (wireless or wired ADB authorisation)
   - **Wireless debugging** (experimental): enable "Wireless debugging authorisation" in Settings and
     use notification pairing / pairing code / direct connect from the home screen
2. Pick the target firmware (grouped by region: China / HK-TW), choose a version under
   **"KernelSU driver version"** (3.3.0 by default), then tap **"Start Root"**
   (running with the screen off is recommended — it lowers the chance of a kernel race)
3. Wait for the exploit to finish; KernelSU late-load runs automatically
4. Install the **official KernelSU Manager v3.3.0**; the home screen then shows
   **"Working [Jailbreak mode] · version 32601-2"**

> The exploit is probabilistic: if it fails, retry (or reboot and retry). Success markers:
> `exploit completed` + `retval=0 socket=1`.

---

## Documentation

The full documentation index lives in [docs/README.md](docs/README.md) (Chinese).

### Getting started

- [docs/release-v3.5.0.md](docs/release-v3.5.0.md) — latest release notes (KernelSU 3.3.0 adaptation, full changelog)
- [docs/release-v3.4.0.md](docs/release-v3.4.0.md) — v3.4.0 release notes
- [docs/forum-and-admin.md](docs/forum-and-admin.md) — discussion board and admin console
- [docs/auth-plan.md](docs/auth-plan.md) — authorisation options (status of each Shizuku / wireless-debugging approach)

### Technical background

- [docs/technical-principles.md](docs/technical-principles.md) — how this App works (root cause / exploit chain / why no unlock is needed / risks)
- [VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md) — full vulnerability analysis (research notes, authoritative)

### Firmware adaptation and calibration

- **[docs/adaptation-guide-full.md](docs/adaptation-guide-full.md) — complete adaptation guide (step by step; includes criteria and failure classes) ⭐**
- [docs/adaptation-guide.md](docs/adaptation-guide.md) — adaptation methodology (older, more theoretical)
- [docs/dze2-target-complete.md](docs/dze2-target-complete.md) — HK/TW DZE2 calibration report
- [docs/dzg1-target-complete.md](docs/dzg1-target-complete.md) — China DZG1 calibration report
- [docs/cza1-target-complete.md](docs/cza1-target-complete.md) — HK/TW CZA1 calibration report
- [docs/oneui7-adaptation-report.md](docs/oneui7-adaptation-report.md) — One UI 7 (BYH7) feasibility report
- [docs/byh7-target-complete.md](docs/byh7-target-complete.md) — BYH7 calibration report

### Run logs and troubleshooting

- [docs/run-log-analysis.md](docs/run-log-analysis.md) — line-by-line comparison of successful and failed logs
- [docs/sm-s9380-rmg-root-experience.md](docs/sm-s9380-rmg-root-experience.md) — SM-S9380 field notes

### Research material

- [docs/research-index.md](docs/research-index.md) — full research index
- [samsung-root-research](https://github.com/NanoTurtle1145/samsung-root-research) — research repository (firmware / exploit engineering / calibration reports)

> Most linked documents are written in Chinese; English translations are not available yet.

## How it works (summary)

This App exploits kernel vulnerability **CVE-2026-43499** (an rtmutex kernel-stack use-after-free):
a PI-futex chain deadlock rollback triggers an error-cleanup path that leaves a dangling pointer to a
freed kernel stack. That stack is reused to forge an `rt_mutex_waiter`, which yields physical memory
read/write; the chain then bypasses KASLR, relaxes SELinux, executes a helper as root, and finally
late-loads the KernelSU driver. Nothing persistent is ever written, so the bootloader stays locked and
the KNOX e-fuse stays intact.

> Full details (root cause / exploit chain / bypassing mitigations / source references) are in
> [docs/technical-principles.md](docs/technical-principles.md) and
> [VULNERABILITY_ANALYSIS.md](https://github.com/NanoTurtle1145/samsung-root-research/blob/main/VULNERABILITY_ANALYSIS.md) (Chinese).

## Caveats

- **Stay on a supported firmware build and do not update**: newer firmware patches CVE-2026-43499
- The exploit is probabilistic: retry a few times, reboot if needed
- Running with the screen off is recommended (fewer kernel races → fewer crashes)
- KernelSU must be re-loaded by running "Start Root" once after every reboot
- **You can keep using the device afterwards, but do not linger**: the exploit leaves a process
  holding a few reclaimed kernel pages (`stability keeper`). The kernel still accounts those pages as
  free, so under heavy memory pressure they can occasionally be hit and the device may reset.
  **Day-to-day use is normally fine** (the early "black screen on unlock" crash was fixed by forking
  the keeper earlier), but it is best to finish what you need with root and reboot rather than
  staying in heavy-load scenarios (games, many background tasks, repeated lock/unlock) for long.
  This holds whether the run succeeded or failed — a failed run has already modified kernel memory too.
- **Reboot before retrying**: running again without a reboot after a failed attempt usually just
  keeps failing (stale kernel state is not cleaned up)
- **The manager must be the official KernelSU v3.3.0**: the driver only trusts the official signature;
  any other build shows "Not installed"
- **A loaded system kills the success rate**: right after boot, or while heavy background work runs
  (downloads, games, screen recording), the kernel race is far more likely to fail or crash — wait
  until `load` drops to single digits

## Building

```sh
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:assembleRelease  # release APK (configure your own signing)
```

The version is still derived from git (`versionCode` = commit count, `versionName` = latest tag), but
**pre-release channels** append a suffix to `versionName` so beta builds stay distinguishable from
official releases (shown in About/Settings, the APK manifest and the `appVersion` field of uploaded
run logs):

```sh
./gradlew :app:assembleRelease -Prms24Channel=alpha1   # → 3.5.0-alpha1 (157)  engine experiments
./gradlew :app:assembleRelease -Prms24Channel=alpha2   # → 3.5.0-alpha2 (157)
./gradlew :app:assembleRelease -Prms24Channel=beta1    # → 3.5.0-beta1  (157)  device/firmware previews
./gradlew :app:assembleRelease -Prms24Channel=beta2    # → 3.5.0-beta2  (157)
./gradlew :app:assembleRelease -Prms24Channel=stable   # official → 3.5.0 (157)
```

Channel semantics (do not mix them up):

| Channel | Meaning | Typical content |
| --- | --- | --- |
| `alphaN` | **Engine / framework experiments** (internal + volunteer testers) | New exploit chains (e.g. the DirtyFrag engine), reworked injection flow |
| `betaN` | **Device / firmware adaptation previews** | New firmware payloads, ksud adaptation for a model |
| `stable` | Official release | Only changes already verified on real hardware |

Artifacts are named `RootMyS24-v{version}-{channel}-build{versionCode}.apk` (e.g.
`RootMyS24-v3.5.0-alpha1-build157.apk`), so a single glance tells you which build and which line a
device is running. The channel suffix **does not change the version number itself** (neither
`versionCode` nor the tag-derived base version moves).

The payloads (exploit / root helper / ksud) are bundled in `app/src/main/assets/`. Their build chain
is a developer responsibility and out of scope for this repository.

## Credits

- [CVE-2026-43499](https://github.com/IonStack/CVE-2026-43499) security research (IonStack / NebuSec)
- [Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) reference project for unlock-free root
- [KernelSU](https://github.com/tiann/KernelSU) (GPL-2.0)
- [Zygisk-Next](https://github.com/Dr-TSNG/ZygiskNext)
- [LSPosed](https://github.com/LSPosed/LSPosed) (GPL-3.0)
- [KnoxPatch](https://github.com/salvogiangri/KnoxPatch)
- [Vector](https://github.com/JingMatrix/Vector) UI template (Material 3 / ambience)

## License

[GNU General Public License v3.0](LICENSE)
