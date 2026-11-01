package com.retropod.player

import android.app.Application

class RetroPodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
    }
}
