package com.watube.yard.constants

/**
 * keys for the shared preferences
 */
object PreferenceKeys {
    // region Piped Account
    const val TOKEN = "token"
    const val USERNAME = "username"

    const val AUTH_INSTANCE = "selectAuthInstance"
    const val AUTH_INSTANCE_TOGGLE = "auth_instance_toggle"

    const val LOGIN_REGISTER = "login_register"
    const val LOGOUT = "logout"
    const val DELETE_ACCOUNT = "delete_account"

    const val AUTH_PREF_FILE = "auth"
    // endregion

    // region Privacy
    /** Master switch: blocks SIM/network based data collection and other identifiers. */
    const val PRIVACY_HARDENING = "privacy_hardening"

    /**
     * Legacy daily rotation switch, superseded by [PRIVACY_ROTATION_FREQUENCY].
     * Still read as a fallback for installs that never migrated and still written by
     * the preference migration (see PreferenceHelper.MIGRATIONS).
     */
    const val RESET_SB_UUID_DAILY = "reset_sb_uuid_daily"

    /**
     * Shared rotation frequency ("12h" / "24h" / "manual") driving *every* rotating
     * client identifier at once: the SponsorBlock user id and the neutral region.
     * A single value keeps the identifiers in sync (Tor/Mullvad style rotation).
     */
    const val PRIVACY_ROTATION_FREQUENCY = "privacy_rotation_frequency"

    /** Watube: Tor/Mullvad-style neutral region for trending/home requests (opt-in). */
    const val PRIVACY_NEUTRAL_REGION = "privacy_neutral_region"

    /** Watube: keeps no browsing trace on the device (history, searches, positions). */
    const val TOURIST_MODE = "tourist_mode"

    /** Watube: hides every window of the app from screenshots, recordings and recent apps. */
    const val BLOCK_SCREENSHOTS = "block_screenshots"

    /** Country of the neutral region picked by the user, used while the rotation is manual. */
    const val NEUTRAL_REGION_COUNTRY = "privacy_neutral_region_country"
    // endregion

    // region SponsorBlock
    /** SponsorBlock user id: "user id" half + "cycle stamp" half. */
    const val SB_USER_ID_CYCLE = "sb_user_id_cycle"

    const val SB_USER_ID = "sb_user_id"
    const val CONTRIBUTE_TO_SB = "sb_contribute_key"

    const val SB_HIGHLIGHTS = "sb_highlights"
    // endregion

    // region Settings
    const val LANGUAGE = "language"

    const val ORIENTATION = "orientation"

    const val RESET_SETTINGS = "reset_settings"

    const val PREFERENCE_VERSION = "PREFERENCE_VERSION"
    // endregion

    // region Trends
    const val REGION = "region"
    const val TRENDING_CATEGORY = "trending_category"
    // endregion

    // region Navigation
    const val NAVBAR_ITEMS = "navbar_items"
    const val START_FRAGMENT = "start_fragment"
    // endregion

    // region Search
    const val UNLIMITED_SEARCH_HISTORY = "unlimited_search_history"

    const val PERSISTENT_SEARCH_BAR = "persistent_search_bar"
    const val SEARCH_SUGGESTIONS = "search_suggestions"

    const val SEARCH_HISTORY_TOGGLE = "search_history_toggle"

    const val CLEAR_SEARCH_HISTORY = "clear_search_history"
    // endregion

    // region Background Playback
    const val AUDIO_ONLY_MODE = "audio_only_mode"
    // endregion

    // region Appearance
    const val THEME_MODE = "theme_toggle"
    const val PURE_THEME = "pure_theme"
    const val ACCENT_COLOR = "accent_color"
    const val APP_FONT = "app_font"
    const val FONT_SCALE = "font_scale"
    const val FEED_DENSITY = "feed_density"
    const val GRID_COLUMNS_PORTRAIT = "grid"
    const val GRID_COLUMNS_LANDSCAPE = "grid_landscape"
    const val APP_ICON = "icon_change"
    // endregion

    // region Feed
    const val NEW_VIDEOS_BADGE = "new_videos_badge"

    const val HIDE_WATCHED_FROM_FEED = "hide_watched_from_feed"
    const val SHOW_UPCOMING_IN_FEED = "show_upcoming_in_feed"
    const val SELECTED_FEED_FILTERS = "filter_feed"
    const val FEED_SORT_ORDER = "sort_oder_feed"
    const val LOCAL_FEED_EXTRACTION = "local_feed_extraction"
    const val LAST_LOCAL_FEED_REFRESH_TIMESTAMP_MILLIS = "last_feed_refresh_timestamp_millis"

    // use the helper methods at PreferenceHelper to access these
    const val LAST_USER_SEEN_FEED_TIME = "last_watched_feed_time"
    const val LAST_REFRESHED_FEED_TIME = "last_refreshed_feed_time"
    // endregion

    // region Playlists
    const val PLAYLISTS_ORDER = "playlists_order"
    const val PLAYLIST_SORT_ORDER = "playlist_sort_order"
    // endregion

    // region Home
    const val HOME_TAB_CONTENT = "home_tab_content"
    // endregion

    // region Piped Instances
    const val FETCH_INSTANCE = "selectInstance"

    const val CUSTOM_INSTANCE = "customInstance"

    const val FULL_LOCAL_MODE = "full_local_mode"

    const val IMAGE_PROXY_URL = "image_proxy_url"

    const val LAST_PROXY_FETCH_TIME = "last_proxy_fetch_time"
    const val LAST_PROXY_FETCH_INSTANCE = "last_proxy_fetch_instance"
    // endregion

    // region Fullscreen
    const val AUTO_FULLSCREEN = "auto_fullscreen"

    const val FULLSCREEN_ORIENTATION = "fullscreen_orientation"

    const val AUTO_FULLSCREEN_SHORTS = "auto_fullscreen_shorts"

    const val FULLSCREEN_GESTURES = "fullscreen_gestures"
    // endregion

    // region Autoplay
    const val AUTOPLAY = "autoplay"

    const val AUTOPLAY_COUNTDOWN = "autoplay_countdown"
    // endregion

    // region Related Videos
    const val RELATED_STREAMS = "related_streams_toggle"
    // endregion

    // region Playback Options
    const val PLAYBACK_SPEED = "playback_speed"

    const val DEFAULT_RESOLUTION = "default_res"
    const val DEFAULT_RESOLUTION_MOBILE = "default_res_mobile"
    const val BUFFERING_GOAL = "buffering_goal"
    const val PLAYER_AUDIO_QUALITY = "player_audio_quality"
    const val PLAYER_AUDIO_QUALITY_MOBILE = "player_audio_quality_mobile"

    const val PLAYER_RESIZE_MODE = "current_player_resize_mode"

    const val SKIP_SILENCE = "skip_silence"

    const val PLAY_AUTOMATICALLY = "play_automatically"
    // endregion

    // region Video Player
    const val PAUSE_ON_SCREEN_OFF = "pause_screen_off"

    const val SEEK_INCREMENT = "seek_increment"

    const val SHOW_TIME_LEFT = "show_time_left"
    // endregion

    // region Watch Positions
    const val WATCH_POSITIONS = "watch_positions"

    const val CLEAR_WATCH_POSITIONS = "clear_watch_positions"
    // endregion

    // region Watch History
    const val WATCH_HISTORY_TOGGLE = "watch_history_toggle"

    const val CLEAR_WATCH_HISTORY = "clear_watch_history"

    const val SELECTED_HISTORY_STATUS_FILTER = "filter_history_status"
    /** Automatic retention of the watch history in days, 0 = keep it forever. */
    const val HISTORY_RETENTION_DAYS = "history_retention_days"
    // endregion

    // region Captions
    const val SYSTEM_CAPTION_STYLE = "system_caption_style"
    const val CAPTION_SETTINGS = "caption_settings"
    const val RICH_CAPTION_RENDERING = "rich_caption_rendering"

    const val DEFAULT_SUBTITLE = "default_subtitle"

    const val CAPTIONS_SIZE = "captions_size"
    // endregion

    // region Player Gestures
    const val PLAYER_SWIPE_CONTROLS = "player_swipe_controls"
    const val PLAYER_PINCH_CONTROL = "player_pinch_control"

    const val DOUBLE_TAP_TO_SEEK = "double_tap_seek"
    const val LONG_PRESS_FAST_FORWARD = "long_press_fast_forward"
    // endregion

    // region Picture-in-Picture
    const val AUTO_PIP = "auto_pip"

    const val ALTERNATIVE_PIP_CONTROLS = "alternative_pip_controls"
    // endregion

    // region Sleep Timer
    const val SLEEP_TIMER = "sleep_timer"
    const val SLEEP_TIMER_ALL_VIDEOS = "sleep_timer_all_videos"
    const val SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
    // endregion

    // region Rest Mode
    const val REST_MODE_SCHEDULE = "rest_mode_schedule"
    const val REST_MODE_DAYS = "rest_mode_days"
    const val REST_MODE_START = "rest_mode_start"
    const val REST_MODE_END = "rest_mode_end"
    // endregion

    // region Stats for Nerds
    const val STATS_FOR_NERDS = "stats_for_nerds"
    // endregion

    // region Playing Queue
    const val REPEAT_MODE = "repeat_mode"
    // endregion

    // region DeArrow
    const val CONTRIBUTE_TO_DEARROW = "dearrow_contribute_key"
    const val DEARROW = "dearrow"
    // endregion

    // region New Video Notifications
    const val NOTIFICATION_ENABLED = "notification_toggle"
    const val SHORTS_NOTIFICATIONS = "shorts_notifications"
    const val CHECKING_FREQUENCY = "checking_frequency"
    const val REQUIRED_NETWORK = "required_network"
    const val IGNORED_NOTIFICATION_CHANNELS = "ignored_notification_channels"
    const val NOTIFICATION_TIME_ENABLED = "notification_time"
    const val NOTIFICATION_START_TIME = "notification_start_time"
    const val NOTIFICATION_END_TIME = "notification_end_time"
    // ids of the latest videos a notification was posted for, newest first (deduplication)
    const val NOTIFIED_VIDEO_IDS = "notified_video_ids"
    // version of the notification work request scheduled by this install
    const val NOTIFICATION_WORK_SPEC_VERSION = "notification_work_spec_version"
    // endregion

    // region Updates
    const val AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
    /** Minimum delay (in hours, as a string) between two automatic update checks; "0" = every launch. */
    const val UPDATE_CHECK_INTERVAL = "update_check_interval"

    const val LAST_SHOWN_INFO_MESSAGE_VERSION_CODE = "last_shown_info_message_version"

    const val LAST_UPDATE_CHECK_TIME = "last_update_check_time"
    // endregion

    // region Data Saver
    const val DATA_SAVER_MODE = "data_saver_mode_key"
    // endregion

    // region Share
    const val SHARE_WITH_TIME_CODE = "share_with_time_code"
    // endregion

    // region Playlist Bookmarks
    const val CLEAR_BOOKMARKS = "clear_bookmarks"
    // endregion

    // region Downloads
    const val EXTERNAL_DOWNLOAD_PROVIDER = "external_download_provider"

    const val SELECTED_DOWNLOAD_SORT_TYPE = "selected_download_sort_type"
    const val SELECTED_DOWNLOAD_PLAYLIST_SORT_TYPE = "selected_download_playlist_sort_type"
    // endregion

    // region Return YouTube Dislike
    const val LOCAL_RYD = "local_return_youtube_dislikes"
    // endregion

    // region Local Extraction
    const val LOCAL_STREAM_EXTRACTION = "local_stream_extraction"
    // endregion

    // region Backup & Restore
    const val INCLUDE_TIMESTAMP_IN_BACKUP_FILENAME = "include_timestamp_in_filename"
    // endregion

    // region Crash Reports
    const val ERROR_LOG = "error_log"
    // endregion

    // region Subscriptions
    const val SELECTED_CHANNEL_GROUP = "selected_channel_group"
    // endregion
}
