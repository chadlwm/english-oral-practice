package com.example.englishoralpractice.library.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VideoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {
    
    abstract fun videoDao(): VideoDao
    
    companion object {
        @Volatile
        private var INSTANCE: VideoDatabase? = null
        
        fun getInstance(context: Context): VideoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VideoDatabase::class.java,
                    "english_oral_practice.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
