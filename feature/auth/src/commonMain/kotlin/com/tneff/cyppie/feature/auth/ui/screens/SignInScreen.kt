package com.tneff.cyppie.feature.auth.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tneff.cyppie.feature.auth.ui.components.BrandLogo
import com.tneff.cyppie.feature.auth.ui.components.OutlinedInputField
import com.tneff.cyppie.feature.auth.ui.components.PrimaryButton
import com.tneff.cyppie.feature.auth.ui.theme.CryptasaBlue
import com.tneff.cyppie.feature.auth.ui.theme.CryptasaGreen
import com.tneff.cyppie.feature.auth.ui.theme.DividerGray
import com.tneff.cyppie.feature.auth.ui.theme.FieldBorder
import com.tneff.cyppie.feature.auth.ui.theme.TextSubtitle

/**
 * Sign-in screen (Figma node 0:7175): small gradient hero with the brand logo and a
 * circular back button, then a white rounded card with the form (email + password,
 * remember-me / recover-password row, primary button, "or" divider, QR-code button).
 * "Remember me", "Recover password" and the QR button are visual only for now.
 */
@Composable
fun SignInScreen(
    error: String?,
    onSubmit: (email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            // Hero band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Brush.linearGradient(listOf(CryptasaGreen, CryptasaBlue))),
                contentAlignment = Alignment.Center,
            ) {
                BrandLogo()
            }

            // White card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-20).dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sign in", fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter your details to proceed further",
                    color = TextSubtitle,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                OutlinedInputField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "catherine.shaw@gmail.com",
                    keyboardType = KeyboardType.Email,
                    trailingContent = {
                        Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = Color.Black)
                    },
                )
                Spacer(Modifier.height(16.dp))
                OutlinedInputField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Enter your password",
                    isPassword = true,
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                        Text("Remember me", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSubtitle)
                    }
                    Text("Recover password", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(20.dp))
                PrimaryButton("Sign in", onClick = { onSubmit(email, password) })

                Spacer(Modifier.height(20.dp))
                OrDivider()
                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {},
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, FieldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Outlined.QrCode2, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log in with QR code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Circular back button over the hero
        Box(Modifier.safeContentPadding().padding(start = 20.dp, top = 8.dp)) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.size(40.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = DividerGray)
        Text("or", color = TextSubtitle, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = DividerGray)
    }
}
