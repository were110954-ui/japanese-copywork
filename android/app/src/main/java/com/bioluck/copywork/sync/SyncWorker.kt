package com.bioluck.copywork.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bioluck.copywork.CopyworkApp

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as CopyworkApp).repository.sync(incremental = true)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
