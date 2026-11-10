package com.omstudy.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * डेटाबेस version 3। version 1 → 2 में हर टेबल में ownerUid कॉलम जुड़ता है, पुराना डेटा सुरक्षित रहता है।
 * fallbackToDestructiveMigration जानबूझकर नहीं लगाया, ताकि अपडेट पर डेटा कभी न मिटे।
 */
@Database(
    entities = [AlarmEntity::class, TaskEntity::class, AlarmLogEntity::class, ProfileAnswerEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao
    abstract fun profileDao(): ProfileDao

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

        /** version 2 → 3: onboarding के जवाबों की नई टेबल। पुराना डेटा जस का तस रहता है। */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profile_answers` (`ownerUid` TEXT NOT NULL, `key` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, PRIMARY KEY(`ownerUid`, `key`))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "om_study.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
