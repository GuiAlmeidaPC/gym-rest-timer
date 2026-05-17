package com.gymresttimer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [RestProfile::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restProfileDao(): RestProfileDao

    companion object {
        fun build(context: Context, scope: CoroutineScope): AppDatabase {
            lateinit var instance: AppDatabase
            instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gym_rest_timer.db",
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            instance.restProfileDao().insertAll(DEFAULT_PROFILES)
                        }
                    }
                })
                .addMigrations(MIGRATION_1_2)
                .build()
            return instance
        }

        private val DEFAULT_PROFILES = listOf(
            RestProfile(profileName = "Quick", restDurationSeconds = 60),
            RestProfile(profileName = "Hypertrophy", restDurationSeconds = 90),
            RestProfile(profileName = "Strength", restDurationSeconds = 180),
            RestProfile(profileName = "Power", restDurationSeconds = 240),
        )

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO rest_profiles (profileName, restDurationSeconds) " +
                        "SELECT 'Quick', 60 WHERE NOT EXISTS " +
                        "(SELECT 1 FROM rest_profiles WHERE restDurationSeconds = 60)",
                )
            }
        }
    }
}
