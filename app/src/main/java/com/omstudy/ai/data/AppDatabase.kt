package com.omstudy.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * डेटाबेस version 4। version 1 → 2 में हर टेबल में ownerUid कॉलम जुड़ता है, पुराना डेटा सुरक्षित रहता है।
 * fallbackToDestructiveMigration जानबूझकर नहीं लगाया, ताकि अपडेट पर डेटा कभी न मिटे।
 */
@Database(
    entities = [
        AlarmEntity::class, TaskEntity::class, AlarmLogEntity::class, ProfileAnswerEntity::class,
        ChapterEntity::class, NoteEntity::class, QuestionEntity::class, QuizAnswerEntity::class,
        SavedVideoEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao
    abstract fun profileDao(): ProfileDao
    abstract fun chapterDao(): ChapterDao
    abstract fun noteDao(): NoteDao
    abstract fun questionDao(): QuestionDao
    abstract fun quizAnswerDao(): QuizAnswerDao
    abstract fun videoDao(): VideoDao

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

        /** version 3 → 4: सिलेबस, नोट्स, प्रश्न, क्विज़ उत्तर, वीडियो लिंक की नई टेबल। पुराना डेटा जस का तस। */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerUid` TEXT NOT NULL, `subject` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, `doneAt` INTEGER, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerUid` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `subject` TEXT NOT NULL, " +
                        "`chapter` TEXT NOT NULL, `bookmarked` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `questions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerUid` TEXT NOT NULL, `subject` TEXT NOT NULL, `chapter` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`optA` TEXT NOT NULL, `optB` TEXT NOT NULL, `optC` TEXT NOT NULL, `optD` TEXT NOT NULL, " +
                        "`correct` INTEGER NOT NULL, `explanation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quiz_answers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerUid` TEXT NOT NULL, `questionId` INTEGER NOT NULL, `subject` TEXT NOT NULL, " +
                        "`chapter` TEXT NOT NULL, `correct` INTEGER NOT NULL, `answeredAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_videos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerUid` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `subject` TEXT NOT NULL, " +
                        "`chapter` TEXT NOT NULL, `watched` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "om_study.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
