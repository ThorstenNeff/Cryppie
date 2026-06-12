package com.tneff.cyppie.feature.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tneff.cyppie.feature.auth.ui.theme.FieldBorder
import com.tneff.cyppie.feature.auth.ui.theme.TextSubtitle

private val COUNTRIES = listOf("United States", "Germany", "United Kingdom", "France", "Spain", "Other")

/**
 * Sign-up screen (Figma node 0:7152): gradient hero with the brand logo and a circular
 * back button, then a white rounded card with the form (country dropdown, email, password,
 * two agreement checkboxes, "Create account"). The country and the "email updates" checkbox
 * are visual only; the Terms-of-Service checkbox gates the button.
 */
@Composable
fun SignUpScreen(
    error: String?,
    onSubmit: (email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var country by remember { mutableStateOf(COUNTRIES.first()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailUpdates by remember { mutableStateOf(true) }
    var agreedToTerms by remember { mutableStateOf(false) }

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
                Text("Sign Up", fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Take the next step and sign up to your account",
                    color = TextSubtitle,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                CountryDropdown(selected = country, onSelected = { country = it })
                Spacer(Modifier.height(16.dp))
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
                CheckboxRow(
                    checked = emailUpdates,
                    onCheckedChange = { emailUpdates = it },
                    label = "I agree to receive email updates",
                    labelColor = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                CheckboxRow(
                    checked = agreedToTerms,
                    onCheckedChange = { agreedToTerms = it },
                    label = "I have read and agree to Terms of Service",
                    labelColor = TextSubtitle,
                )

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
                PrimaryButton(
                    text = "Create account",
                    onClick = { onSubmit(email, password) },
                    enabled = agreedToTerms,
                )
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
private fun CountryDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Country/Area of Residence", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
        Spacer(Modifier.height(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(1.dp, FieldBorder, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(selected, fontSize = 14.sp, color = Color.Black)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.Black)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                COUNTRIES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    labelColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = labelColor)
    }
}
