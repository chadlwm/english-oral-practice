package com.example.englishoralpractice

import android.app.Application
import com.example.englishoralpractice.library.data.VideoDatabase

class EnglishOralPracticeApp : Application() {
    
    val database: VideoDatabase by lazy {
        VideoDatabase.getInstance(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: EnglishOralPracticeApp
            private set
    }
}
