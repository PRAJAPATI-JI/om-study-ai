package com.omstudy.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omstudy.ai.data.AuthRepository
import com.omstudy.ai.data.AuthRules
import kotlinx.coroutines.launch

@Composable
fun AuthFlow() {
    var screen by rememberSaveable { mutableStateOf("welcome") }
    Surface(Modifier.fillMaxSize()) {
        when (screen) {
            "signup" -> SignUpScreen(onBack = { screen = "welcome" }, onLogin = { screen = "login" })
            "login" -> LoginScreen(
                onBack = { screen = "welcome" },
                onForgot = { screen = "forgot" },
                onSignUp = { screen = "signup" }
            )
            "forgot" -> ForgotScreen(onBack = { screen = "login" })
            else -> WelcomeScreen(onSignUp = { screen = "signup" }, onLogin = { screen = "login" })
        }
    }
}

@Composable
private fun AuthColumn(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OM STUDY AI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WelcomeScreen(onSignUp: () -> Unit, onLogin: () -> Unit) {
    AuthColumn(
        title = "नमस्ते! पढ़ाई का आपका साथी",
        subtitle = "अपना अकाउंट बनाइए या लॉगिन कीजिए। आपकी पढ़ाई का डेटा सिर्फ आपके अकाउंट में रहता है।"
    ) {
        Button(onClick = onSignUp, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("नया अकाउंट बनाएँ", fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("लॉगिन करें", fontSize = 17.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "पासवर्ड हम कभी नहीं देखते। उसे Google की Firebase सेवा सुरक्षित रखती है।",
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SignUpScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthColumn(
        title = "नया अकाउंट बनाइए",
        subtitle = "आपका नाम यहीं एक बार लिखना है। आगे AI शिक्षक दोबारा नहीं पूछेगा।"
    ) {
        AuthField(name, { name = it.take(AuthRules.MAX_NAME) }, "आपका नाम")
        Spacer(Modifier.height(10.dp))
        AuthField(email, { email = it }, "ईमेल", KeyboardType.Email)
        Spacer(Modifier.height(10.dp))
        AuthField(password, { password = it }, "पासवर्ड (कम से कम ${AuthRules.MIN_PASSWORD} अक्षर)", KeyboardType.Password, true)
        Spacer(Modifier.height(10.dp))
        AuthField(confirm, { confirm = it }, "पासवर्ड दोबारा लिखें", KeyboardType.Password, true)
        Spacer(Modifier.height(10.dp))
        ErrorText(error)
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            onClick = {
                val problem = AuthRules.validateSignUp(name, email, password, confirm)
                if (problem != null) {
                    error = problem
                } else {
                    error = null
                    loading = true
                    scope.launch {
                        AuthRepository.signUp(name.trim(), email.trim(), password)
                            .onFailure { error = AuthRepository.friendlyError(it) }
                        loading = false
                    }
                }
            }
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(22.dp)) else Text("अकाउंट बनाएँ", fontSize = 17.sp)
        }
        TextButton(onClick = onLogin) { Text("पहले से अकाउंट है? लॉगिन करें") }
        TextButton(onClick = onBack) { Text("वापस") }
    }
}

@Composable
private fun LoginScreen(onBack: () -> Unit, onForgot: () -> Unit, onSignUp: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthColumn(title = "लॉगिन") {
        AuthField(email, { email = it }, "ईमेल", KeyboardType.Email)
        Spacer(Modifier.height(10.dp))
        AuthField(password, { password = it }, "पासवर्ड", KeyboardType.Password, true)
        Spacer(Modifier.height(10.dp))
        ErrorText(error)
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            onClick = {
                val problem = AuthRules.validateLogin(email, password)
                if (problem != null) {
                    error = problem
                } else {
                    error = null
                    loading = true
                    scope.launch {
                        AuthRepository.signIn(email.trim(), password)
                            .onFailure { error = AuthRepository.friendlyError(it) }
                        loading = false
                    }
                }
            }
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(22.dp)) else Text("लॉगिन", fontSize = 17.sp)
        }
        TextButton(onClick = onForgot) { Text("पासवर्ड भूल गए?") }
        TextButton(onClick = onSignUp) { Text("नया अकाउंट बनाएँ") }
        TextButton(onClick = onBack) { Text("वापस") }
    }
}

@Composable
private fun ForgotScreen(onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthColumn(
        title = "पासवर्ड बदलना",
        subtitle = "अपना ईमेल लिखिए। हम पासवर्ड बदलने का लिंक भेजेंगे।"
    ) {
        AuthField(email, { email = it }, "ईमेल", KeyboardType.Email)
        Spacer(Modifier.height(10.dp))
        ErrorText(error)
        message?.let { Text(it, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            onClick = {
                if (!AuthRules.isValidEmail(email)) {
                    error = "सही ईमेल लिखिए।"
                    message = null
                } else {
                    error = null
                    message = null
                    loading = true
                    scope.launch {
                        val result = AuthRepository.sendPasswordReset(email.trim())
                        result.onSuccess {
                            message = "अगर यह ईमेल रजिस्टर है, तो पासवर्ड बदलने का लिंक भेज दिया गया है। स्पैम फोल्डर भी देखिए।"
                        }.onFailure {
                            error = AuthRepository.friendlyError(it)
                        }
                        loading = false
                    }
                }
            }
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(22.dp)) else Text("लिंक भेजें", fontSize = 17.sp)
        }
        TextButton(onClick = onBack) { Text("लॉगिन पर वापस") }
    }
}
