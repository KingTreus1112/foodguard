package com.example.foodguard3

import com.google.firebase.Timestamp

data class FoodItem(
    val id: String = "",
    val name: String = "",
    val category: String = "Others",
    val expiry: Timestamp? = null,
    val status: String = "active"
)
