package com.tneff.cyppie.feature.auth.data

import com.tneff.cyppie.feature.auth.model.User

/**
 * In-memory user store. No persistence — all registered users are lost when the
 * app process ends. Emails are stored and matched case-insensitively.
 */
class InMemoryUserRepository {
    private val users = mutableListOf<User>()

    fun register(name: String, email: String, password: String): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        if (users.any { it.email == normalizedEmail }) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }
        val user = User(name = name.trim(), email = normalizedEmail, password = password)
        users.add(user)
        return Result.success(user)
    }

    fun login(email: String, password: String): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        val user = users.firstOrNull { it.email == normalizedEmail }
            ?: return Result.failure(IllegalStateException("No account found for this email."))
        if (user.password != password) {
            return Result.failure(IllegalStateException("Incorrect password."))
        }
        return Result.success(user)
    }
}
