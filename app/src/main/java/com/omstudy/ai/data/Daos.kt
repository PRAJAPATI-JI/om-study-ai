package com.omstudy.ai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE ownerUid = :uid ORDER BY hour, minute")
    fun observeAll(uid: String): Flow<List<AlarmEntity>>

    /** सभी अकाउंट के अलार्म (सिर्फ अलार्म रद्द करने जैसे अंदरूनी काम के लिए) */
    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 1 AND ownerUid = :uid")
    suspend fun enabledList(uid: String): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)

    @Query("UPDATE alarms SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimLegacy(uid: String)

    @Query("DELETE FROM alarms WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE epochDay = :day AND ownerUid = :uid ORDER BY startMinutes, id")
    fun observeDay(day: Long, uid: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity): Long

    /** पूरे किए गए काम, किसी दिन से आगे (प्रगति स्क्रीन के लिए) */
    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND done = 1 AND epochDay >= :fromDay")
    fun observeDoneSince(uid: String, fromDay: Long): Flow<List<TaskEntity>>

    @Delete
    suspend fun delete(task: TaskEntity)

    /** पुरानी अपने-आप बनी योजना के बचे हुए (अधूरे) काम हटाना, ताकि दोबारा बनाने पर दोहराव न हो */
    @Query("DELETE FROM tasks WHERE ownerUid = :uid AND note = :tag AND done = 0 AND epochDay >= :fromDay")
    suspend fun deleteAutoPlan(uid: String, tag: String, fromDay: Long)

    @Query("UPDATE tasks SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimLegacy(uid: String)

    @Query("DELETE FROM tasks WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: AlarmLogEntity)

    @Query("SELECT * FROM alarm_logs WHERE firedAt >= :since AND ownerUid = :uid ORDER BY firedAt DESC")
    fun observeSince(since: Long, uid: String): Flow<List<AlarmLogEntity>>

    @Query("UPDATE alarm_logs SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimLegacy(uid: String)

    @Query("DELETE FROM alarm_logs WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile_answers WHERE ownerUid = :uid")
    fun observe(uid: String): Flow<List<ProfileAnswerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(answer: ProfileAnswerEntity)

    @Query("DELETE FROM profile_answers WHERE ownerUid = :uid AND key = :key")
    suspend fun delete(uid: String, key: String)

    @Query("DELETE FROM profile_answers WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE ownerUid = :uid ORDER BY subject, position, id")
    fun observe(uid: String): Flow<List<ChapterEntity>>

    @Insert
    suspend fun insertAll(items: List<ChapterEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM chapters WHERE ownerUid = :uid AND subject = :subject")
    suspend fun maxPosition(uid: String, subject: String): Int

    @Query("UPDATE chapters SET done = :done, doneAt = :at WHERE id = :id AND ownerUid = :uid")
    suspend fun setDone(id: Long, uid: String, done: Boolean, at: Long?)

    @Query("DELETE FROM chapters WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: Long, uid: String)

    @Query("DELETE FROM chapters WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE ownerUid = :uid ORDER BY bookmarked DESC, updatedAt DESC")
    fun observe(uid: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: Long, uid: String)

    @Query("DELETE FROM notes WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE ownerUid = :uid ORDER BY subject, id DESC")
    fun observe(uid: String): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(q: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: Long, uid: String)

    @Query("DELETE FROM questions WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface QuizAnswerDao {
    @Insert
    suspend fun insert(a: QuizAnswerEntity)

    @Query("SELECT * FROM quiz_answers WHERE ownerUid = :uid ORDER BY answeredAt")
    fun observe(uid: String): Flow<List<QuizAnswerEntity>>

    @Query("DELETE FROM quiz_answers WHERE ownerUid = :uid AND questionId = :qid")
    suspend fun deleteForQuestion(uid: String, qid: Long)

    @Query("DELETE FROM quiz_answers WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM saved_videos WHERE ownerUid = :uid ORDER BY createdAt DESC")
    fun observe(uid: String): Flow<List<SavedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: SavedVideoEntity)

    @Query("DELETE FROM saved_videos WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: Long, uid: String)

    @Query("DELETE FROM saved_videos WHERE ownerUid = :uid")
    suspend fun deleteAllFor(uid: String)
}
