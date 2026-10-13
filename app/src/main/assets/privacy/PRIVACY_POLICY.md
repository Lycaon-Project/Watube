<h2 align="left">
<sub>
<img  src="fastlane/metadata/android/en-US/images/readme/privacy.svg"
      height="30"
      width="30">
</sub>
Watube - Privacy Policy
</h2>

**Watube aims to protect the privacy of its users. Our Privacy Policy gives detailed information on which data the app stores in order to work, how it is being used, and how the project protects your personal information. It is recommended to read the privacy policy of Watube as well as the privacy policy of the instance you have chosen inside the app.**

### Information Collection
Watube only stores necessary data such as watch history, settings, and bookmarks. Users have the option to refrain from utilizing data storage features. The application does not gather any information regarding its usage, nor does it create user profiles or track user activity. Additionally, the application avoids implementing tracking mechanisms within third-party libraries that it utilizes.

### Information Use
Watube contains no analytics, no telemetry and no advertising, and it does not sell your data. The app does share a small, documented set of data with a few third-party services in order to provide some of its features: this is described in the [Third-party services](#third-party-services) section below. Apart from those services and the YouTube/Piped traffic described in the rest of this policy, no data is shared with third parties. When using the app without logging in to a Piped account, no data will be shared with any Piped servers. Instead, the data will be stored solely on the device. However, once a user logs in to a Piped account, their subscriptions and playlists will be saved to that account on the corresponding Piped instance.

### Third-party services
Watube uses the following third-party services, which are not part of the Piped instance you selected:

* **SponsorBlock and DeArrow** ([sponsor.ajay.app](https://sponsor.ajay.app)): the app requests the sponsor segments of the videos you play or download. This lookup is done for every video, whether or not the SponsorBlock toggle (on by default) is enabled - that toggle only decides whether the segments are then applied. When the toggle is enabled, the app also requests the sponsor badge shown on the video cards in the lists. In the default "Full local mode" these requests go directly to the service, otherwise they go through the instance you selected; the badge lookup, whenever it happens, goes directly to the service, and only sends the first five characters of the SHA-256 hash of the video identifier instead of the identifier itself. DeArrow titles and thumbnails are requested only when DeArrow is enabled (disabled by default), directly from the service in "Full local mode" and through your instance otherwise. Submitting a segment, a vote or a DeArrow title/thumbnail - disabled by default, and only done when you explicitly ask for it - identifies the video (the identifier itself, or the segment UUID for a vote), the time range for a segment, a random identifier that is periodically rotated instead of any device identifier, and a generic User-Agent. Please also refer to the [SponsorBlock Privacy Policy](https://gist.github.com/ajayyy/aa9f8ded2b573d4f73a3ffa0ef74f796).
* **Return YouTube Dislike** ([returnyoutubedislikeapi.com](https://returnyoutubedislikeapi.com)): the "Local Return Youtube Dislike extractor" option (Settings -> Privacy, disabled by default) sends the video identifier to that service to display the dislike counter without relying on a Piped instance.
* **Search suggestions**: by default the suggestions shown while you type come from your own search history, on the device, and nothing you type is sent anywhere before you search. The "Online search suggestions" option (Settings -> Privacy, disabled by default) sends the text being typed to YouTube in the default "Full local mode", or to your instance otherwise; even then, text that looks like a link, a domain, an e-mail address or a long number (phone, card, account) is never sent, and the requests carry a generic User-Agent and no cookie.
* **Update check** ([api.github.com](https://api.github.com)): the automatic update check (enabled by default) downloads the latest release information of this project once per start of the app, or at the interval you pick (6 h, 12 h, daily, weekly). The request carries no identifier, only a generic User-Agent; like any connection, it shows your IP address to GitHub.
* **Casting to a TV (Stolm)**: only when you tap "Cast". While the device list is open, the phone asks the local network which Cast devices are there; where Android offers its own device picker, it is used instead and Watube only learns the device you choose. Watube then talks only to a device that proves, with a certificate issued by the Cast authority, that it is a genuine Cast device, over an encrypted connection. The video goes through your phone: YouTube (or, in pure Piped mode, your instance's proxy) only sees the phone, as for a video played on it, and the TV reads it from a small server the phone opens for the duration of the cast, on the Wi-Fi only, for that TV only, behind a random key renewed for every video. Only the title is sent to the TV: no artwork, account or identifier. The TV itself loads Google's media player (the Default Media Receiver) from Google: that request comes from the TV, not from the app. Between the phone and the TV the video travels unencrypted on your local network, as with any Cast app.

All of these calls are controlled from the app settings (SponsorBlock and DeArrow under Settings -> Player, the Return YouTube Dislike extractor and the instance mode under Settings -> Privacy, the update check under Settings -> Data & maintenance). Switching them off stops the sponsor badge, the DeArrow data, the dislike counter and the update check from being requested; the sponsor segment lookup itself keeps happening for every video you play or download (see above).

### On the device
* Watube contains no proprietary Google code: Google Play Services (and its Cast SDK) are not part of the app, and the app never connects to them. Casting uses Stolm, Watube's own client written from the open Cast protocol (Chromium / Open Screen, BSD license).
* Nothing is sent to a cloud backup service. A local device-to-device transfer keeps your settings and library (never the account token), and the in-app export remains the way to save a copy.
* No diagnostic data is kept: release versions write nothing to the system log and record no crash report; crash traces only exist in development (debug) builds.
* The "Block screenshots" option (Settings -> Privacy, disabled by default) hides every screen of the app from screenshots, screen recordings and the recent apps preview.
* The only web page the app shows is a bundled one: network loads are blocked in it, and the WebView's Safe Browsing checks and usage metrics are turned off.
* Network connections use TLS 1.2 or 1.3 only; plain http is refused, apart from self-hosted instances running on the device itself and the video served to your TV while casting (see above).

### Instance list source
Watube does not download any public list of instances. The list offered in Settings is built from the instances you add yourself, which are stored locally in the app database, plus the instance currently selected. Until you change it, the app uses [pipedapi.kavin.rocks](https://pipedapi.kavin.rocks) as its default instance.

### Instances & Data handling
In case the user is not logged in to a Piped account on any instance, user data remains private and is not shared with any third parties (the feature calls listed under [Third-party services](#third-party-services) excepted). Local subscriptions and playlists are stored on the app and not shared with Piped. Users have the option to disable the feature that records their browsing history and timestamps. This information is stored only locally and is not shared with any other Piped instances or third party services.

### Piped account
Piped accounts can be completely deleted with no history kept, providing increased security for users on the site.

## Contributing to SponsorBlock
If you decide to contribute to SponsorBlock via Watube (this is disabled by default), please acknowledge their [Privacy Policy](https://gist.github.com/ajayyy/aa9f8ded2b573d4f73a3ffa0ef74f796) and [Terms of Use](https://gist.github.com/ajayyy/9e8100f069348e0bc062641f34d6af12).

### Data Security
Watube offers two methods for receiving data from YouTube. You can either use the "Full Local Mode" to connect directly to YouTube's servers or select a Piped instance to proxy all network requests. Since requests to YouTube are made directly from your device when using "Full Local Mode", so YouTube might be able to profile users based on their IP in that case. However, a certain level of privacy is still maintained. If Watube is used with a Piped instance, trust is shifted to the instance owner. Piped instances do not log user-specific data, but there is no guarantee regarding the actions of the instance owner. In the event that logging occurs on Watube, the amount of data collected would be lower than what is typically gathered by YouTube's official app. This is because Watube does not send user behavior or other telemetry data to the server of the instance being used.
With a Piped instance (Full Local Mode off), YouTube media - thumbnails, avatars and video segments - are only loaded through the media proxy announced by that instance; while that proxy is unknown (instance down, invalid answer), they are not loaded at all rather than requested from Google. The "Local stream extraction" and "Local feed extraction" options (Settings -> Privacy, off by default) fetch the videos or the feed straight from YouTube instead, which shows your IP address to YouTube. In Full Local Mode the instance is never contacted, not even for its configuration. In both modes the player's own requests (video segments, subtitles, notification artwork) carry the same generic User-Agent as every other request, never the phone model.
When the Trends region is set to "Automatic", the "Neutral region" option (Settings -> Privacy, enabled by default) requests trending for the United Kingdom - the same country for every user - instead of your device's country. With the identifier rotation set to "Manual only", you can pick another country, which sets you apart from the other users. Your IP address stays visible to the server either way.
No matter which option you choose, Watube always only does the minimum amount of network requests needed to provide the functionality you see on your screen - nothing more and nothing less.

### Disclaimer
The Watube project is **not affiliated, authorized, or endorsed by YouTube, Google LLC, or any of its affiliates or subsidiaries**. Any intellectual property used is owned by the respective owners.

### Open Source Software
Watube is an **open source software** that is built for learning and research purposes. It is an independent fork of the upstream [LibreTube](https://github.com/libre-tube/LibreTube) project, and it is not connected to the LibreTube developers or their organization. We welcome and encourage collaboration and contributions from the community to help improve the app.

### Changes to Privacy Policy
By using the Watube app, you consent to the handling of your information as outlined in this Privacy Policy. If you have any questions or concerns about our Privacy Policy, you are free to open an issue in the project repository to ask about it.
