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
