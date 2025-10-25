package com.example.foodguard3

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        return try {
            val col = db.collection("users")
                .document(user.uid)
                .collection("foods")
                .whereEqualTo("status", "active")

            val snap = Tasks.await(col.get())

            val days = 3 // default reminder window; you can wire Prefs later
            val now = System.currentTimeMillis()
            val until = now + TimeUnit.DAYS.toMillis(days.toLong())

            var count = 0
            for (doc in snap.documents) {
                val ts = doc.getTimestamp("expiry") ?: continue
                val ms = ts.toDate().time
                if (ms in (now + 1)..until) count++
            }

            if (count > 0) NotificationHelper.notifyExpiring(applicationContext, count)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
