package com.merilo.service

import com.merilo.dto.AddParticipantsRequest
import com.merilo.dto.AddParticipantsResponse
import com.merilo.dto.ParticipantDto
import com.merilo.model.UserOrderEntity
import com.merilo.model.UserOrderStatus
import com.merilo.repository.OrderRepository
import com.merilo.repository.UserOrderRepository
import com.merilo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderParticipantService(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val userOrderRepository: UserOrderRepository
) {

    @Transactional
    fun addParticipants(
        currentUserId: Long,
        orderId: Long,
        request: AddParticipantsRequest
    ): AddParticipantsResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("Order not found") }

        if (order.creator.id != currentUserId) {
            throw IllegalArgumentException("Only creator can add participants")
        }

        val requestedIds = request.userIds
            .distinct()
            .filter { it > 0 }

        if (requestedIds.isEmpty()) {
            return AddParticipantsResponse(
                added = 0,
                participants = loadParticipants(orderId)
            )
        }

        val users = userRepository.findAllById(requestedIds)
        val foundIds = users.mapNotNull { it.id }.toSet()

        if (foundIds.size != requestedIds.size) {
            val missingIds = requestedIds.filter { it !in foundIds }
            throw IllegalArgumentException("Users not found: $missingIds")
        }

        var added = 0

        for (user in users) {
            val userId = user.id ?: continue

            val exists = userOrderRepository.existsByOrderIdAndUserId(orderId, userId)
            if (!exists) {
                userOrderRepository.save(
                    UserOrderEntity(
                        user = user,
                        order = order,
                        status = UserOrderStatus.PENDING
                    )
                )
                added++
            }
        }

        return AddParticipantsResponse(
            added = added,
            participants = loadParticipants(orderId)
        )
    }

    private fun loadParticipants(orderId: Long): List<ParticipantDto> {
        return userOrderRepository.findAllByOrderId(orderId)
            .map { userOrder ->
                ParticipantDto(
                    userId = userOrder.user.id!!,
                    username = userOrder.user.username,
                    status = userOrder.status.name.lowercase()
                )
            }
    }
}