<div align="center">

  <!-- ░░░ HERO ░░░ -->
  <img src="assets/banners/gh-banner.png" width="100%" style="max-width: 920px; border-radius: 18px;" alt="Watube — It's our daily comfort">

  <br><br>

  <img src="assets/icons/Watube.png" width="92" alt="Watube icon" align="center">

  <h1>Watube</h1>

  <p>
    <strong><em>C'est notre confort au quotidien.</em></strong><br>
    <sub><em>It's our daily comfort.</em></sub>
  </p>

  <p>
    <b>A privacy-first, open-source YouTube client for Android</b><br>
    <sub>engineered for <b>fluidity</b>, <b>low resource usage</b> and <b>zero tracking</b>.</sub>
  </p>

  <!-- ░░░ TECH / STATUS BADGES ░░░ -->
  <p>
    <img src="https://img.shields.io/badge/Platform-Android%209%E2%80%9316-00C9A7?style=for-the-badge&logo=android&logoColor=white" alt="Android 9 to 16">
    <img src="https://img.shields.io/badge/Kotlin-100%25-00C9A7?style=for-the-badge&logo=kotlin&logoColor=white" alt="100% Kotlin">
    <img src="https://img.shields.io/badge/Material_3-Expressive-00C9A7?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3 Expressive">
  </p>
  <p>
    <a href="https://www.gnu.org/licenses/gpl-3.0.en.html"><img src="https://img.shields.io/badge/License-GPL_v3-2EA043?style=for-the-badge&logo=gnu&logoColor=white" alt="GPL-v3"></a>
    <a href="https://github.com/Lycaon-Project/Watube"><img src="https://img.shields.io/badge/Lycaon--Project-Watube-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Lycaon-Project"></a>
    <a href="https://github.com/Lycaon-Project/Watube/releases/latest"><img src="https://img.shields.io/badge/Release-26.10.4-00C9A7?style=for-the-badge&logo=github&logoColor=white" alt="Latest release 26.10.2"></a>
  </p>

  <br>

  <!-- ░░░ DOWNLOAD ░░░ -->
  <table border="0">
    <tr>
      <td align="center">
        <a href="https://github.com/Lycaon-Project/Watube/releases/latest">
          <img src="assets/badges/ghload.png" alt="Download latest release on GitHub" height="62">
        </a>
      </td>
      <td align="center">
        <a href="https://github.com/Lycaon-Project/Watube/releases/tag/nightly">
          <img src="assets/badges/ghload-nightly.png" alt="Download nightly build on GitHub" height="62">
        </a>
      </td>
    </tr>
    <tr>
      <td align="center">
        <a href="#"><img src="assets/badges/fdrload.png" alt="Get it on F-Droid (soon)" height="62"></a>
      </td>
      <td align="center">
        <a href="#"><img src="assets/badges/izzyload.png" alt="Get it on IzzyOnDroid (soon)" height="62"></a>
      </td>
    </tr>
  </table>

  <a href="#"><img src="assets/badges/tgload.png" alt="Join us on Telegram" height="54"></a>

  <br><br>

  <sub>⚠️ <strong>Nightly builds</strong> ship new features and fixes ahead of the stable channel. They are less tested — use at your own risk.</sub>

</div>

<br>

> [!NOTE]
> **Watube is an independent fork** maintained by **Lycaon-Project**, built on the foundations of the open-source LibreTube project.
> It follows its **own roadmap** — centred on raw performance, a lean resource footprint, security hardening and a modern design language — while keeping every feature of the app it grew from.

<br>

<div align="center">
  <img src="assets/readme/about.svg" height="54" alt="About">
</div>

## 🔍 Why Watube exists

YouTube ships with a deeply invasive [privacy policy](https://support.google.com/youtube/answer/10364219): your watch habits, tastes, opinions and far more are harvested and monetised.

**Watube cuts Google out of the loop.** It streams only the video you asked for and sends the strict minimum of data required to work — **no account, no ads, no behavioural tracking**. Your history, subscriptions and playlists live **on your device**, not on a server.

<table>
<tr>
<td width="50%" valign="top">

### 🎯 What this fork obsesses over

- ⚡ **Fluidity** — tuned for 120 Hz, fewer wake-ups, lighter battery drain
- 🧠 **Lean memory** — smaller buffers and caches for low-end phones
- 🛡️ **Hardened** — reduced attack surface, strict network & backup rules
- 🧩 **Reliability** — bounded retries instead of runaway loops
- 🎨 **Modern UI** — the Watube *aqua-green* identity on Material 3 Expressive

</td>
<td width="50%" valign="top">

### 📦 What you get out of the box

- 🚫 No ads, no sign-in, no telemetry
- 📥 Offline downloads & background audio
- 🗂️ Subscriptions, groups, playlists & bookmarks
- ⏭️ SponsorBlock · 👎 Return YouTube Dislike · 🏷️ DeArrow
- 🌙 Full dark / OLED-friendly theming

</td>
</tr>
</table>

<br>

<div align="center">
  <img src="assets/readme/feature.svg" height="54" alt="What's new">
</div>

## ✨ Latest improvements

The freshest work landing in Watube — focused on **playback correctness**, **sound** and **picture quality**:

| Area | What changed |
| :--- | :--- |
| 🎚️ **True quality ladder** | The resolution picker now mirrors YouTube's real tiers — **2160p 4K · 1440p · 1080p HD · 720p · 480p · 360p · 240p · 144p** — and **only offers tiers the source stream actually provides** (no more phantom values like "2026p"). *Automatique* stays on top and adapts to your connection. |
| 🔁 **No more playback loops** | A stream error no longer traps the player in an endless "source error → restart" cycle. Watube now **re-extracts a fresh stream once per video**, then surfaces the error cleanly instead of spinning forever. |
| 🔇 **Clean video switching** | Jumping to another video no longer lets the **previous audio bleed over the new one** — the old track is stopped and cleared before the next begins. |
| 🧹 **Zero-warning codebase** | A full pass across the Kotlin sources, plus fixes for translation format-string bugs that could crash certain locales at runtime. |

<br>

<div align="center">
  <img src="assets/readme/phone.svg" height="54" alt="Screenshots">
</div>

## 📱 Screenshots

<div align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_1.jpg" width="19%" alt="Home" style="border-radius: 14px;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_2.jpg" width="19%" alt="Feed" style="border-radius: 14px;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_3.jpg" width="19%" alt="Subscriptions" style="border-radius: 14px;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_4.jpg" width="19%" alt="Library" style="border-radius: 14px;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_9.jpg" width="19%" alt="Channel" style="border-radius: 14px;">
</div>

<div align="center"><sub>More shots in <a href="SCREEN_SHOT.md">the gallery →</a></sub></div>

<br>

<div align="center">
  <img src="assets/readme/feature.svg" height="54" alt="Engineering">
</div>

## ⚙️ Under the hood

Watube isn't a reskin. Its differentiator is **engineering**: deep rendering and lifecycle optimisations that keep playback buttery-smooth *("our daily comfort")* while shrinking the resource footprint.

<details open>
<summary><b>🚀 Performance & resources</b></summary>
<br>

| Optimisation | Implementation |
| :--- | :--- |
| **Smart UI polling** | Player-UI loops (`position`, queue buttons, chapter name, seek bar, chapter index) only do work when a value actually changes, slow down while paused, and stop entirely once the player detaches. |
| **Zero-allocation rendering** | SponsorBlock / chapter time-bars no longer allocate `Paint`/`Rect` every frame — no GC spikes during playback. |
| **Debounced search** | Suggestions are debounced (300 ms) instead of one network request per keystroke. |
| **Optimised persistence** | Watch position is saved every 5 s (and instantly on pause/end) and cached in memory, so list rows never block on Room while binding. |
| **Smart DiffUtil** | Rows are keyed by a stable id (video url, primary key…), so lists rebind and reload thumbnails only when content truly changed. |
| **Throttled notifications** | Download-progress notifications are capped at 2 updates/second. |
| **Lean memory** | Player back-buffer trimmed from 3 min → 1 min to spare RAM on low-end devices. |

</details>

<details>
<summary><b>🛡️ Quality, security & maintenance</b></summary>
<br>

- **Strict network security** — cleartext traffic fully disabled (only local dev hosts are exempt).
- **Secure backups** — encrypted backups explicitly exclude the account token to prevent session hijacking.
- **Smaller attack surface** — the unused boot receiver was removed.
- **Smart updates** — checks against [Lycaon-Project/Watube](https://github.com/Lycaon-Project/Watube/releases/latest) at most once every 12 h, plus an on-demand check in settings.
- **One-tap maintenance** — *Settings → Maintenance → Clear cache* wipes the image, HTTP and leftover app caches at once.
- **Resilient error handling** — the SABR data source, picture-in-picture, media-service connection and feed notifications use **bounded** retry loops, never infinite ones.
- **Unified loaders** — a single Coil image loader and a shared HTTP cache are created once for the whole app lifecycle.

</details>

<details>
<summary><b>🎨 Design system</b></summary>
<br>

- A signature **Watube aqua-green** accent (default), rounder component shapes, and a raised bottom navigation bar.
- The app title renders with a live colour gradient in the toolbar and the About screen.
- Built on **Material 3 Expressive**, OLED-friendly dark theme included.

</details>

<details>
<summary><b>🧱 Tech stack</b></summary>
<br>

| | |
| :--- | :--- |
| **Language** | Kotlin 2.3.20 — 100% Kotlin, 0 Java |
| **UI** | Material 3 Expressive, View system |
| **Playback** | AndroidX Media3 / ExoPlayer |
| **Build** | Gradle 9.8.0 · AGP 9.4.1 · JDK 21 |
| **SDK** | minSdk 28 (Android 9) · compile/target SDK 37 (Android 16) |

</details>

<br>

<div align="center">
  <img src="assets/readme/ltvnp.svg" height="54" alt="Comparison">
</div>

## ⚖️ How Watube compares

<table>
<tr>
<th align="left">vs. NewPipe</th>
<th align="left">vs. its LibreTube roots</th>
</tr>
<tr>
<td valign="top" width="50%">

Watube focuses squarely on **YouTube** and on **user experience**: Material 3 Expressive design, SponsorBlock / Return YouTube Dislike / DeArrow, and optional cross-device sync via [Piped](https://github.com/TeamPiped/Piped).

NewPipe also covers SoundCloud, PeerTube, Bandcamp and more. Both are excellent — try them and keep the one that fits you.

</td>
<td valign="top" width="50%">

Watube keeps the privacy-first philosophy and feature set of upstream, but adds its **own identity and engineering layer**: the aqua-green design, the performance/memory optimisations and security hardening above, and an **independent release channel and roadmap**.

Think of it as the same promise, tuned for *daily comfort*.

</td>
</tr>
</table>

<br>

<div align="center">
  <img src="assets/readme/community.svg" height="54" alt="Contributing">
</div>

## 🤝 Contributing

Ideas, bug reports, design, code cleanup or deep changes — all welcome.

```text
1.  Open an issue        → discuss before you build
2.  Fork & branch        → git checkout -b feature/your-feature-name
3.  Follow the style     → Kotlin official coding conventions
4.  Test thoroughly      → make sure it actually works
5.  Open a pull request  → with a clear description
```

**Commit style** — [conventional commits](https://www.conventionalcommits.org/) (`feat`, `fix`, `refactor`, `ci`, `chore`), e.g. `feat: add support for 120Hz displays`. Code follows the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).

> [!WARNING]
> Issues that bypass the template are closed. **AI-generated issue text or pull requests are not accepted.**

<br>

<div align="center">
  <img src="assets/readme/community.svg" height="54" alt="Translations">
</div>

## 🌍 Translations

Watube shares its string catalogue with the upstream project on Weblate — help translate it into your language:

<div align="center">
  <a href="https://hosted.weblate.org/projects/libretube/#languages">
    <img src="https://hosted.weblate.org/widgets/libretube/-/287x66-grey.png" alt="Translation status" />
  </a>
</div>

<br>

<div align="center">
  <img src="assets/readme/donate.svg" height="54" alt="Support">
</div>

## 💚 Support

<table>
<tr>
<td width="50%" valign="top">

### Back Watube
The simplest ways to help this fork grow:
- ⭐ **Star** the repository
- 🐛 **Report bugs** & suggest ideas
- 🔧 **Contribute** code or docs
- 📢 **Share** Watube around you

</td>
<td width="50%" valign="top">

### Thank the upstream
Watube stands on LibreTube's work — support its developers:
- [github.com/sponsors/Bnyro](https://github.com/sponsors/Bnyro)
- [liberapay.com/Bnyro](https://liberapay.com/Bnyro)
- **XMR** `47jAx7jMFo5iqy9VgDH98qL1bSK4kr6Pxi7HKWcRwsxbVYJdjxJtyrwXeAUa5MutvcQUmWMBfvAKnPAutDHvWEymUgLm5v8`
- **ETH** `0x599909f54CdC18B997Be8F032341d1Fb14BF4F39`

</td>
</tr>
</table>

<br>

<div align="center">
  <img src="assets/readme/privacy.svg" height="54" alt="Privacy & License">
</div>

## 🔒 Privacy & ⚖️ License

Watube is built to **protect its users**. [Our Privacy Policy](PRIVACY_POLICY.md) details exactly what the app stores to function, how it's used, and how your data is kept safe. Reading the upstream LibreTube policy and the policy of the instance you pick is also recommended.

<div align="center">

[![GNU GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Watube is **[Free Software](https://en.wikipedia.org/wiki/Free_software)** — use, study, share and modify it freely under the
**[GNU GPL v3 or later](https://www.gnu.org/licenses/gpl.html)**, published by the [Free Software Foundation](https://www.fsf.org/).

</div>

<br>

<details>
<summary><b>📜 Credits & acknowledgments</b></summary>
<br>

**Foundations**
Watube is built on the original **LibreTube** project by the libre-tube team. We're grateful for their foundational work on a privacy-focused YouTube client and their contributions to open source.

> **Important:** Watube is an independent project by Lycaon-Project. It is **not** affiliated with, endorsed by, or connected to the original LibreTube developers.

**Design & assets**
<sub>🎨 Watube branding, 3D assets & README design — Lycaon-Project / XelXen</sub>
<sub>📸 README screenshots — ARBoyGo</sub>
<sub>😊 Emoji — openmoji</sub>

**Icons**
<sub>🖼️ <a href="assets/icons/Watube.png">Watube app icon</a> — the Watube brand logo</sub>
<sub>🐦 <a href="app/src/main/res/mipmap-xxxhdpi/ic_bird_round.png">Boosted Bird</a> — Margot Albert-Heuzey</sub>

</details>

<br>

<div align="center">
  <sub>Made with 💚 by <strong>Lycaon-Project</strong></sub><br><br>
  <a href="#watube">⬆ Back to top</a>
</div>
