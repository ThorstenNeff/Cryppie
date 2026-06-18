package com.tneff.cyppie

// Web is read-only / onboarding-only — no persisted wallet seed (no :storage).
internal actual suspend fun walletExists(): Boolean = false
