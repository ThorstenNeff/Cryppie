package com.tneff.cyppie.feature.auth

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tneff.cyppie.feature.auth.ui.screens.HomeScreen
import com.tneff.cyppie.feature.auth.ui.screens.LoggedInScreen
import com.tneff.cyppie.feature.auth.ui.screens.SignInScreen
import com.tneff.cyppie.feature.auth.ui.screens.SignUpScreen
import com.tneff.cyppie.feature.auth.ui.theme.CryptasaTheme

/**
 * Public entry point of the auth feature. Hosts the theme, the [AuthViewModel] and
 * the state-based routing between the auth screens. Drop this into any app target.
 */
@Composable
fun AuthRoot(viewModel: AuthViewModel = viewModel { AuthViewModel() }) {
    CryptasaTheme {
        val state = viewModel.uiState
        val user = state.currentUser
        if (user != null) {
            LoggedInScreen(
                userName = user.name.ifBlank { user.email },
                onLogout = viewModel::logout,
            )
        } else {
            when (state.route) {
                AuthRoute.HOME -> HomeScreen(
                    onSignUp = { viewModel.goTo(AuthRoute.SIGN_UP) },
                    onSignIn = { viewModel.goTo(AuthRoute.SIGN_IN) },
                )

                AuthRoute.SIGN_UP -> SignUpScreen(
                    error = state.error,
                    onSubmit = viewModel::submitSignUp,
                    onNavigateToSignIn = { viewModel.goTo(AuthRoute.SIGN_IN) },
                    onBack = { viewModel.goTo(AuthRoute.HOME) },
                )

                AuthRoute.SIGN_IN -> SignInScreen(
                    error = state.error,
                    onSubmit = viewModel::submitSignIn,
                    onBack = { viewModel.goTo(AuthRoute.HOME) },
                )
            }
        }
    }
}
