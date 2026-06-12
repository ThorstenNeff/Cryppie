package com.tneff.cyppie.feature.auth.model

/**
 * A registered user. Held only in memory for the duration of the app session.
 */
data class User(
    val name: String,
    val email: String,
    val password: String,
)
