package com.example.englishoralpractice.library.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val durationMs: Long,
    val uri: String,
    val subtitleUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
