package com.moe.puzzle

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform