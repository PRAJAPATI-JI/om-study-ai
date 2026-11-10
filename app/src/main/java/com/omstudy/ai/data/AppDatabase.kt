package com.omstudy.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * डेटाबेस version 2। version 1 → 2 में हर टेबल में ownerUid कॉलम जुड़ता है, पुराना डेटा सुरक्षित रहता है।
 * fallbackToDestructiveMigration जानबूझकर नहीं लगाया, ताकि अपडेट पर डेटा कभी न मिटे।
 */
@Database(
    entities = [AlarmEntity::class, TaskEntity::class, AlarmLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alarm_logs ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "om_study.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
