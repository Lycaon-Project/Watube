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
* **Update check** ([api.github.com](https://api.github.com)): if the automatic update check is enabled (disabled by default), the app downloads the latest release information of this project. No personal data is sent.

All of these calls are controlled from the app settings (SponsorBlock and DeArrow under Settings -> SponsorBlock, the Return YouTube Dislike extractor and the instance mode under Settings -> Privacy, the update check under Settings -> General). Switching them off stops the sponsor badge, the DeArrow data, the dislike counter and the update check from being requested; the sponsor segment lookup itself keeps happening for every video you play or download (see above).

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
No matter which option you choose, Watube always only does the minimum amount of network requests needed to provide the functionality you see on your screen - nothing more and nothing less.

### Disclaimer
The Watube project is **not affiliated, authorized, or endorsed by YouTube, Google LLC, or any of its affiliates or subsidiaries**. Any intellectual property used is owned by the respective owners.

### Open Source Software
Watube is an **open source software** that is built for learning and research purposes. It is an independent fork of the upstream [LibreTube](https://github.com/libre-tube/LibreTube) project, and it is not connected to the LibreTube developers or their organization. We welcome and encourage collaboration and contributions from the community to help improve the app.

### Changes to Privacy Policy
By using the Watube app, you consent to the handling of your information as outlined in this Privacy Policy. If you have any questions or concerns about our Privacy Policy, you are free to open an issue in the project repository to ask about it.
