package com.watube.yard.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.watube.yard.db.dao.CustomInstanceDao
import com.watube.yard.db.dao.DownloadDao
import com.watube.yard.db.dao.LocalPlaylistsDao
import com.watube.yard.db.dao.LocalSubscriptionDao
import com.watube.yard.db.dao.PlaylistBookmarkDao
import com.watube.yard.db.dao.SearchHistoryDao
import com.watube.yard.db.dao.SubscriptionGroupsDao
import com.watube.yard.db.dao.SubscriptionsFeedDao
import com.watube.yard.db.dao.WatchHistoryDao
import com.watube.yard.db.dao.WatchPositionDao
import com.watube.yard.db.obj.CustomInstance
import com.watube.yard.db.obj.Download
import com.watube.yard.db.obj.DownloadChapter
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.db.obj.DownloadPlaylist
import com.watube.yard.db.obj.DownloadPlaylistVideosCrossRef
import com.watube.yard.db.obj.DownloadSponsorBlockSegment
import com.watube.yard.db.obj.LocalPlaylist
import com.watube.yard.db.obj.LocalPlaylistItem
import com.watube.yard.db.obj.LocalSubscription
import com.watube.yard.db.obj.PlaylistBookmark
import com.watube.yard.db.obj.SearchHistoryItem
import com.watube.yard.db.obj.SubscriptionGroup
import com.watube.yard.db.obj.SubscriptionsFeedItem
import com.watube.yard.db.obj.WatchHistoryItem
import com.watube.yard.db.obj.WatchPosition

@Database(
    entities = [
        WatchHistoryItem::class,
        WatchPosition::class,
        SearchHistoryItem::class,
        CustomInstance::class,
        LocalSubscription::class,
        PlaylistBookmark::class,
        LocalPlaylist::class,
        LocalPlaylistItem::class,
        Download::class,
        DownloadItem::class,
        DownloadChapter::class,
        DownloadSponsorBlockSegment::class,
        DownloadPlaylist::class,
        DownloadPlaylistVideosCrossRef::class,
        SubscriptionGroup::class,
        SubscriptionsFeedItem::class
    ],
    version = 25,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 23, to = 24, spec = DatabaseHolder.MIGRATION_23_24::class),
        AutoMigration(from = 24, to = 25),
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Watch History
     */
    abstract fun watchHistoryDao(): WatchHistoryDao

    /**
     * Watch Positions
     */
    abstract fun watchPositionDao(): WatchPositionDao

    /**
     * Search History
     */
    abstract fun searchHistoryDao(): SearchHistoryDao

    /**
     * Custom Instances
     */
    abstract fun customInstanceDao(): CustomInstanceDao

    /**
     * Local Subscriptions
     */
    abstract fun localSubscriptionDao(): LocalSubscriptionDao

    /**
     * Bookmarked Playlists
     */
    abstract fun playlistBookmarkDao(): PlaylistBookmarkDao

    /**
     * Local playlists
     */
    abstract fun localPlaylistsDao(): LocalPlaylistsDao

    /**
     * Downloads
     */
    abstract fun downloadDao(): DownloadDao

    /**
     * Subscription groups
     */
    abstract fun subscriptionGroupsDao(): SubscriptionGroupsDao

    /**
     * Locally cached subscription feed
     */
    abstract fun feedDao(): SubscriptionsFeedDao
}
