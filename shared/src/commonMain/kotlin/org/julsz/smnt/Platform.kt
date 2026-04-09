package org.julsz.smnt

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform