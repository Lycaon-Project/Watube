# Watube features

Every piece of Watube's code sits under the name of the feature it belongs to, like the chapters
of a book: search for a feature's name and you land on its code.

```kotlin
// region Share
fun shareVideo() { … }
// endregion
```

- **Find a feature**: search the project for `region <Name>` (Android Studio: Ctrl+Shift+F,
  e.g. `region Share`). Android Studio also folds each region, and the names show in the folded
  code. The same markers are in the layouts and settings screens (`<!-- region Share -->`), the
  manifest, the R8 rules and the build file (`# region`, `// region`).
- **Kotlin files** keep the declarations of one feature together. Properties and `init` blocks
  never move (Kotlin initializes them in the order they are written), so a large class can show
  the same feature twice: once among its properties, once with its functions.
- **XML files** are marked where the elements are: their order is what appears on screen.
- **New code** goes in the region of its feature. A new feature takes a name from the table below,
  or a new row is added to it. `FeatureRegionsTest` (unit tests) fails when a Kotlin file has no
  region, when a region name is not in this table, or when regions are unbalanced.
- Texts (`strings.xml`, 75 languages) are not marked: their order is shared with the
  translation platform. Their names usually start with the feature (`stolm_…`, `sb_…`).

## Watching

| Feature | What it covers | Main files |
|---|---|---|
| **Video Player** | The player itself: the playback services, the player screen, its controls and seek bar. | [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt), [CustomExoPlayerView.kt](app/src/main/java/com/watube/yard/ui/views/CustomExoPlayerView.kt), [AbstractPlayerService.kt](app/src/main/java/com/watube/yard/services/AbstractPlayerService.kt) |
| **Stream Sources** | Turning extracted streams into something the player reads: DASH manifests, HLS parsing, track selection. | [DashHelper.kt](app/src/main/java/com/watube/yard/helpers/DashHelper.kt), [OnlinePlayerService.kt](app/src/main/java/com/watube/yard/services/OnlinePlayerService.kt), [DefaultTrackSelectorWithAudioQualitySupport.kt](app/src/main/java/com/watube/yard/util/DefaultTrackSelectorWithAudioQualitySupport.kt) |
| **SABR Streaming** | YouTube's server-driven streaming protocol, played and downloaded without a third party. | [SabrClient.kt](app/src/main/java/com/watube/yard/player/parser/SabrClient.kt), [DefaultSabrChunkSource.kt](app/src/main/java/com/watube/yard/player/DefaultSabrChunkSource.kt), [SabrMediaPeriod.kt](app/src/main/java/com/watube/yard/player/SabrMediaPeriod.kt) |
| **Live Streams** | Live videos: HLS live playback and its live-edge handling. | [YouTubeHlsPlaylistParser.kt](app/src/main/java/com/watube/yard/util/YouTubeHlsPlaylistParser.kt) |
| **Fullscreen** | Entering and leaving fullscreen, screen orientation, cutouts. | [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt), [CustomExoPlayerView.kt](app/src/main/java/com/watube/yard/ui/views/CustomExoPlayerView.kt), [FullscreenGestureAnimationController.kt](app/src/main/java/com/watube/yard/ui/controllers/FullscreenGestureAnimationController.kt) |
| **Mini Player** | The collapsed player at the bottom of the screen and its transitions. | [SingleViewTouchableMotionLayout.kt](app/src/main/java/com/watube/yard/ui/views/SingleViewTouchableMotionLayout.kt), [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt), [player_scene.xml](app/src/main/res/xml/player_scene.xml) |
| **Picture-in-Picture** | The floating video window. | [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt), [PlayerHelper.kt](app/src/main/java/com/watube/yard/helpers/PlayerHelper.kt), [PictureInPictureParamsCompat.kt](app/src/main/java/com/watube/yard/compat/PictureInPictureParamsCompat.kt) |
| **Background Playback** | Audio-only playback and its screen. | [AudioPlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/AudioPlayerFragment.kt), [fragment_audio_player.xml](app/src/main/res/layout/fragment_audio_player.xml), [BackgroundHelper.kt](app/src/main/java/com/watube/yard/helpers/BackgroundHelper.kt) |
| **Media Notification** | The playback notification and media session (lock screen, headsets, other apps). | [AbstractPlayerService.kt](app/src/main/java/com/watube/yard/services/AbstractPlayerService.kt), [SetMetadata.kt](app/src/main/java/com/watube/yard/extensions/SetMetadata.kt), [NowPlayingNotification.kt](app/src/main/java/com/watube/yard/util/NowPlayingNotification.kt) |
| **Player Gestures** | Double tap to seek, swipes for brightness and volume, pinch to zoom. | [CustomExoPlayerView.kt](app/src/main/java/com/watube/yard/ui/views/CustomExoPlayerView.kt), [PlayerGestureController.kt](app/src/main/java/com/watube/yard/ui/listeners/PlayerGestureController.kt), [player_gesture_controls_view.xml](app/src/main/res/layout/player_gesture_controls_view.xml) |
| **Playback Options** | Speed, pitch, quality, audio tracks, skip silence and the other options of the player. | [CustomExoPlayerView.kt](app/src/main/java/com/watube/yard/ui/views/CustomExoPlayerView.kt), [PlayerHelper.kt](app/src/main/java/com/watube/yard/helpers/PlayerHelper.kt), [playback_bottom_sheet.xml](app/src/main/res/layout/playback_bottom_sheet.xml) |
| **Captions** | Subtitles: tracks, choice and display. | [PlayerHelper.kt](app/src/main/java/com/watube/yard/helpers/PlayerHelper.kt), [CustomExoPlayerView.kt](app/src/main/java/com/watube/yard/ui/views/CustomExoPlayerView.kt), [TranslationSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/TranslationSheet.kt) |
| **Chapters** | Video chapters: list, seek bar marks and the current chapter label. | [chapters_row.xml](app/src/main/res/layout/chapters_row.xml), [ChaptersBottomSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/ChaptersBottomSheet.kt), [ChaptersAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/ChaptersAdapter.kt) |
| **Seekbar Preview** | Thumbnails shown while seeking. | [SeekbarPreviewListener.kt](app/src/main/java/com/watube/yard/ui/listeners/SeekbarPreviewListener.kt), [OnlineTimeFrameReceiver.kt](app/src/main/java/com/watube/yard/util/OnlineTimeFrameReceiver.kt), [exo_styled_player_control_view.xml](app/src/main/res/layout/exo_styled_player_control_view.xml) |
| **Playing Queue** | The queue of videos to play and its sheet. | [PlayingQueue.kt](app/src/main/java/com/watube/yard/util/PlayingQueue.kt), [PlayingQueueSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/PlayingQueueSheet.kt), [queue_bottom_sheet.xml](app/src/main/res/layout/queue_bottom_sheet.xml) |
| **Autoplay** | Playing the next video automatically, with its countdown. | [AutoplayCountdownView.kt](app/src/main/java/com/watube/yard/ui/views/AutoplayCountdownView.kt), [autoplay_countdown.xml](app/src/main/res/layout/autoplay_countdown.xml), [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt) |
| **Related Videos** | The videos suggested under the player. | [fragment_player.xml](app/src/main/res/layout-land/fragment_player.xml), [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt), [fragment_player.xml](app/src/main/res/layout/fragment_player.xml) |
| **Video Details** | Title, description, links, tags and channel row under the player. | [description_layout.xml](app/src/main/res/layout/description_layout.xml), [DescriptionLayout.kt](app/src/main/java/com/watube/yard/ui/views/DescriptionLayout.kt), [HtmlParser.kt](app/src/main/java/com/watube/yard/util/HtmlParser.kt) |
| **Comments** | Comments and their replies. | [comments_row.xml](app/src/main/res/layout/comments_row.xml), [CommentsMainFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/CommentsMainFragment.kt), [CommentsPagingAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/CommentsPagingAdapter.kt) |
| **Screenshot** | Saving the current video frame as an image. | [PlayerFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlayerFragment.kt), [fragment_player.xml](app/src/main/res/layout/fragment_player.xml), [fragment_player.xml](app/src/main/res/layout-land/fragment_player.xml) |
| **Stats for Nerds** | Technical playback statistics. | [dialog_stats.xml](app/src/main/res/layout/dialog_stats.xml), [PlayerHelper.kt](app/src/main/java/com/watube/yard/helpers/PlayerHelper.kt), [StatsSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/StatsSheet.kt) |
| **Sleep Timer** | Stopping playback after a set time. | [SleepTimer.kt](app/src/main/java/com/watube/yard/ui/tools/SleepTimer.kt), [sleep_timer_sheet.xml](app/src/main/res/layout/sleep_timer_sheet.xml), [SleepTimerSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/SleepTimerSheet.kt) |
| **Rest Mode** | Scheduled hours during which nothing plays. | [RestMode.kt](app/src/main/java/com/watube/yard/ui/tools/RestMode.kt), [PlayerSettings.kt](app/src/main/java/com/watube/yard/ui/preferences/PlayerSettings.kt), [player_settings.xml](app/src/main/res/xml/player_settings.xml) |
| **Watch Positions** | Resuming videos where they were left, and the watched progress bars. | [DatabaseHelper.kt](app/src/main/java/com/watube/yard/db/DatabaseHelper.kt), [SetWatchProgressLength.kt](app/src/main/java/com/watube/yard/ui/extensions/SetWatchProgressLength.kt), [PlayerHelper.kt](app/src/main/java/com/watube/yard/helpers/PlayerHelper.kt) |
| **Stolm** | Watube's own Cast client: sending the video to a TV. | [CastMediaServer.kt](app/src/main/java/com/watube/yard/cast/CastMediaServer.kt), [StolmPlayer.kt](app/src/main/java/com/watube/yard/cast/StolmPlayer.kt), [CastSession.kt](app/src/main/java/com/watube/yard/cast/CastSession.kt) |

## Content

| Feature | What it covers | Main files |
|---|---|---|
| **Home** | The home screen. | [fragment_home.xml](app/src/main/res/layout/fragment_home.xml), [HomeFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/HomeFragment.kt), [HomeViewModel.kt](app/src/main/java/com/watube/yard/ui/models/HomeViewModel.kt) |
| **Trends** | The trending videos screen. | [TrendsFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/TrendsFragment.kt), [fragment_trends.xml](app/src/main/res/layout/fragment_trends.xml), [TrendsViewModel.kt](app/src/main/java/com/watube/yard/ui/models/TrendsViewModel.kt) |
| **Search** | Search, suggestions, search history and result filters. | [fragment_search_result.xml](app/src/main/res/layout/fragment_search_result.xml), [SearchResultsAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/SearchResultsAdapter.kt), [SearchSuggestionsFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/SearchSuggestionsFragment.kt) |
| **Channels** | Channel screens, tabs and channel options. | [ChannelFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/ChannelFragment.kt), [fragment_channel.xml](app/src/main/res/layout/fragment_channel.xml), [ChannelContentFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/ChannelContentFragment.kt) |
| **Subscriptions** | Subscribing, the subscriptions list and channel groups. | [SubscriptionsBottomSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/SubscriptionsBottomSheet.kt), [EditChannelGroupSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/EditChannelGroupSheet.kt), [sheet_subscriptions.xml](app/src/main/res/layout/sheet_subscriptions.xml) |
| **Feed** | The subscriptions feed, its filters and sorting. | [SubscriptionsFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/SubscriptionsFragment.kt), [fragment_subscriptions.xml](app/src/main/res/layout/fragment_subscriptions.xml), [LocalFeedRepository.kt](app/src/main/java/com/watube/yard/repo/LocalFeedRepository.kt) |
| **New Video Notifications** | Background check of subscriptions for new videos, and its notifications. | [NotificationWorker.kt](app/src/main/java/com/watube/yard/workers/NotificationWorker.kt), [NotificationHelper.kt](app/src/main/java/com/watube/yard/helpers/NotificationHelper.kt), [notification_settings.xml](app/src/main/res/xml/notification_settings.xml) |
| **Playlists** | Local and Piped playlists: screens, editing, adding videos. | [PlaylistFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/PlaylistFragment.kt), [fragment_playlist.xml](app/src/main/res/layout/fragment_playlist.xml), [PlaylistOptionsBottomSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/PlaylistOptionsBottomSheet.kt) |
| **Playlist Bookmarks** | Saved playlists of other people. | [PlaylistBookmarkAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/PlaylistBookmarkAdapter.kt), [PlaylistBookmarkDao.kt](app/src/main/java/com/watube/yard/db/dao/PlaylistBookmarkDao.kt), [PlaylistBookmark.kt](app/src/main/java/com/watube/yard/db/obj/PlaylistBookmark.kt) |
| **Watch History** | The history of watched videos. | [WatchHistoryFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/WatchHistoryFragment.kt), [fragment_watch_history.xml](app/src/main/res/layout/fragment_watch_history.xml), [WatchHistoryAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/WatchHistoryAdapter.kt) |
| **Library** | The library screen. | [LibraryFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/LibraryFragment.kt), [fragment_library.xml](app/src/main/res/layout/fragment_library.xml), [watube_shortcut_tile.xml](app/src/main/res/layout/watube_shortcut_tile.xml) |
| **Downloads** | Downloading videos, audio and subtitles, and the downloads screen. | [DownloadService.kt](app/src/main/java/com/watube/yard/services/DownloadService.kt), [DownloadsFragment.kt](app/src/main/java/com/watube/yard/ui/fragments/DownloadsFragment.kt), [DownloadDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/DownloadDialog.kt) |
| **Offline Playback** | Playing downloaded videos. | [OfflinePlayerService.kt](app/src/main/java/com/watube/yard/services/OfflinePlayerService.kt), [dialog_play_offline.xml](app/src/main/res/layout/dialog_play_offline.xml), [PlayOfflineDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/PlayOfflineDialog.kt) |
| **Share** | Sharing links, copying them and opening videos in another app. | [ShareDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/ShareDialog.kt), [IntentHelper.kt](app/src/main/java/com/watube/yard/helpers/IntentHelper.kt), [dialog_share.xml](app/src/main/res/layout/dialog_share.xml) |
| **Video Menu** | The options of a video (long press or menu button): queue, playlist, download, share... | [VideoOptionsBottomSheet.kt](app/src/main/java/com/watube/yard/ui/sheets/VideoOptionsBottomSheet.kt) |

## Third-party services

| Feature | What it covers | Main files |
|---|---|---|
| **SponsorBlock** | Skipping sponsored segments, with SponsorBlock data and submissions. | [sponsorblock_settings.xml](app/src/main/res/xml/sponsorblock_settings.xml), [SubmitSegmentDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/SubmitSegmentDialog.kt), [dialog_submit_segment.xml](app/src/main/res/layout/dialog_submit_segment.xml) |
| **DeArrow** | Community titles and thumbnails, and their submissions. | [SubmitDeArrowDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/SubmitDeArrowDialog.kt), [dialog_submit_dearrow.xml](app/src/main/res/layout/dialog_submit_dearrow.xml), [DeArrowUtil.kt](app/src/main/java/com/watube/yard/util/DeArrowUtil.kt) |
| **Return YouTube Dislike** | The dislike counter. | [VoteInfo.kt](app/src/main/java/com/watube/yard/api/obj/VoteInfo.kt), [ExternalApi.kt](app/src/main/java/com/watube/yard/api/ExternalApi.kt), [PlayerHelper.kt](app/src/main/java/com/watube/yard/helpers/PlayerHelper.kt) |

## Sources and accounts

| Feature | What it covers | Main files |
|---|---|---|
| **Local Extraction** | Reading YouTube directly on the device with NewPipeExtractor. | [NewPipeMediaServiceRepository.kt](app/src/main/java/com/watube/yard/api/NewPipeMediaServiceRepository.kt), [NewPipeExtractorInstance.kt](app/src/main/java/com/watube/yard/helpers/NewPipeExtractorInstance.kt), [NewPipeDownloaderImpl.kt](app/src/main/java/com/watube/yard/util/NewPipeDownloaderImpl.kt) |
| **Piped Instances** | Piped servers: instance choice, custom instances, the instance API and its media proxy. | [InstanceSettings.kt](app/src/main/java/com/watube/yard/ui/preferences/InstanceSettings.kt), [ProxyHelper.kt](app/src/main/java/com/watube/yard/helpers/ProxyHelper.kt), [InstancesAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/InstancesAdapter.kt) |
| **Piped Account** | Logging in to a Piped instance, the account token and its deletion. | [LoginDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/LoginDialog.kt), [TokenCipher.kt](app/src/main/java/com/watube/yard/helpers/TokenCipher.kt), [PreferenceHelper.kt](app/src/main/java/com/watube/yard/helpers/PreferenceHelper.kt) |

## App

| Feature | What it covers | Main files |
|---|---|---|
| **App Startup** | Starting the app: process-wide setup and clean-up of older versions. | [WatubeApp.kt](app/src/main/java/com/watube/yard/WatubeApp.kt), [AndroidManifest.xml](app/src/main/AndroidManifest.xml) |
| **Navigation** | Moving between screens: navigation bar, rail, routes and back handling. | [MainActivity.kt](app/src/main/java/com/watube/yard/ui/activities/MainActivity.kt), [NavBarHelper.kt](app/src/main/java/com/watube/yard/helpers/NavBarHelper.kt), [activity_main.xml](app/src/main/res/layout/activity_main.xml) |
| **Deep Links** | Opening YouTube links and intents from other apps. | [MainActivity.kt](app/src/main/java/com/watube/yard/ui/activities/MainActivity.kt), [AndroidManifest.xml](app/src/main/AndroidManifest.xml), [RouterActivity.kt](app/src/main/java/com/watube/yard/ui/activities/RouterActivity.kt) |
| **Settings** | The settings screens and the stored preferences. | [PreferenceHelper.kt](app/src/main/java/com/watube/yard/helpers/PreferenceHelper.kt), [nav_settings.xml](app/src/main/res/navigation/nav_settings.xml), [BasePreferenceFragment.kt](app/src/main/java/com/watube/yard/ui/base/BasePreferenceFragment.kt) |
| **Appearance** | Theme, colours, app icon and layout density. | [AndroidManifest.xml](app/src/main/AndroidManifest.xml), [ColorPickerDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/ColorPickerDialog.kt), [MainActivity.kt](app/src/main/java/com/watube/yard/ui/activities/MainActivity.kt) |
| **Privacy** | Privacy protections: neutral identifiers, screenshot blocking, private keyboard. | [PrivacyHelper.kt](app/src/main/java/com/watube/yard/helpers/PrivacyHelper.kt), [PrivacySettings.kt](app/src/main/java/com/watube/yard/ui/preferences/PrivacySettings.kt), [privacy_settings.xml](app/src/main/res/xml/privacy_settings.xml) |
| **Backup & Restore** | Exporting and importing the library and the settings. | [BackupHelper.kt](app/src/main/java/com/watube/yard/helpers/BackupHelper.kt), [ImportHelper.kt](app/src/main/java/com/watube/yard/helpers/ImportHelper.kt), [BackupRestoreSettings.kt](app/src/main/java/com/watube/yard/ui/preferences/BackupRestoreSettings.kt) |
| **Updates** | Checking for new releases. | [UpdateChecker.kt](app/src/main/java/com/watube/yard/util/UpdateChecker.kt), [UpdateAvailableDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/UpdateAvailableDialog.kt), [MainActivity.kt](app/src/main/java/com/watube/yard/ui/activities/MainActivity.kt) |
| **Crash Reports** | Crash traces, kept in development builds only. | [PreferenceHelper.kt](app/src/main/java/com/watube/yard/helpers/PreferenceHelper.kt), [ExceptionHandler.kt](app/src/main/java/com/watube/yard/util/ExceptionHandler.kt), [ErrorDialog.kt](app/src/main/java/com/watube/yard/ui/dialogs/ErrorDialog.kt) |
| **About** | The about, help and mission screens and the licences. | [AboutActivity.kt](app/src/main/java/com/watube/yard/ui/activities/AboutActivity.kt), [activity_about.xml](app/src/main/res/layout/activity_about.xml), [activity_help.xml](app/src/main/res/layout/activity_help.xml) |
| **No Internet Screen** | What is shown without a connection. | [fragment_nointernet.xml](app/src/main/res/layout/fragment_nointernet.xml), [NoInternetActivity.kt](app/src/main/java/com/watube/yard/ui/activities/NoInternetActivity.kt), [activity_nointernet.xml](app/src/main/res/layout/activity_nointernet.xml) |
| **App Shortcuts** | Launcher shortcuts. | [ShortcutHelper.kt](app/src/main/java/com/watube/yard/helpers/ShortcutHelper.kt) |

## Foundations

| Feature | What it covers | Main files |
|---|---|---|
| **Build** | Compiling and shrinking the app: Gradle setup and R8 rules. | [build.gradle.kts](app/build.gradle.kts), [proguard-rules.pro](app/proguard-rules.pro) |
| **Networking** | HTTP clients, User-Agent, JSON and network state. | [RetrofitInstance.kt](app/src/main/java/com/watube/yard/api/RetrofitInstance.kt), [NetworkHelper.kt](app/src/main/java/com/watube/yard/helpers/NetworkHelper.kt), [SharedHttpClient.kt](app/src/main/java/com/watube/yard/util/SharedHttpClient.kt) |
| **Image Loading** | Loading, caching and showing images. | [ZoomableImageView.kt](app/src/main/java/com/watube/yard/ui/views/ZoomableImageView.kt), [ImageHelper.kt](app/src/main/java/com/watube/yard/helpers/ImageHelper.kt), [ZoomableImageActivity.kt](app/src/main/java/com/watube/yard/ui/activities/ZoomableImageActivity.kt) |
| **Data Saver** | Reducing data use on metered networks. | [DataSaverMode.kt](app/src/main/java/com/watube/yard/util/DataSaverMode.kt), [general_settings.xml](app/src/main/res/xml/general_settings.xml), [PreferenceKeys.kt](app/src/main/java/com/watube/yard/constants/PreferenceKeys.kt) |
| **Database** | The local database and its access. | [DatabaseHolder.kt](app/src/main/java/com/watube/yard/db/DatabaseHolder.kt), [Converters.kt](app/src/main/java/com/watube/yard/db/Converters.kt) |
| **UI Components** | Reusable views, sheets, dialogs and UI helpers. | [video_row.xml](app/src/main/res/layout/video_row.xml), [VideoCardsAdapter.kt](app/src/main/java/com/watube/yard/ui/adapters/VideoCardsAdapter.kt), [trending_row.xml](app/src/main/res/layout/trending_row.xml) |
| **Common Utilities** | General-purpose Kotlin and Android helpers. | [TextUtils.kt](app/src/main/java/com/watube/yard/util/TextUtils.kt), [Bundle.kt](app/src/main/java/com/watube/yard/extensions/Bundle.kt), [ResettableLazy.kt](app/src/main/java/com/watube/yard/api/ResettableLazy.kt) |

