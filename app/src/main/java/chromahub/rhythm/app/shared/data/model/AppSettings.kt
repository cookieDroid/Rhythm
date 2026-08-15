package chromahub.rhythm.app.shared.data.model

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import chromahub.rhythm.app.R
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.worker.BackupWorker
import chromahub.rhythm.app.worker.RhythmPulseNotificationWorker
import chromahub.rhythm.app.worker.UpdateNotificationWorker
import chromahub.rhythm.app.BuildConfig
import java.io.File
import java.util.Date // Import Date for timestamp
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * Data class to represent a single crash log entry
 */
data class CrashLogEntry(
    val timestamp: Long,
    val log: String
)

/**
 * Enum for album view types in the library
 */
enum class AlbumViewType {
    LIST, GRID
}

/**
 * Enum for artist view types in the library
 */
enum class ArtistViewType {
    LIST, GRID
}

/**
 * Enum for playlist view types in the library
 */
enum class PlaylistViewType {
    LIST, GRID
}

/**
 * Enum for artist artwork source preferences
 */
enum class ArtistArtworkSource {
    PREFER_LOCAL_THEN_API,
    LOCAL_ONLY,
    API_ONLY,
    DISABLED;

    companion object {
        fun fromName(name: String?): ArtistArtworkSource {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: PREFER_LOCAL_THEN_API
        }
    }
}

data class RhythmAuraPolicyBand(
    val minAge: Int,
    val maxAge: Int,
    val maxVolumeThreshold: Float,
    val recommendedDailyMinutes: Int,
    val stopPlaybackOnZeroVolume: Boolean,
    val enforceHapticFeedback: Boolean
)

typealias RhythmGuardPolicyBand = RhythmAuraPolicyBand

private val RHYTHM_AURA_POLICY_BANDS = listOf(
    RhythmAuraPolicyBand(8, 12, 0.50f, 40, true, true),
    RhythmAuraPolicyBand(13, 15, 0.56f, 55, true, true),
    RhythmAuraPolicyBand(16, 17, 0.60f, 70, true, true),
    RhythmAuraPolicyBand(18, 25, 0.68f, 95, false, false),
    RhythmAuraPolicyBand(26, 40, 0.72f, 120, false, false),
    RhythmAuraPolicyBand(41, 55, 0.70f, 105, false, false),
    RhythmAuraPolicyBand(56, 80, 0.65f, 90, true, true)
)

private val RHYTHM_GUARD_POLICY_BANDS = RHYTHM_AURA_POLICY_BANDS


/**
 * Priority order for lyrics APIs
 */
enum class LyricsApiPriority(val displayName: String) {
    LYRICALLY_FIRST("Lyrically"),
    LRCLIB_FIRST("LRCLib"),
    BETTERLYRICS_FIRST("Better Lyrics");

    companion object {
        fun fromOrdinal(ordinal: Int): LyricsApiPriority {
            return values().getOrElse(ordinal) { LYRICALLY_FIRST }
        }
    }
}

/**
 * Singleton class to manage all app settings using SharedPreferences
 */
class AppSettings private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "rhythm_preferences"
        
        // Playback Settings
        private const val KEY_GAPLESS_PLAYBACK = "gapless_playback"
        private const val KEY_CROSSFADE = "crossfade"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_CROSSFADE_REPEAT_ONE = "crossfade_repeat_one"
        private const val KEY_CROSSFADE_ON_SKIP = "crossfade_on_skip"
        private const val KEY_AUDIO_NORMALIZATION = "audio_normalization"
        private const val KEY_REPLAY_GAIN = "replay_gain"
        private const val KEY_REPLAY_GAIN_MODE = "replay_gain_mode"
        private const val KEY_REPLAY_GAIN_DRC = "replay_gain_drc"
        private const val KEY_REPLAY_GAIN_PREAMP = "replay_gain_preamp"
        private const val KEY_REPLAY_GAIN_PREAMP_UNTAGGED = "replay_gain_preamp_untagged"
        private const val KEY_SKIP_SILENCE = "skip_silence_enabled"
        private const val KEY_AUDIO_ROUTING_MODE = "audio_routing_mode" // "default", "app", "system"
        private const val KEY_RESUME_ON_DEVICE_RECONNECT = "resume_on_device_reconnect"
        private const val KEY_AUDIO_OFFLOAD_ENABLED = "audio_offload_enabled"
        
        // Battery Saver Settings
        private const val KEY_BATTERY_SAVER_ENABLED = "battery_saver_enabled"
        private const val KEY_BATTERY_SAVER_MODE = "battery_saver_mode" // "auto" or "manual"
        private const val KEY_BATTERY_SAVER_DISABLE_HAPTICS = "battery_saver_disable_haptics"
        private const val KEY_BATTERY_SAVER_ENABLE_OFFLOAD = "battery_saver_enable_offload"
        private const val KEY_BATTERY_SAVER_DISABLE_MARQUEE = "battery_saver_disable_marquee"
        private const val KEY_BATTERY_SAVER_DISABLE_LOSSLESS_ARTWORK = "battery_saver_disable_lossless_artwork"
        private const val KEY_BATTERY_SAVER_DISABLE_AUTO_FETCH_ARTWORK = "battery_saver_disable_auto_fetch_artwork"
        private const val KEY_GLOBAL_MARQUEE_ENABLED = "global_marquee_enabled"
        
        private const val KEY_PRELOAD_LIMIT = "preload_limit"
        
        // Lyrics Settings
        private const val KEY_SHOW_LYRICS = "show_lyrics"
        private const val KEY_ONLINE_ONLY_LYRICS = "online_only_lyrics" // Deprecated, kept for migration
        private const val KEY_LYRICS_SOURCE_PREFERENCE = "lyrics_source_preference"
        private const val KEY_SHOW_LYRICS_BACKGROUND_ARTWORK = "show_lyrics_background_artwork"
        private const val KEY_SHOW_LYRICS_TRANSLATION = "show_lyrics_translation"
        private const val KEY_SHOW_LYRICS_ROMANIZATION = "show_lyrics_romanization"
        private const val KEY_KEEP_SCREEN_ON_LYRICS = "keep_screen_on_lyrics"
        private const val KEY_TAP_LYRICS_TO_FULL_SCREEN = "tap_lyrics_to_full_screen"
        private const val KEY_LYRICS_API_PRIORITY = "lyrics_api_priority"
        private const val KEY_LYRICS_API_FALLBACK_RETRY = "lyrics_api_fallback_retry"
        private const val KEY_AUTO_HIDE_LYRICS_CONTROLS = "auto_hide_lyrics_controls"
        private const val KEY_LYRIC_BOLD = "lyric_bold"
        private const val KEY_TRIM_LYRICS = "trim_lyrics"
        private const val KEY_LYRIC_NO_ANIMATION = "lyric_no_animation"
        private const val KEY_TRANSLATION_AUTO_WORD = "translation_auto_word"
        
        // Theme Settings
        private const val KEY_USE_SYSTEM_THEME = "use_system_theme"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AMOLED_THEME = "amoled_theme"
        private const val KEY_USE_DYNAMIC_COLORS = "use_dynamic_colors"
        private const val KEY_CUSTOM_COLOR_SCHEME = "custom_color_scheme"
        private const val KEY_CUSTOM_FONT = "custom_font"
        private const val KEY_COLOR_SOURCE = "color_source" // ALBUM_ART, MONET, or CUSTOM
        private const val KEY_EXTRACTED_ALBUM_COLORS = "extracted_album_colors" // JSON string with color values
        private const val KEY_FONT_SOURCE = "font_source" // SYSTEM or CUSTOM
        private const val KEY_CUSTOM_FONT_PATH = "custom_font_path" // Path to imported font file
        private const val KEY_CUSTOM_FONT_FAMILY = "custom_font_family" // Display name of custom font
        
        // Player Theme Settings
        private const val KEY_PLAYER_THEME_ID = "player_theme_id" // ID of the selected player theme (default, compact, large, minimal)
        private const val KEY_MINI_PLAYER_THEME_ID = "miniplayer_theme_id"
        private const val KEY_USE_EXPERIMENTAL_PLAYER_UI = "use_experimental_player_ui"
        private const val KEY_ENABLE_ALBUM_EDITING = "enable_album_editing"
        
        // Library Settings
        private const val KEY_ALBUM_VIEW_TYPE = "album_view_type"
        private const val KEY_ARTIST_VIEW_TYPE = "artist_view_type"
        private const val KEY_PLAYLIST_VIEW_TYPE = "playlist_view_type"
        private const val KEY_ALBUM_SORT_ORDER = "album_sort_order"
        private const val KEY_PLAYLIST_SORT_ORDER = "playlist_sort_order"
        private const val KEY_PLAYLIST_DETAIL_SORT_ORDER = "playlist_detail_sort_order"
        private const val KEY_ARTIST_COLLABORATION_MODE = "artist_collaboration_mode"
        private const val KEY_LIBRARY_TAB_ORDER = "library_tab_order"
        private const val KEY_LIBRARY_COMBINE_DISCS = "library_combine_discs"
        private const val KEY_PLAYER_CHIP_ORDER = "player_chip_order"
        private const val KEY_HIDDEN_LIBRARY_TABS = "hidden_library_tabs"
        private const val KEY_HIDDEN_PLAYER_CHIPS = "hidden_player_chips"
        private const val KEY_LYRICALLY_SOURCES_ORDER = "lyrically_sources_order"
        private const val KEY_DISABLED_LYRICALLY_SOURCES = "disabled_lyrically_sources"
        private const val KEY_GROUP_BY_ALBUM_ARTIST = "group_by_album_artist" // New setting for album artist grouping
        private const val KEY_PREFER_SONG_ARTWORK = "prefer_song_artwork" // Prefer per-song embedded artwork over shared album art
        private const val KEY_IGNORE_MEDIASTORE_COVERS = "ignore_mediastore_covers" // Legacy key kept for migration compatibility
        private const val KEY_LOSSLESS_ARTWORK = "lossless_artwork" // Show cover art without downscaling/compression
        private const val KEY_SHOW_LIBRARY_SECTION_HEADERS = "show_library_section_headers"
        private const val KEY_SHOW_LIBRARY_BOTTOM_BAR_ALWAYS = "show_library_bottom_bar_always"
        
        // Audio Device Settings
        private const val KEY_LAST_AUDIO_DEVICE = "last_audio_device"
        private const val KEY_AUTO_CONNECT_DEVICE = "auto_connect_device"
        private const val KEY_USE_SYSTEM_VOLUME = "use_system_volume"
        private const val KEY_STOP_PLAYBACK_ON_ZERO_VOLUME = "stop_playback_on_zero_volume"
        private const val KEY_DISMISSED_AUTOEQ_SUGGESTIONS = "dismissed_autoeq_suggestions"
        
        // Equalizer Settings
        private const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_EQUALIZER_BAND_LEVELS = "equalizer_band_levels"
        private const val KEY_AUTOEQ_PROFILE = "autoeq_profile"
        private const val KEY_USER_AUDIO_DEVICES = "user_audio_devices"
        private const val KEY_ACTIVE_AUDIO_DEVICE_ID = "active_audio_device_id"
        private const val KEY_BASS_BOOST_ENABLED = "bass_boost_enabled"
        private const val KEY_BASS_BOOST_STRENGTH = "bass_boost_strength"
        private const val KEY_BASS_BOOST_AVAILABLE = "bass_boost_available"
        private const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
        private const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
        private const val KEY_MONO_AUDIO_ENABLED = "mono_audio_enabled"
        
        // DAC Support Settings (Experimental)
        private const val KEY_DAC_SUPPORT_ENABLED = "dac_support_enabled"
        private const val KEY_DAC_BIT_PERFECT_MODE = "dac_bit_perfect_mode"
        private const val KEY_DAC_USE_NATIVE_ROUTING = "dac_use_native_routing"
        
        // Cache Settings
        private const val KEY_MAX_CACHE_SIZE = "max_cache_size"

        
        // Search History
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_SHOW_KEYBOARD_ON_SEARCH_OPEN = "show_keyboard_on_search_open"
        
        // Playlists
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_FAVORITE_SONGS = "favorite_songs"
        private const val KEY_DEFAULT_PLAYLISTS_ENABLED = "default_playlists_enabled"
        
        // User Statistics
        private const val KEY_LISTENING_TIME = "listening_time"
        private const val KEY_SONGS_PLAYED = "songs_played"
        private const val KEY_UNIQUE_ARTISTS = "unique_artists"
        private const val KEY_GENRE_PREFERENCES = "genre_preferences"
        private const val KEY_TIME_BASED_PREFERENCES = "time_based_preferences"

        // Rhythm Guard (listening health)
        private const val KEY_RHYTHM_GUARD_MODE = "rhythm_guard_mode"
        private const val KEY_RHYTHM_GUARD_AGE = "rhythm_guard_age"
        private const val KEY_RHYTHM_GUARD_MANUAL_WARNINGS_ENABLED = "rhythm_guard_manual_warnings_enabled"
        private const val KEY_RHYTHM_GUARD_MANUAL_VOLUME_THRESHOLD = "rhythm_guard_manual_volume_threshold"
        private const val KEY_RHYTHM_GUARD_APPLY_VOLUME_LIMIT_ON_SPEAKER = "rhythm_guard_apply_volume_limit_on_speaker"
        private const val KEY_RHYTHM_GUARD_LAST_AUTO_APPLIED_AT = "rhythm_guard_last_auto_applied_at"
        private const val KEY_RHYTHM_GUARD_ALERT_THRESHOLD_MINUTES = "rhythm_guard_alert_threshold_minutes"
        private const val KEY_RHYTHM_GUARD_WARNING_TIMEOUT_MINUTES = "rhythm_guard_warning_timeout_minutes"
        private const val KEY_RHYTHM_GUARD_POST_TIMEOUT_COOLDOWN_MINUTES = "rhythm_guard_post_timeout_cooldown_minutes"
        private const val KEY_RHYTHM_GUARD_BREAK_RESUME_MINUTES = "rhythm_guard_break_resume_minutes"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS = "rhythm_guard_timeout_until_ms"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_REASON = "rhythm_guard_timeout_reason"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS = "rhythm_guard_timeout_started_at_ms"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS = "rhythm_guard_timeout_cooldown_until_ms"
        private const val KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES = "rhythm_guard_next_allowed_limit_minutes"
        private const val KEY_RHYTHM_GUARD_FIRST_BREAK_SEEN = "rhythm_guard_first_break_seen"
        private const val KEY_HOME_SHOW_RHYTHM_GUARD = "home_show_rhythm_guard"

        // Legacy keys kept for migration compatibility.
        private const val KEY_RHYTHM_AURA_MODE = "rhythm_aura_mode"
        private const val KEY_RHYTHM_AURA_AGE = "rhythm_aura_age"
        private const val KEY_RHYTHM_AURA_MANUAL_WARNINGS_ENABLED = "rhythm_aura_manual_warnings_enabled"
        private const val KEY_RHYTHM_AURA_MANUAL_VOLUME_THRESHOLD = "rhythm_aura_manual_volume_threshold"
        private const val KEY_RHYTHM_AURA_LAST_AUTO_APPLIED_AT = "rhythm_aura_last_auto_applied_at"

        const val RHYTHM_GUARD_MODE_OFF = "OFF"
        const val RHYTHM_GUARD_MODE_AUTO = "AUTO"
        const val RHYTHM_GUARD_MODE_MANUAL = "MANUAL"

        @Deprecated("Use RHYTHM_GUARD_MODE_OFF")
        const val RHYTHM_AURA_MODE_OFF = RHYTHM_GUARD_MODE_OFF
        @Deprecated("Use RHYTHM_GUARD_MODE_AUTO")
        const val RHYTHM_AURA_MODE_AUTO = RHYTHM_GUARD_MODE_AUTO
        @Deprecated("Use RHYTHM_GUARD_MODE_MANUAL")
        const val RHYTHM_AURA_MODE_MANUAL = RHYTHM_GUARD_MODE_MANUAL
        
        // Recently Played
        private const val KEY_RECENTLY_PLAYED = "recently_played"
        private const val KEY_RECENTLY_PLAYED_SONG_CACHE = "recently_played_song_cache"
        private const val KEY_LAST_PLAYED_TIMESTAMP = "last_played_timestamp"
        
        // API Integration
        private const val KEY_DEEZER_API_ENABLED = "deezer_api_enabled"
        private const val KEY_LRCLIB_API_ENABLED = "lrclib_api_enabled"
        private const val KEY_BETTERLYRICS_API_ENABLED = "better_lyrics_api_enabled"
        private const val KEY_YTMUSIC_API_ENABLED = "ytmusic_api_enabled"
        private const val KEY_SPOTIFY_API_ENABLED = "spotify_api_enabled"
        private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
        private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
        private const val KEY_LYRICALLY_API_ENABLED = "lyrically_api_enabled"
        private const val KEY_WIKIPEDIA_API_ENABLED = "wikipedia_api_enabled"
        private const val KEY_AUTO_FETCH_ARTWORK = "auto_fetch_artwork"
        private const val KEY_ARTIST_ARTWORK_SOURCE = "artist_artwork_source"
        private const val KEY_APPLE_CANVAS_ENABLED = "apple_canvas_enabled"
        private const val KEY_APPLE_CANVAS_NETWORK_MODE = "apple_canvas_network_mode"
        
        // General Broadcast Status Settings (for Tasker, KWGT, etc.)
        private const val KEY_BROADCAST_STATUS_ENABLED = "broadcast_status_enabled"
        private const val KEY_BLUETOOTH_LYRICS_ENABLED = "bluetooth_lyrics_enabled"
        
        // Enhanced User Preferences
        private const val KEY_FAVORITE_GENRES = "favorite_genres"
        private const val KEY_DAILY_LISTENING_STATS = "daily_listening_stats"
        private const val KEY_WEEKLY_TOP_ARTISTS = "weekly_top_artists"
        private const val KEY_MOOD_PREFERENCES = "mood_preferences"
        
        // Song Play Counts
        private const val KEY_SONG_PLAY_COUNTS = "song_play_counts"

        // Onboarding
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_INITIAL_MEDIA_SCAN_COMPLETED = "initial_media_scan_completed"
        private const val KEY_GENRE_DETECTION_COMPLETED = "genre_detection_completed"
        private const val KEY_AUDIO_METADATA_EXTRACTION_COMPLETED = "audio_metadata_extraction_completed"
        private const val KEY_EMBEDDED_ARTWORK_EXTRACTION_COMPLETED = "embedded_artwork_extraction_completed"
        private const val KEY_EMBEDDED_ARTWORK_EXTRACTION_LOSSLESS_STATUS = "embedded_artwork_extraction_lossless_status"

        // App Updater Settings
        private const val KEY_AUTO_CHECK_FOR_UPDATES = "auto_check_for_updates"
        private const val KEY_UPDATE_CHANNEL = "update_channel" // New key for update channel
        private const val KEY_UPDATE_SOURCE = "update_source" // New key for OTA source flavor
        private const val KEY_UPDATES_ENABLED = "updates_enabled" // Master switch for updates
        private const val KEY_UPDATE_NOTIFICATIONS_ENABLED = "update_notifications_enabled" // Push-style notifications
        private const val KEY_UPDATE_STATUS_NOTIFICATIONS_ENABLED = "update_status_notifications_enabled" // Notify for no-update/error states
        private const val KEY_USE_SMART_UPDATE_POLLING = "use_smart_update_polling" // Use ETag/conditional requests
        private const val KEY_MEDIA_SCAN_MODE = "media_scan_mode" // Mode for media scanning: "blacklist" or "whitelist"
        private const val KEY_INCLUDE_HIDDEN_WHITELISTED_MEDIA = "include_hidden_whitelisted_media"
        private const val KEY_UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours" // Configurable interval

        // Beta Program
        private const val KEY_HAS_SHOWN_BETA_POPUP = "has_shown_beta_popup"

        // Crash Reporting
        private const val KEY_LAST_CRASH_LOG = "last_crash_log"
        private const val KEY_CRASH_LOG_HISTORY = "crash_log_history" // New key for crash log history
        
        // Haptic Feedback
        private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        
        // Notification Settings
        private const val KEY_USE_CUSTOM_NOTIFICATION = "use_custom_notification"
        private const val KEY_LIBRARY_OPERATIONS_NOTIFICATIONS_ENABLED = "library_operations_notifications_enabled"
        private const val KEY_SLEEP_TIMER_NOTIFICATIONS_ENABLED = "sleep_timer_notifications_enabled"
        private const val KEY_STREAMING_NOTIFICATIONS_ENABLED = "streaming_notifications_enabled"
        private const val KEY_RHYTHM_GUARD_ALERT_NOTIFICATIONS_ENABLED = "rhythm_guard_alert_notifications_enabled"
        private const val KEY_RHYTHM_GUARD_TIMER_NOTIFICATIONS_ENABLED = "rhythm_guard_timer_notifications_enabled"
        private const val KEY_RHYTHM_PULSE_NOTIFICATIONS_ENABLED = "rhythm_pulse_notifications_enabled"
        private const val KEY_RHYTHM_PULSE_NOTIFICATION_INTERVAL_HOURS = "rhythm_pulse_notification_interval_hours"
        
        // UI Settings
        private const val KEY_USE_SETTINGS = "use_settings"
        private const val KEY_DEFAULT_SCREEN = "default_screen"
        private const val KEY_FORCE_PLAYER_COMPACT_MODE = "force_player_compact_mode"
        
        // Codec Monitoring & Enhanced Seeking
        private const val KEY_CODEC_MONITORING_ENABLED = "codec_monitoring_enabled"
        private const val KEY_SHOW_CODEC_NOTIFICATIONS = "show_codec_notifications"
        private const val KEY_ENHANCED_SEEKING_ENABLED = "enhanced_seeking_enabled"
        
        // Media3 1.9.0 Features
        private const val KEY_USE_CUSTOM_COMMAND_BUTTONS = "use_custom_command_buttons"
        private const val KEY_SCRUBBING_MODE_ENABLED = "scrubbing_mode_enabled"
        private const val KEY_STUCK_PLAYER_DETECTION_ENABLED = "stuck_player_detection_enabled"
        private const val KEY_TRACK_ERROR_CHECKER_ENABLED = "track_error_checker_enabled"
        
        // Festive Theme Settings
        private const val KEY_FESTIVE_THEME_ENABLED = "festive_theme_enabled"
        private const val KEY_FESTIVE_THEME_TYPE = "festive_theme_type"
        private const val KEY_FESTIVE_THEME_INTENSITY = "festive_theme_intensity"
        private const val KEY_FESTIVE_THEME_AUTO_DETECT = "festive_theme_auto_detect"
        private const val KEY_FESTIVE_SNOWFLAKE_SIZE = "festive_snowflake_size"
        private const val KEY_FESTIVE_SNOWFLAKE_AREA = "festive_snowflake_area"
        
        // Festive Decoration Position Settings
        private const val KEY_FESTIVE_SHOW_TOP_LIGHTS = "festive_show_top_lights"
        private const val KEY_FESTIVE_SHOW_SIDE_GARLAND = "festive_show_side_garland"
        private const val KEY_FESTIVE_SHOW_BOTTOM_SNOW = "festive_show_bottom_snow"
        private const val KEY_FESTIVE_SHOW_SNOWFALL = "festive_show_snowfall"
        
        // Blacklisted Songs
        private const val KEY_BLACKLISTED_SONGS = "blacklisted_songs"
        
        // Blacklisted Folders
        private const val KEY_BLACKLISTED_FOLDERS = "blacklisted_folders"
        
        // Whitelisted Songs
        private const val KEY_WHITELISTED_SONGS = "whitelisted_songs"
        
        // Whitelisted Folders
        private const val KEY_WHITELISTED_FOLDERS = "whitelisted_folders"

        // Pinned Folders (Explorer)
        private const val KEY_PINNED_FOLDERS = "pinned_folders"
        
        // Playlist Playback Behavior
        private const val KEY_PLAYLIST_CLICK_BEHAVIOR = "playlist_click_behavior" // "ask", "play_all", "play_one"
        
        // Backup and Restore
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_BACKUP_LOCATION = "backup_location"
        
        // Sleep Timer
        private const val KEY_SLEEP_TIMER_ACTIVE = "sleep_timer_active"
        private const val KEY_SLEEP_TIMER_REMAINING_SECONDS = "sleep_timer_remaining_seconds"
        private const val KEY_SLEEP_TIMER_ACTION = "sleep_timer_action"
        
        // Media Scan Tracking
        private const val KEY_LAST_SCAN_TIMESTAMP = "last_scan_timestamp"
        private const val KEY_LAST_SCAN_DURATION = "last_scan_duration"
        private const val KEY_PENDING_FULL_MEDIA_RESCAN = "pending_full_media_rescan"
        private const val KEY_LAST_EMBEDDED_ART_SELF_HEAL_MS = "last_embedded_art_self_heal_ms"
        
        // Media Scan Filtering
        private const val KEY_ALLOWED_FORMATS = "allowed_formats"
        private const val KEY_MINIMUM_BITRATE = "minimum_bitrate"
        private const val KEY_MINIMUM_DURATION = "minimum_duration"
        
        // Library Sort Order
        private const val KEY_SONGS_SORT_ORDER = "songs_sort_order"
        
        // Alphabet Bar Settings
        private const val KEY_SHOW_ALPHABET_BAR = "show_alphabet_bar"
        private const val KEY_SHOW_SCROLL_TO_TOP = "show_scroll_to_top"
        
        // App Mode Settings (Local vs Streaming)
        private const val KEY_APP_MODE = "app_mode" // "LOCAL" or "STREAMING"
        private const val KEY_STREAMING_SERVICE = "streaming_service" // "SPOTIFY", "APPLE_MUSIC", etc.
        private const val KEY_STREAMING_QUALITY = "streaming_quality" // "LOW", "MEDIUM", "HIGH", "LOSSLESS"
        private const val KEY_ALLOW_CELLULAR_STREAMING = "allow_cellular_streaming"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_REMEMBER_STREAMING_PASSWORDS = "remember_streaming_passwords"
        
        // Queue & Playback Behavior
        private const val KEY_SHUFFLE_USES_EXOPLAYER = "shuffle_uses_exoplayer"
        private const val KEY_AUTO_ADD_TO_QUEUE = "auto_add_to_queue"
        private const val KEY_CLEAR_QUEUE_ON_NEW_SONG = "clear_queue_on_new_song"
        private const val KEY_CONTEXT_QUEUE_PREFERENCE = "context_queue_preference" // ARTIST_FIRST | GENRE_FIRST | ARTIST_THEN_GENRE
        private const val KEY_CONTEXT_QUEUE_PERSISTENCE = "context_queue_persistence" // EPHEMERAL | PERSISTENT
        private const val KEY_CONTEXT_QUEUE_SIZE = "context_queue_size" // default number of contextual tracks to build
        private const val KEY_HIDE_PLAYED_SONGS_IN_QUEUE = "hide_played_songs_in_queue"
        private const val KEY_SHOW_QUEUE_DIALOG = "show_queue_dialog"
        private const val KEY_LIST_QUEUE_ACTION_BEHAVIOR = "list_queue_action_behavior" // "replace", "ask", "play_next", "add_to_end"
        private const val KEY_REPEAT_MODE_PERSISTENCE = "repeat_mode_persistence"
        private const val KEY_SHUFFLE_MODE_PERSISTENCE = "shuffle_mode_persistence"
        private const val KEY_SAVED_SHUFFLE_STATE = "saved_shuffle_state"
        private const val KEY_SAVED_REPEAT_MODE = "saved_repeat_mode"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        private const val KEY_USE_DEFAULT_PLAYBACK_SPEED = "use_default_playback_speed"
        private const val KEY_PLAYBACK_PITCH = "playback_pitch"
        private const val KEY_SYNC_SPEED_AND_PITCH = "sync_speed_and_pitch"
        private const val KEY_USE_HOURS_IN_TIME_FORMAT = "use_hours_in_time_format"
        private const val KEY_SHOW_REMAINING_TIME = "show_remaining_time"
        private const val KEY_USE_EXACT_ARTWORK_COLORS = "use_exact_artwork_colors"
        private const val KEY_STOP_PLAYBACK_ON_APP_CLOSE = "stop_playback_on_app_close"
        private const val KEY_QUEUE_PERSISTENCE_ENABLED = "queue_persistence_enabled" // Enable/disable queue persistence
        private const val KEY_SAVED_QUEUE = "saved_queue" // Queue persistence - list of song IDs
        private const val KEY_SAVED_QUEUE_INDEX = "saved_queue_index" // Current position in queue
        private const val KEY_SAVED_PLAYBACK_POSITION = "saved_playback_position" // Current playback position in ms
        private const val KEY_HIDE_PLAYED_QUEUE_SONGS = "hide_played_queue_songs" // Hide already-played songs in queue
        
        // Widget Settings
        private const val KEY_WIDGET_SHOW_ALBUM_ART = "widget_show_album_art"
        private const val KEY_WIDGET_SHOW_ARTIST = "widget_show_artist"
        private const val KEY_WIDGET_SHOW_ALBUM = "widget_show_album"
        private const val KEY_WIDGET_CORNER_RADIUS = "widget_corner_radius"
        private const val KEY_WIDGET_AUTO_UPDATE = "widget_auto_update"
        private const val KEY_WIDGET_SHOW_FAVORITE_BUTTON = "widget_show_favorite_button"
        private const val KEY_WIDGET_THEME = "widget_theme"
        // Rhythm Cookie widget corner actions: 0=skip, 1=shuffle, 2=repeat, 3=favorite, 4=none
        private const val KEY_WIDGET_COOKIE_BOTTOM_LEFT = "widget_cookie_bottom_left"
        private const val KEY_WIDGET_COOKIE_BOTTOM_RIGHT = "widget_cookie_bottom_right"
        // Rhythm Stats widget: 0=all time, 1=today, 2=week, 3=month
        private const val KEY_WIDGET_STATS_RANGE = "widget_stats_range"
        // Rhythm Stats gem: 0=longest streak, 1=current streak, 2=active days, 3=total sessions
        private const val KEY_WIDGET_STATS_GEM = "widget_stats_gem"
        
        // Global Header Settings
        private const val KEY_HEADER_COLLAPSE_BEHAVIOR = "header_collapse_behavior" // 0=Normal, 1=Always Collapsed (applies to all screens)
        
        // Home Screen Customization Settings - Header
        private const val KEY_HOME_HEADER_DISPLAY_MODE = "home_header_display_mode" // 0=Icon Only, 1=Name Only, 2=Both
        private const val KEY_HOME_SHOW_APP_ICON = "home_show_app_icon" // Deprecated - kept for migration
        private const val KEY_HOME_APP_ICON_VISIBILITY = "home_app_icon_visibility" // 0=Both, 1=Expanded, 2=Collapsed
        
        // Home Screen Customization Settings - Section Visibility
        private const val KEY_HOME_SHOW_GREETING = "home_show_greeting"
        private const val KEY_HOME_SHOW_RECENTLY_PLAYED = "home_show_recently_played"
        private const val KEY_HOME_SHOW_DISCOVER_CAROUSEL = "home_show_discover_carousel"
        private const val KEY_HOME_SHOW_ARTISTS = "home_show_artists"
        private const val KEY_HOME_SHOW_NEW_RELEASES = "home_show_new_releases"
        private const val KEY_HOME_SHOW_RECENTLY_ADDED = "home_show_recently_added"
        private const val KEY_HOME_SHOW_RECOMMENDED = "home_show_recommended"
        private const val KEY_HOME_SHOW_LISTENING_STATS = "home_show_listening_stats"
        
        // Home Screen Customization Settings - Discover Widget
        private const val KEY_HOME_DISCOVER_AUTO_SCROLL = "home_discover_auto_scroll"
        private const val KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL = "home_discover_auto_scroll_interval"
        private const val KEY_HOME_DISCOVER_ITEM_COUNT = "home_discover_item_count"
        private const val KEY_HOME_DISCOVER_CAROUSEL_STYLE = "home_discover_carousel_style" // 0=Default (2 side peeks), 1=Hero (1 side peek)
        private const val KEY_HOME_DISCOVER_SHOW_ALBUM_NAME = "home_discover_show_album_name"
        private const val KEY_HOME_DISCOVER_SHOW_ARTIST_NAME = "home_discover_show_artist_name"
        private const val KEY_HOME_DISCOVER_SHOW_YEAR = "home_discover_show_year"
        private const val KEY_HOME_DISCOVER_SHOW_PLAY_BUTTON = "home_discover_show_play_button"
        private const val KEY_HOME_DISCOVER_SHOW_GRADIENT = "home_discover_show_gradient"
        
        // Home Screen Customization Settings - Section Item Counts
        private const val KEY_HOME_RECENTLY_PLAYED_COUNT = "home_recently_played_count"
        private const val KEY_HOME_ARTISTS_COUNT = "home_artists_count"
        private const val KEY_HOME_NEW_RELEASES_COUNT = "home_new_releases_count"
        private const val KEY_HOME_RECENTLY_ADDED_COUNT = "home_recently_added_count"
        private const val KEY_HOME_RECOMMENDED_COUNT = "home_recommended_count"
        
        // Home Screen Customization Settings - Card Appearance
        private const val KEY_HOME_COMPACT_CARDS = "home_compact_cards"
        private const val KEY_HOME_SHOW_PLAY_BUTTONS = "home_show_play_buttons"
        private const val KEY_HOME_SECTION_ORDER = "home_section_order"

        // Streaming Home Screen Customization
        private const val KEY_STREAMING_HOME_SHOW_GREETING = "streaming_home_show_greeting"
        private const val KEY_STREAMING_HOME_SHOW_RHYTHM_GUARD = "streaming_home_show_rhythm_guard"
        private const val KEY_STREAMING_HOME_SHOW_RHYTHM_STATS = "streaming_home_show_rhythm_stats"
        private const val KEY_STREAMING_HOME_SHOW_RECENTLY_PLAYED = "streaming_home_show_recently_played"
        private const val KEY_STREAMING_HOME_SHOW_ARTISTS = "streaming_home_show_artists"
        private const val KEY_STREAMING_HOME_SHOW_RECOMMENDED = "streaming_home_show_recommended"
        private const val KEY_STREAMING_HOME_SHOW_NEW_RELEASES = "streaming_home_show_new_releases"
        private const val KEY_STREAMING_HOME_SHOW_PLAYLISTS = "streaming_home_show_playlists"
        private const val KEY_STREAMING_HOME_SHOW_RECOMMENDATIONS = "streaming_home_show_recommendations"
        private const val KEY_STREAMING_HOME_SHOW_TOP_CHARTS = "streaming_home_show_top_charts"
        private const val KEY_STREAMING_HOME_SECTION_ORDER = "streaming_home_section_order"

        private const val KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR = "album_bottom_sheet_gradient_blur"
        private const val KEY_ALBUM_BOTTOM_SHEET_DISC_FILTER = "album_bottom_sheet_disc_filter"
        private const val KEY_ALBUM_HIDE_ABOUT = "album_hide_about"
        
        // Artist Separator Settings
        private const val KEY_ARTIST_SEPARATOR_ENABLED = "artist_separator_enabled"
        private const val KEY_ARTIST_SEPARATOR_DELIMITERS = "artist_separator_delimiters" // Comma-separated string of delimiters
        private const val KEY_ARTIST_SEPARATOR_CACHE_SIGNATURE = "artist_separator_cache_signature"
        
        // Player Screen Customization Settings
        private const val KEY_PLAYER_SHOW_GRADIENT_OVERLAY = "player_show_gradient_overlay"
        private const val KEY_PLAYER_ART_OVERLAY_TYPE = "player_art_overlay_type" // 0=Gradient, 1=Blur
        private const val KEY_PLAYER_ART_OVERLAY_INTENSITY = "player_art_overlay_intensity" // Float 0.0-1.0
        private const val KEY_PLAYER_LYRICS_OVERLAY_TYPE = "player_lyrics_overlay_type" // 0=Gradient, 1=Blur
        private const val KEY_PLAYER_LYRICS_OVERLAY_INTENSITY = "player_lyrics_overlay_intensity" // Float 0.0-1.0
        private const val KEY_PLAYER_AMBIENT_BACKDROP_ENABLED = "player_ambient_backdrop_enabled" // Ambient backdrop from artwork
        private const val KEY_PLAYER_AMBIENT_BACKDROP_INTENSITY = "player_ambient_backdrop_intensity" // Float 0.0-1.0, controls container transparency
        private const val KEY_PLAYER_ACCENT_BACKGROUND_ENABLED = "player_accent_background_enabled" // Use accent color as player bg (normal mode)
        private const val KEY_PLAYER_MERGE_CONTROLS_TO_BOTTOM = "player_merge_controls_to_bottom" // Merge lyrics/favorite into centered bottom icon controls
        private const val KEY_PLAYER_GLASS_INTENSITY = "player_glass_intensity" // Float 0.0-2.0, glass effect opacity multiplier
        private const val KEY_PLAYER_LYRICS_TRANSITION = "player_lyrics_transition" // 0=SlideVertical, 1=Fade, 2=Scale, 3=SlideHorizontal
        private const val KEY_PLAYER_LYRICS_TEXT_SIZE = "player_lyrics_text_size" // Float sp multiplier, default 1.0
        private const val KEY_PLAYER_LYRICS_ALIGNMENT = "player_lyrics_alignment" // "CENTER", "START", "END"
        private const val KEY_PLAYER_SHOW_ART_BELOW_LYRICS = "player_show_art_below_lyrics" // Boolean
        private const val KEY_PLAYER_SHOW_SEEK_BUTTONS = "player_show_seek_buttons"
        private const val KEY_PLAYER_TEXT_ALIGNMENT = "player_text_alignment" // "START", "CENTER", "END"
        private const val KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK = "player_show_song_info_on_artwork"
        private const val KEY_PLAYER_ARTWORK_CORNER_RADIUS = "player_artwork_corner_radius" // 0-40 dp
        private const val KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES = "player_show_audio_quality_badges"
        private const val KEY_PLAYER_PROGRESS_STYLE = "player_progress_style" // "NORMAL", "WAVY", "ROUNDED", "THIN", "THICK"
        private const val KEY_PLAYER_PROGRESS_THUMB_STYLE = "player_progress_thumb_style" // "NONE", "DEFAULT", "CIRCLE", "SQUARE", "PILL", "DIAMOND", "FLOWER", "HEART", "COOKIE", "PUFFY"
        private const val KEY_PLAYER_PROGRESS_THUMB_ROTATE = "player_progress_thumb_rotate" // Boolean
        
        // MiniPlayer Customization Settings
        private const val KEY_MINIPLAYER_PROGRESS_STYLE = "miniplayer_progress_style" // "NORMAL", "WAVY", "ROUNDED", "THIN", "GRADIENT"
        private const val KEY_MINIPLAYER_SHOW_PROGRESS = "miniplayer_show_progress"
        private const val KEY_MINIPLAYER_SHOW_ARTWORK = "miniplayer_show_artwork"
        private const val KEY_MINIPLAYER_ARTWORK_SIZE = "miniplayer_artwork_size" // 40-72 dp
        private const val KEY_MINIPLAYER_CORNER_RADIUS = "miniplayer_corner_radius" // 0-28 dp
        private const val KEY_MINIPLAYER_SHOW_TIME = "miniplayer_show_time"
        private const val KEY_MINIPLAYER_ARTWORK_STYLE = "miniplayer_artwork_style" // "ROUNDED", "CIRCLE", "SQUARE"
        private const val KEY_MINIPLAYER_SHOW_SKIP_BUTTONS = "miniplayer_show_skip_buttons"
        private const val KEY_MINIPLAYER_TEXT_ALIGNMENT = "miniplayer_text_alignment" // "START", "CENTER"
        private const val KEY_MINIPLAYER_SWIPE_GESTURES = "miniplayer_swipe_gestures"
        private const val KEY_MINIPLAYER_SHOW_ARTIST = "miniplayer_show_artist"
        private const val KEY_MINIPLAYER_ALWAYS_SHOW_TABLET = "miniplayer_always_show_tablet"
        
        // Gesture Settings
        private const val KEY_GESTURE_PLAYER_SWIPE_DISMISS = "gesture_player_swipe_dismiss" // Swipe down to dismiss full player
        private const val KEY_GESTURE_PLAYER_SWIPE_TRACKS = "gesture_player_swipe_tracks" // Swipe left/right to change tracks in full player
        private const val KEY_GESTURE_ARTWORK_DOUBLE_TAP = "gesture_artwork_double_tap" // Double tap on artwork to play/pause
        
        // Expressive MaterialShapes Settings (M3 Expressive API)
        private const val KEY_EXPRESSIVE_SHAPES_ENABLED = "expressive_shapes_enabled" // Master toggle for expressive shapes
        private const val KEY_EXPRESSIVE_SHAPE_PRESET = "expressive_shape_preset" // Preset: DEFAULT, PLAYFUL, ORGANIC, GEOMETRIC, RETRO, CUSTOM
        private const val KEY_EXPRESSIVE_SHAPE_ALBUM_ART = "expressive_shape_album_art" // Shape for album artwork
        private const val KEY_EXPRESSIVE_SHAPE_PLAYER_ART = "expressive_shape_player_art" // Shape for player artwork
        private const val KEY_EXPRESSIVE_SHAPE_SONG_ART = "expressive_shape_song_art" // Shape for song artwork
        private const val KEY_EXPRESSIVE_SHAPE_PLAYLIST_ART = "expressive_shape_playlist_art" // Shape for playlist artwork
        private const val KEY_EXPRESSIVE_SHAPE_ARTIST_ART = "expressive_shape_artist_art" // Shape for artist artwork
        private const val KEY_EXPRESSIVE_SHAPE_PLAYER_CONTROLS = "expressive_shape_player_controls" // Shape for player controls
        private const val KEY_EXPRESSIVE_SHAPE_MINI_PLAYER = "expressive_shape_mini_player" // Shape for mini player
        private const val KEY_SHOW_SETTINGS_SUGGESTIONS = "show_settings_suggestions"
        private const val KEY_INITIAL_SETTINGS_SUBROUTE = "initial_settings_subroute"
        private const val KEY_INITIAL_STREAMING_ROUTE = "initial_streaming_route"
        
        const val KEY_SONG_LYRICS_PREFERENCES = "song_lyrics_preferences"
        const val KEY_SONG_CUSTOM_LRC_FILES = "song_custom_lrc_files"
        const val KEY_LRC_RENAME_BEHAVIOR = "lrc_rename_behavior"
        
        @Volatile
        private var INSTANCE: AppSettings? = null
        
        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun defaultAllowedFormats(): Set<String> = setOf(
            "mp3", "flac", "ogg", "m4a", "opus", "opa", "wav", "aac", "alac", "aiff", "aif", "wma",
            "mka", "ac3", "ac4", "oga", "mid", "midi", "adts", "m4b", "eac", "eac3", "mhm", "mhm1",
            "dts", "dtshd", "dtsx", "truehd", "ape", "wv", "tta", "tak", "dsf", "dff", "dsd"
        )
    }
    
    private val context: Context = context.applicationContext
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // DAC Support Settings
    private val _dacSupportEnabled = MutableStateFlow(prefs.getBoolean(KEY_DAC_SUPPORT_ENABLED, false))
    val dacSupportEnabled: StateFlow<Boolean> = _dacSupportEnabled.asStateFlow()
    
    private val _dacBitPerfectMode = MutableStateFlow(prefs.getBoolean(KEY_DAC_BIT_PERFECT_MODE, false))
    val dacBitPerfectMode: StateFlow<Boolean> = _dacBitPerfectMode.asStateFlow()
    
    private val _dacUseNativeRouting = MutableStateFlow(prefs.getBoolean(KEY_DAC_USE_NATIVE_ROUTING, true))
    val dacUseNativeRouting: StateFlow<Boolean> = _dacUseNativeRouting.asStateFlow()
    
    fun setDacSupportEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DAC_SUPPORT_ENABLED, enabled) }
        _dacSupportEnabled.value = enabled
    }
    
    fun setDacBitPerfectMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DAC_BIT_PERFECT_MODE, enabled) }
        _dacBitPerfectMode.value = enabled
    }
    
    fun setDacUseNativeRouting(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DAC_USE_NATIVE_ROUTING, enabled) }
        _dacUseNativeRouting.value = enabled
    }
}
