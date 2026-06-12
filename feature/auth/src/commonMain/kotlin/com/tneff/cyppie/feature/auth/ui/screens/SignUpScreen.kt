package com.tneff.cyppie.feature.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tneff.cyppie.feature.auth.ui.components.LabeledTextField
import com.tneff.cyppie.feature.auth.ui.components.LinkRow
import com.tneff.cyppie.feature.auth.ui.components.PrimaryButton
import com.tneff.cyppie.feature.auth.ui.theme.TextSubtitle

/**
 * Functional sign-up form. Visuals are intentionally simple for now and will be
 * adapted pixel-perfectly once the Figma link for this screen is provided.
 */
@Composable
fun SignUpScreen(
    error: String?,
    onSubmit: (name: String, email: String, password: String, confirmPassword: String) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeContentPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("← Back")
        }
        Text("Create account", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text("Sign up to get started", color = TextSubtitle, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))

        LabeledTextField(name, { name = it }, "Name")
        LabeledTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        LabeledTextField(password, { password = it }, "Password", isPassword = true)
        LabeledTextField(confirmPassword, { confirmPassword = it }, "Confirm password", isPassword = true)

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton("Sign Up", onClick = { onSubmit(name, email, password, confirmPassword) })
        LinkRow("Already have an account", "Sign In", onNavigateToSignIn)
    }
}
