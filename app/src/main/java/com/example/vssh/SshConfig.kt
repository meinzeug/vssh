package com.example.vssh

data class SshConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)
