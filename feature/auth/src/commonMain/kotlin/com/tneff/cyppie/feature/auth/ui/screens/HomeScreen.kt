package com.tneff.cyppie.feature.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tneff.cyppie.feature.auth.ui.components.BrandLogo
import com.tneff.cyppie.feature.auth.ui.components.LinkRow
import com.tneff.cyppie.feature.auth.ui.theme.CryptasaBlue
import com.tneff.cyppie.feature.auth.ui.theme.CryptasaGreen
import com.tneff.cyppie.feature.auth.ui.theme.DividerGray
import com.tneff.cyppie.feature.auth.ui.theme.SoftWhite
import com.tneff.cyppie.feature.auth.ui.theme.TextSubtitle

/**
 * Landing screen of the auth flow (Figma node 0:7202). Hero with a gradient,
 * logo and testimonial on top; a white rounded card with the two entry links at
 * the bottom. The mesh-gradient background is approximated with a linear gradient.
 */
@Composable
fun HomeScreen(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        // Hero
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .background(Brush.linearGradient(listOf(CryptasaGreen, CryptasaBlue))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeContentPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                BrandLogo()
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "“Enjoy the world’s largest cryptocurrency exchange at your fingertips”",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                CarouselDots(activeIndex = 0, count = 3)
                Spacer(Modifier.height(32.dp))
                Text("Waiapi Karaka", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Financial Officer", color = SoftWhite, fontSize = 14.sp)
            }
        }

        // Bottom card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .safeContentPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Buy and trade cryptocurrencies",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter your details to proceed further",
                color = TextSubtitle,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = DividerGray)
            LinkRow("Don’t have an account", "Sign Up", onSignUp)
            HorizontalDivider(color = DividerGray)
            LinkRow("Already have an account", "Sign In", onSignIn, actionColor = Color.Black)
            HorizontalDivider(color = DividerGray)
        }
    }
}

@Composable
private fun CarouselDots(activeIndex: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val active = index == activeIndex
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else SoftWhite),
            )
        }
    }
}
