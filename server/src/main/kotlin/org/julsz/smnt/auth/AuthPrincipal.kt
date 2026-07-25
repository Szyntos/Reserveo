package org.julsz.smnt.auth

data class AuthPrincipal(
    val userId: Int,
    val name: String,
    val email: String,
    val appRole: String
)
