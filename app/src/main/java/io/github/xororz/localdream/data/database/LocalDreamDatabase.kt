package io.github.xororz.localdream.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GenerationEntity::class,
        PromptPresetEntity::class,
        TagEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LocalDreamDatabase : RoomDatabase() {
    abstract fun generationDao(): GenerationDao
    abstract fun promptPresetDao(): PromptPresetDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: LocalDreamDatabase? = null

        fun getDatabase(context: Context): LocalDreamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalDreamDatabase::class.java,
                    "local_dream_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
