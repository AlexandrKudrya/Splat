package com.merilo.dto

data class AddParticipantsRequest(
    val userIds: List<Long>
)

data class AddParticipantsResponse(
    val added: Int,
    val participants: List<ParticipantDto>
)

data class ParticipantDto(
    val userId: Long,
    val username: String?,
    val status: String
)