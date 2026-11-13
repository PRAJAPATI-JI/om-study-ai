package com.omstudy.ai.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * लॉगिन और प्रोफाइल। पासवर्ड कभी हमारे पास नहीं आता, उसे Firebase Authentication संभालता है।
 * uid = हर अकाउंट की बदली न जा सकने वाली पहचान। सारा निजी डेटा इसी uid से बँधा है।
 */
object AuthRepository {
    private val _uid = MutableStateFlow<String?>(null)
    private val _name = MutableStateFlow("")
    private val _email = MutableStateFlow("")

    val uid: StateFlow<String?> = _uid
    val name: StateFlow<String> = _name
    val email: StateFlow<String> = _email

    /** साइनअप चल रहा हो तो true, ताकि प्रोफाइल दो बार न बने */
    @Volatile
    var busy: Boolean = false
        private set

    init {
        publish(FirebaseAuth.getInstance().currentUser)
        FirebaseAuth.getInstance().addAuthStateListener { publish(it.currentUser) }
    }

    private fun publish(user: FirebaseUser?) {
        _uid.value = user?.uid
        _name.value = user?.displayName.orEmpty()
        _email.value = user?.email.orEmpty()
    }

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> {
        busy = true
        return try {
            runCatching {
                val result = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: error("user missing")
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
                _name.value = name
                ensureProfile()
            }
        } finally {
            busy = false
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
        Unit
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
        Unit
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    /** Firestore में users/{uid} प्रोफाइल बनाता है (पहले से हो तो कुछ नहीं करता)। इंटरनेट न हो तो चुपचाप छोड़ देता है। */
    suspend fun ensureProfile() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        runCatching {
            val ref = FirebaseFirestore.getInstance().collection("users").document(user.uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "displayName" to (user.displayName ?: "").take(AuthRules.MAX_NAME),
                        "email" to (user.email ?: ""),
                        "language" to "hi",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    /**
     * अकाउंट हमेशा के लिए मिटाना। सुरक्षा के लिए पहले पासवर्ड से दोबारा पहचान ली जाती है।
     * Firestore की प्रोफाइल मिटाना कोशिश भर है (इंटरनेट/नियम में दिक्कत हो तो भी अकाउंट मिट जाता है)।
     */
    suspend fun deleteAccount(password: String): Result<Unit> = runCatching {
        val user = FirebaseAuth.getInstance().currentUser ?: error("not signed in")
        val mail = user.email ?: error("no email")
        user.reauthenticate(EmailAuthProvider.getCredential(mail, password)).await()
        runCatching { FirebaseFirestore.getInstance().collection("users").document(user.uid).delete().await() }
        user.delete().await()
        Unit
    }

    suspend fun updateName(newName: String): Result<Unit> = runCatching {
        val clean = newName.trim().take(AuthRules.MAX_NAME)
        val user = FirebaseAuth.getInstance().currentUser ?: error("not signed in")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).await()
        _name.value = clean
        runCatching {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .update("displayName", clean).await()
        }
    }

    /** error का हिंदी संदेश। असली तकनीकी संदेश या गोपनीय बातें स्क्रीन पर नहीं दिखाते। */
    fun friendlyError(e: Throwable): String = when (e) {
        is FirebaseNetworkException -> "इंटरनेट नहीं चल रहा। कनेक्शन देखकर दोबारा कोशिश कीजिए।"
        is FirebaseAuthUserCollisionException -> "यह ईमेल पहले से रजिस्टर है। लॉगिन कीजिए।"
        is FirebaseAuthWeakPasswordException -> "पासवर्ड कमज़ोर है। उसे और लंबा या मुश्किल रखिए।"
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "ईमेल या पासवर्ड सही नहीं है।"
        is FirebaseTooManyRequestsException -> "बहुत बार कोशिश हो गई। थोड़ी देर बाद फिर कीजिए।"
        else -> "कुछ गड़बड़ हो गई। थोड़ी देर बाद दोबारा कोशिश कीजिए।"
    }
}
