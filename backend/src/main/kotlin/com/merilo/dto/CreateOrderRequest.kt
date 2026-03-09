package com.merilo.dto

data class CreateOrderRequest(
    val participantIds: List<Long> = emptyList(),
    val tripId: Long? = null
)