package com.example.foodguard3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


class HomeActivity : AppCompatActivity() {

    private fun showFragment(tag: String) {
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        val frag = existing ?: when (tag) {
            "home" -> HomeFragment()
            "pantry" -> PantryFragment()
            "expiring" -> ExpiringFragment()
            "profile" -> ProfileFragment()
            else -> HomeFragment()
        }
        fm.beginTransaction()
            .replace(R.id.fragmentContainer, frag, tag)
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)


        NotificationHelper.createChannel(this)
        requestNotificationPermissionIfNeeded()
        ReminderScheduler.scheduleDaily(this)



        //sample testing rani, comment kung mana
        debugTriggerReminderOnce(1)






        val bottom = findViewById<BottomNavigationView>(R.id.bottomNav)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)

        if (savedInstanceState == null) {
            showFragment("home")
            bottom.selectedItemId = R.id.nav_home
        }

        bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment("home")
                R.id.nav_pantry -> showFragment("pantry")
                R.id.nav_expiring -> showFragment("expiring")
                R.id.nav_profile -> showFragment("profile")
            }
            true
        }

        fab.setOnClickListener {
            AddFoodBottomSheet().show(supportFragmentManager, "addFood")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    42
                )
            }
        }
    }










    private fun debugTriggerReminderOnce(minutes: Long = 1) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES) // 1 minute by default
            .build()
        WorkManager.getInstance(this).enqueue(req)
    }





}
