package com.omstudy.ai.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omstudy.ai.alarm.AlarmScheduler
import com.omstudy.ai.data.AlarmEntity
import com.omstudy.ai.data.AppDatabase
import com.omstudy.ai.data.AuthRepository
import com.omstudy.ai.data.Prefs
import com.omstudy.ai.data.TaskEntity
import com.omstudy.ai.data.Keys
import com.omstudy.ai.data.Onboarding
import com.omstudy.ai.data.ProfileAnswerEntity
import com.omstudy.ai.data.StudyPlanner
import com.omstudy.ai.data.AlarmLogEntity
import com.omstudy.ai.data.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

const val PLAN_NOTE = "सुझाई गई योजना"

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val db = AppDatabase.get(ctx)
    private val uidFlow: StateFlow<String?> = AuthRepository.uid

    val userName: StateFlow<String> = AuthRepository.name
    val email: StateFlow<String> = AuthRepository.email

    // हर लिस्ट सिर्फ अभी लॉगिन अकाउंट (uid) की होती है। लॉगआउट होते ही खाली, यानी पिछले अकाउंट का डेटा नहीं दिखता।
    @OptIn(ExperimentalCoroutinesApi::class)
    val alarms = uidFlow
        .flatMapLatest { u -> if (u == null) flowOf(emptyList<AlarmEntity>()) else db.alarmDao().observeAll(u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _day = MutableStateFlow(LocalDate.now().toEpochDay())
    val day: StateFlow<Long> = _day

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks = combine(uidFlow, _day) { u, d -> u to d }
        .flatMapLatest { (u, d) ->
            if (u == null) flowOf(emptyList<TaskEntity>()) else db.taskDao().observeDay(d, u)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayTasks = uidFlow
        .flatMapLatest { u ->
            if (u == null) flowOf(emptyList<TaskEntity>())
            else db.taskDao().observeDay(LocalDate.now().toEpochDay(), u)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayLogs = uidFlow
        .flatMapLatest { u ->
            if (u == null) flowOf(emptyList<AlarmLogEntity>())
            else db.logDao().observeSince(
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), u
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** इस अकाउंट के onboarding जवाब। null = अभी लोड हो रहे हैं (या लॉगिन नहीं है)। */
    @OptIn(ExperimentalCoroutinesApi::class)
    val answers: StateFlow<Map<String, String>?> = uidFlow
        .flatMapLatest { u ->
            if (u == null) flowOf<Map<String, String>?>(null)
            else db.profileDao().observe(u).map { rows -> rows.associate { it.key to it.value } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode = ctx.dataStore.data.map { it[Prefs.THEME] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // लॉगिन होते ही: लॉगिन से पहले वाला पुराना डेटा इस अकाउंट को दो, अलार्म दोबारा लगाओ, प्रोफाइल पक्का करो
        viewModelScope.launch(Dispatchers.IO) {
            uidFlow.filterNotNull().collect { u ->
                db.alarmDao().claimLegacy(u)
                db.taskDao().claimLegacy(u)
                db.logDao().claimLegacy(u)
                AlarmScheduler.rescheduleAll(ctx)
                if (!AuthRepository.busy) AuthRepository.ensureProfile()
            }
        }
    }

    // ---- अलार्म ----
    fun saveAlarm(alarm: AlarmEntity) = viewModelScope.launch(Dispatchers.IO) {
        val u = AuthRepository.uid.value ?: return@launch
        val owned = alarm.copy(ownerUid = u, updatedAt = System.currentTimeMillis())
        val id = db.alarmDao().upsert(owned)
        AlarmScheduler.schedule(ctx, owned.copy(id = if (owned.id == 0L) id else owned.id))
    }

    fun deleteAlarm(alarm: AlarmEntity) = viewModelScope.launch(Dispatchers.IO) {
        AlarmScheduler.cancel(ctx, alarm.id)
        db.alarmDao().delete(alarm)
    }

    fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        db.alarmDao().setEnabled(alarm.id, enabled, System.currentTimeMillis())
        AlarmScheduler.schedule(ctx, alarm.copy(enabled = enabled))
    }

    // ---- प्लानर ----
    fun setDay(epochDay: Long) {
        _day.value = epochDay
    }

    fun saveTask(task: TaskEntity) = viewModelScope.launch(Dispatchers.IO) {
        val u = AuthRepository.uid.value ?: return@launch
        db.taskDao().upsert(task.copy(ownerUid = u))
    }

    fun deleteTask(task: TaskEntity) = viewModelScope.launch(Dispatchers.IO) { db.taskDao().delete(task) }

    fun toggleTask(task: TaskEntity, done: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        db.taskDao().upsert(task.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null))
    }

    /** अधूरा काम अगले दिन के लिए टालना */
    fun postponeTask(task: TaskEntity) = viewModelScope.launch(Dispatchers.IO) {
        db.taskDao().upsert(task.copy(epochDay = task.epochDay + 1, done = false, doneAt = null))
    }

    // ---- onboarding और योजना ----
    fun saveAnswer(key: String, value: String) = viewModelScope.launch(Dispatchers.IO) {
        val u = AuthRepository.uid.value ?: return@launch
        db.profileDao().upsert(ProfileAnswerEntity(u, key, value))
        // कक्षा/स्ट्रीम बदलने पर पुराने विषय अब सही नहीं रहते, इसलिए दोबारा पूछे जाएँगे
        if (key == Keys.CLASS || key == Keys.STREAM) {
            db.profileDao().delete(u, Keys.SUBJECTS)
            db.profileDao().delete(u, Keys.WEAK)
        }
    }

    fun clearAnswer(key: String) = viewModelScope.launch(Dispatchers.IO) {
        val u = AuthRepository.uid.value ?: return@launch
        db.profileDao().delete(u, key)
        if (key == Keys.CLASS || key == Keys.STREAM) {
            db.profileDao().delete(u, Keys.SUBJECTS)
            db.profileDao().delete(u, Keys.WEAK)
        }
    }

    fun confirmOnboarding() = saveAnswer(Keys.DONE, "1")

    /** "जवाब देखें/बदलें" खोलना */
    fun reopenReview() = clearAnswer(Keys.DONE)

    /** सुझाई गई 7 दिन की योजना को प्लानर में जोड़ना। पुरानी अधूरी अपने-आप बनी योजना पहले हट जाती है। */
    fun activatePlan(onDone: (Int) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val u = AuthRepository.uid.value ?: return@launch
        val a = answers.value ?: return@launch
        val today = LocalDate.now()
        val items = StudyPlanner.generate(a, today.toEpochDay(), today.dayOfWeek.value - 1, 7)
        db.taskDao().deleteAutoPlan(u, PLAN_NOTE, today.toEpochDay())
        items.forEach {
            db.taskDao().upsert(
                TaskEntity(
                    title = it.subject, subject = it.subject, epochDay = it.epochDay,
                    startMinutes = it.startMinutes, durationMin = it.durationMin,
                    priority = if (it.isRevision) 2 else 1, note = PLAN_NOTE, ownerUid = u
                )
            )
        }
        withContext(Dispatchers.Main) { onDone(items.size) }
    }

    // ---- सेटिंग्स और खाता ----
    fun setTheme(mode: Int) = viewModelScope.launch { ctx.dataStore.edit { it[Prefs.THEME] = mode } }

    fun setName(name: String) = viewModelScope.launch(Dispatchers.IO) { AuthRepository.updateName(name) }

    fun testAlarm() = AlarmScheduler.scheduleTest(ctx, 10)

    /** लॉगआउट: सारे अलार्म रद्द (ताकि अगला अकाउंट इनके बजने से परेशान न हो), फिर साइन-आउट */
    fun logout() = viewModelScope.launch(Dispatchers.IO) {
        db.alarmDao().all().forEach { AlarmScheduler.cancel(ctx, it.id) }
        AuthRepository.signOut()
    }

    /** सिर्फ अभी लॉगिन अकाउंट का फोन-वाला डेटा मिटाता है। अकाउंट खुद नहीं मिटता। */
    fun clearAll(onDone: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val u = AuthRepository.uid.value ?: return@launch
        db.alarmDao().all().filter { it.ownerUid == u }.forEach { AlarmScheduler.cancel(ctx, it.id) }
        db.alarmDao().deleteAllFor(u)
        db.taskDao().deleteAllFor(u)
        db.logDao().deleteAllFor(u)
        db.profileDao().deleteAllFor(u)
        withContext(Dispatchers.Main) { onDone() }
    }
}
