package com.tneff.cyppie

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform