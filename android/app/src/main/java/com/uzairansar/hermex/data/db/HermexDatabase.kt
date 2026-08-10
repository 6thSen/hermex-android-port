package com.uzairansar.hermex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedSessionEntity::class, CachedMessageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        fun create(context: Context): HermexDatabase = Room.databaseBuilder(
            context,
            HermexDatabase::class.java,
            "hermex.db",
        )
            // v2: wipe stale session cache (7-day TTL) so pre-fix sessions cached
            // with a bare model id / openrouter provider pin don't survive the
            // catalog-namespace fix. The cache is re-fetched from the server.
            .fallbackToDestructiveMigration()
            .build()
    }
}
