package com.merilo.dto

import com.fasterxml.jackson.databind.JsonNode
import com.merilo.model.OrderStatus
import java.time.OffsetDateTime

data class OrderResponse(
    val id: Long,
    val status: OrderStatus,
    val creatorId: Long,
    val orderInfo: JsonNode,
    val participants: List<OrderParticipantResponse>,
    val createdAt: OffsetDateTime
)

data class OrderParticipantResponse(
    val userId: Long,
    val username: String?,
    val status: String
)