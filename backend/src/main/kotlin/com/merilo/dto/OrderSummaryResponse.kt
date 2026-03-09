package com.merilo.dto

data class OrderSummaryResponse(
    val orderId: Long,
    val participants: List<ParticipantSummaryDto>,
    val paymentMethods: Map<String, String>
)

data class ParticipantSummaryDto(
    val userId: Long,
    val username: String?,
    val amountDue: Double,
    val status: String
)