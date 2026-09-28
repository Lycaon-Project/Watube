<div align="center">

  <!-- Bannière principale GitHub -->
  <img src="assets/banners/gh-banner.png" width="100%" style="max-width: 900px; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 255, 100, 0.1);" alt="Watube - C'est notre confort au quotidien">

  <h1>Watube</h1>

  <p>
    <em><strong>C'est notre confort au quotidien.</strong></em><br>
    <em>It's our daily comfort.</em>
  </p>

  <p>
    Un client YouTube alternatif, open-source et axé sur la vie privée pour Android.<br>
    <sub>A privacy-focused, open-source alternative YouTube client for Android.</sub>
  </p>

  <div align="center" style="margin: 10px 0;">
    <a href="https://www.gnu.org/licenses/gpl-3.0.en.html"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge" alt="GPL-v3"></a>
    <a href="https://github.com/Lycaon-Project/Watube"><img src="https://img.shields.io/badge/GitHub-Lycaon--Project-181717?style=for-the-badge&logo=github" alt="GitHub Lycaon-Project"></a>
  </div>

  <!-- Badges de téléchargement personnalisés (Assets générés) -->
  <div align="center" style="width:100%; display:flex; justify-content:center; gap: 15px; margin: 25px 0; flex-wrap: wrap;">
    <a href="https://github.com/Lycaon-Project/Watube/releases/latest">
      <img src="assets/badges/ghload.png" alt="Download Latest Release on GitHub" height="65">
    </a>
    <a href="https://github.com/Lycaon-Project/Watube/releases/tag/nightly">
      <img src="assets/badges/ghload-nightly.png" alt="Download Nightly Build on GitHub" height="65">
    </a>
  </div>

  <div align="center" style="width:100%; display:flex; justify-content:center; gap: 15px; margin-bottom: 20px; flex-wrap: wrap;">
    <a href="#"><img src="assets/badges/fdrload.png" alt="Get it on F-Droid" height="65"></a>
    <a href="#"><img src="assets/badges/izzyload.png" alt="Get it on IzzyOnDroid" height="65"></a>
    <a href="#"><img src="assets/badges/tgload.png" alt="Join us on Telegram" height="65"></a>
  </div>

  <div align="center">
    <sub>⚠️ <strong>Note about Nightly builds:</strong> Nightly builds include features and fixes before the official release. They are generally less stable than normal releases. Use at your own risk.</sub>
  </div>

</div>

---

> **📌 Important Note** <br>
> This is **Watube**, an **independent fork** maintained by Lycaon-Project and based on the original LibreTube project. Watube is a separate entity with its own development roadmap focused on performance optimisation, resource efficiency, security hardening and modernisation — while keeping every feature of the upstream app.

---

<h2 align="left">🔍 About Watube</h2>

YouTube has an extremely invasive [privacy policy](https://support.google.com/youtube/answer/10364219) which relies on using user data in unethical ways. They store a lot of your personal data - ranging from ideas, music taste, content, political opinions, and much more than you think.

**Watube** aims at improving the users' privacy by being independent from Google and bypassing their data collection as much as possible. The app only sends the minimum amount of data necessary to ensure that the app works, e.g. it only loads the YouTube-video you want to play without tracking your behavior when using the app.

### 🎯 This Fork's Focus

While maintaining the original project's privacy-first philosophy, **Watube specifically focuses on**:

- ⚡ **Performance Optimization** - Optimize for 120 Hz screens and to reduce battery consumption
- 🐛 **Bug Fixes & Stability** - Comprehensive testing and reliability improvements
- 📱 **Android 15+ Compatibility** - Full support for latest Android features and requirements
- 🎨 **Enhanced User Experience** - Smoother animations and improved responsiveness
- 🔒 **Security Hardening** - Reduced attack surface and stricter network/backups rules

---

<h2 align="left">⚙️ Technical Deep Dive: Notable Changes in Watube</h2>

Unlike standard forks, Watube introduces deep architectural and rendering optimizations to guarantee a buttery-smooth experience ("our daily comfort") while drastically reducing the resource footprint.

#### 🚀 Performance & Resources
| Optimization Area | Technical Implementation |
| :--- | :--- |
| **Smart UI Polling** | Polling loops of the player UI (`position`, queue buttons, chapter name, seek bar, chapter index) only do work when their value actually changed, slow down while paused, and are stopped when the player is detached. |
| **Zero-Allocation Rendering** | SponsorBlock/chapter time bars no longer allocate `Paint`/`Rect` objects on every frame, eliminating GC (Garbage Collection) spikes during playback. |
| **Debounced Search** | Search suggestions are debounced (300 ms) instead of firing one network request per keystroke. |
| **Optimized Persistence** | The watch position is persisted every 5 s instead of every second (and immediately on pause/end). It is cached in memory so list rows don't have to query Room while binding. |
| **Smart DiffUtil** | Identifies rows by their stable id (video url, primary key, …), so lists rebind and reload thumbnails only when their content really changed. |
| **Throttled Notifications** | Download progress notifications are throttled to 2 updates per second to save CPU cycles. |
| **Memory Management** | The player back buffer was reduced from 3 minutes to 1 minute to save RAM on low-end devices. |

#### 🛡️ Quality, Security & Maintenance
- **Strict Network Security:** Cleartext traffic is completely disabled (only local development hosts are exempted).
- **Secure Backups:** Encrypted-app backups explicitly exclude the account token to prevent session hijacking.
- **Attack Surface Reduction:** Removal of the unused boot receiver.
- **Smart Updates:** Automatic update checks against [Lycaon-Project/Watube](https://github.com/Lycaon-Project/Watube/releases/latest), throttled to once every 12 hours, plus a manual check in the settings.
- **One-Tap Maintenance:** **Maintenance → Clear cache** in the settings wipes the image cache, the HTTP cache, and the leftover app cache in one tap.
- **Resilient Error Handling:** Reliable error handling in the SABR data source and picture-in-picture, using bounded retry loops (media service connection, feed notifications) instead of infinite ones.
- **Unified Loaders:** A unified Coil image loader and a shared HTTP cache are created once for the whole app lifecycle.

#### 🎨 Design System
- New **Watube aqua/green** accent (default), rounder component shapes, and a raised bottom navigation bar.
- The app title is rendered with a dynamic colour gradient in the toolbar and the about screen.

---

<h2 align="left">📱 Screenshots</h2>

<div style="width:100%; display:flex; justify-content:space-between; flex-wrap: wrap; gap: 10px;">
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_1.jpg"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_1.jpg" width=19% alt="Home" style="border-radius: 12px;"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_2.jpg"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_2.jpg" width=19% alt="Home" style="border-radius: 12px;"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_3.jpg"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_3.jpg" width=19% alt="Subscriptions" style="border-radius: 12px;"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_4.jpg"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_4.jpg" width=19% alt="Library" style="border-radius: 12px;"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_9.jpg"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_9.jpg" width=19% alt="Channel Overview" style="border-radius: 12px;"></a>
</div>

<sub>* More screenshots can be found [here](SCREEN_SHOT.md)</sub>

---

<h2 align="left">✨ Features</h2>

### Core Features
- ✅ **No Ads or Tracking** - Watch videos without interruptions or data collection
- ✅ **Subscriptions & Groups** - Follow channels and organize them into custom groups
- ✅ **Playlists & Bookmarks** - Create, manage, and quick-save playlists
- ✅ **Local History** - Track your viewing and search history locally
- ✅ **Downloads** - Save videos for offline viewing
- ✅ **Background Playback** - Listen to audio while using other apps
- ✅ **Piped Accounts** (optional) - Sync across devices via [Piped](https://github.com/TeamPiped/Piped)

### Privacy & Enhancements
- ✅ **[SponsorBlock](https://sponsor.ajay.app/)** - Automatically skip sponsored segments
- ✅ **[ReturnYouTubeDislike](https://www.returnyoutubedislike.com/)** - See video dislike statistics
- ✅ **[DeArrow](https://dearrow.ajay.app/)** - Get better, crowd-sourced titles and thumbnails

---

<h2 align="left">🤝 Contributing</h2>

Whether you have ideas, translations, design changes, code cleaning, or really heavy code changes, help is always welcome!

### How to Contribute
1. **Open an Issue** - Discuss your proposed changes before implementing them.
2. **Fork the Repository** - Create your own copy to work on.
3. **Create a Feature Branch** - `git checkout -b feature/your-feature-name`
4. **Make Your Changes** - Follow the existing code style and conventions.
5. **Test Thoroughly** - Ensure your changes work as expected.
6. **Submit a Pull Request** - Provide a clear description of your changes.

### 📝 Code Guidelines
- Follow [Kotlin official coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use meaningful commit messages following [conventional commit types](https://www.conventionalcommits.org/) (`feat`, `fix`, `refactor`, `ci`, `chore`).
- Example: `feat: add support for 120Hz displays`

> ⚠️ **Note**: Any issue avoiding the issue template will be ignored and closed.
> ⚠️ **Note**: The usage of AI to generate issue texts or pull requests is not permitted and such contributions will be rejected.

---

<h2 align="left">💚 Support the Project</h2>

### Support This Fork
If you find Watube useful and want to support its development:
- ⭐ **Star this repository** on GitHub
- 🐛 **Report bugs** and suggest improvements
- 🔧 **Contribute code** or documentation
- 📢 **Share the project** with others

### Support the Original Project
If you'd like to support the **original LibreTube developers**, donations can be made at:
- <https://github.com/sponsors/Bnyro>
- <https://liberapay.com/Bnyro>
- **Monero (XMR)**: `47jAx7jMFo5iqy9VgDH98qL1bSK4kr6Pxi7HKWcRwsxbVYJdjxJtyrwXeAUa5MutvcQUmWMBfvAKnPAutDHvWEymUgLm5v8`
- **Ethereum (ETH)**: `0x599909f54CdC18B997Be8F032341d1Fb14BF4F39`

---

<h2 align="left">🌍 Translations</h2>

Help make Watube available in your language!

<a href="https://hosted.weblate.org/projects/libretube/#languages">
  <img src="https://hosted.weblate.org/widgets/libretube/-/287x66-grey.png" alt="Translation status" />
</a>

---

<h2 align="left">⚖️ Differences to NewPipe</h2>

Watube's main difference to NewPipe is that it has a much stronger focus on user experience. Watube uses the modern [Material 3 Expressive](https://m3.material.io) design, supports external APIs such as SponsorBlock, ReturnYouTubeDislike, or DeArrow, and allows you to synchronize your user data across devices via Piped.

While Watube only supports content from YouTube, NewPipe also allows the use of other platforms like SoundCloud, PeerTube, Bandcamp, and media.ccc.de. Both are great clients for watching YouTube videos—try them both and see which one fits you best!

---

<h2 align="left">🔒 Privacy Policy and Disclaimer</h2>

Watube aims to protect the privacy of its users. [Our Privacy Policy](PRIVACY_POLICY.md) gives detailed information on which data the app stores in order to work, how it is being used, and how the project protects your personal information. It is recommended to read the privacy policy of the upstream LibreTube project as well as the privacy policy of the instance you have chosen inside the app.

---

<h2 align="left">📄 License</h2>

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Watube is [Free Software](https://en.wikipedia.org/wiki/Free_software): You can use, study, share and modify it at your will. The app can be redistributed and/or modified under the terms of the [GNU General Public License version 3 or later](https://www.gnu.org/licenses/gpl.html) published by the [Free Software Foundation](https://www.fsf.org/).

---

<details>
  <summary><h3 style="display:inline">📜️ Credits & Acknowledgments</h3></summary>

  <br>

**Original Project Inspiration**<br>
This fork is based on the original LibreTube project created by the libre-tube team. We acknowledge their foundational work in creating a privacy-focused YouTube client and their significant contributions to the open-source community.
> ⚠️ **Important**: This fork is an independent project maintained by Lycaon-Project. It is not officially affiliated with, endorsed by, or connected to the original LibreTube developers.

**Design & Assets**<br>
<sub>🎨 <strong>Watube Branding, 3D Assets & Readme Design</strong> by Lycaon-Project / XelXen</sub><br>
<sub>📸 <strong>Readme Screenshots</strong> by ARBoyGo</sub><br>
<sub>😊 <strong>Readme Emoji</strong> from openmoji</sub>

**Icons**<br>
<sub>🖼️ <a href="assets/icons/Watube.png">Watube App Icon</a> - the Watube brand logo</sub><br>
<sub>🐦 <a href="app/src/main/res/mipmap-xxxhdpi/ic_bird_round.png">Boosted Bird</a> by Margot Albert-Heuzey</sub>

</details>

<div align="center">
  <sub>Made with 💚 by the <strong>Lycaon-Project</strong></sub><br><br>
  <a href="#watube">⬆ Scroll to top</a>
</div>