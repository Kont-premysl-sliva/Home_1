package com.example.data


data class EventRecord(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",
    val author: String = "",
    val depo: String = "Depo 1"
)
