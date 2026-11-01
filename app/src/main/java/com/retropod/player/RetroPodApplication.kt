package com.retropod.player

import android.app.Application
import android.content.Context

class RetroPodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
    }
}

object AppContextHolder {
    lateinit var context: Context
}
