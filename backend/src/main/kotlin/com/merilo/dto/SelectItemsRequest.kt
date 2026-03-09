package com.merilo.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SelectItemsRequest(
    val selections: List<ItemSelectionDto>
)

data class ItemSelectionDto(
    @JsonProperty("item_id")
    val itemId: Long,
    val quantity: Double
)