package com.gymsmart.gymsmart

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform