package com.example.foodguard3

import android.app.Application

class FoodGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        ReminderScheduler.scheduleDaily(this) // schedule once; kept if already scheduled
    }
}
