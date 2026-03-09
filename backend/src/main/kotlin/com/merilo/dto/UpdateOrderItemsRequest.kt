package com.merilo.dto

data class UpdateOrderItemsRequest(
    val items: List<OrderItemDto>
)

data class OrderItemDto(
    val id: Long,
    val name: String,
    val price: Long,
    val quantity: Double,
    val splits: List<SplitDto> = emptyList()
)

data class SplitDto(
    val userId: Long,
    val quantity: Double
)