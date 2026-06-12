package com.tneff.cyppie.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.tneff.cyppie.feature.auth.data.InMemoryUserRepository
import com.tneff.cyppie.feature.auth.model.User

/** Which screen of the unauthenticated flow is currently shown. */
enum class AuthRoute { HOME, SIGN_UP, SIGN_IN }

data class AuthUiState(
    val route: AuthRoute = AuthRoute.HOME,
    val currentUser: User? = null,
    val error: String? = null,
)

/**
 * Drives the auth flow: simple state-based navigation plus an in-memory user store.
 * A single instance lives at the app root, so the registered users persist for the
 * whole session.
 */
class AuthViewModel : ViewModel() {
    private val repository = InMemoryUserRepository()

    var uiState by mutableStateOf(AuthUiState())
        private set

    fun goTo(route: AuthRoute) {
        uiState = uiState.copy(route = route, error = null)
    }

    fun submitSignUp(name: String, email: String, password: String, confirmPassword: String) {
        val validationError = validateSignUp(name, email, password, confirmPassword)
        if (validationError != null) {
            uiState = uiState.copy(error = validationError)
            return
        }
        repository.register(name, email, password)
            .onSuccess { user -> uiState = uiState.copy(currentUser = user, error = null) }
            .onFailure { uiState = uiState.copy(error = it.message ?: "Registration failed.") }
    }

    fun submitSignIn(email: String, password: String) {
        val validationError = validateSignIn(email, password)
        if (validationError != null) {
            uiState = uiState.copy(error = validationError)
            return
        }
        repository.login(email, password)
            .onSuccess { user -> uiState = uiState.copy(currentUser = user, error = null) }
            .onFailure { uiState = uiState.copy(error = it.message ?: "Sign in failed.") }
    }

    fun logout() {
        uiState = AuthUiState()
    }

    private fun validateSignUp(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): String? = when {
        name.isBlank() -> "Please enter your name."
        !isValidEmail(email) -> "Please enter a valid email address."
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
        password != confirmPassword -> "Passwords do not match."
        else -> null
    }

    private fun validateSignIn(email: String, password: String): String? = when {
        !isValidEmail(email) -> "Please enter a valid email address."
        password.isEmpty() -> "Please enter your password."
        else -> null
    }

    private fun isValidEmail(email: String): Boolean =
        EMAIL_REGEX.matches(email.trim())

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
