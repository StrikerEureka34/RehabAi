package com.rehabai.app

import android.app.Application

class RehabAIApplication : Application() {
    
    lateinit var sessionRepository: SessionRepository
        private set
    
    override fun onCreate() {
        super.onCreate()
        sessionRepository = SessionRepository()
    }
}
