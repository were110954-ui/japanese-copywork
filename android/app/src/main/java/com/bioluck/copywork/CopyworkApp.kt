package com.bioluck.copywork

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.*
import com.bioluck.copywork.data.*
import com.bioluck.copywork.sync.SyncWorker
import java.util.concurrent.TimeUnit

class CopyworkApp : Application() {
    lateinit var repository: CopyworkRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''")
            }
        }
        val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN rubyVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
        val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM drafts WHERE articleId IN (SELECT id FROM articles WHERE category = 'shunju')")
                db.execSQL("DELETE FROM article_states WHERE articleId IN (SELECT id FROM articles WHERE category = 'shunju')")
                db.execSQL("DELETE FROM articles WHERE category = 'shunju'")
            }
        }
        val db = Room.databaseBuilder(this, CopyworkDatabase::class.java, "copywork.db")
            .addMigrations(migration1To2, migration2To3, migration3To4).build()
        repository = CopyworkRepository(db.dao())

        val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-article-sync", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
