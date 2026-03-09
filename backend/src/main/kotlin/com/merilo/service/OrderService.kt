package com.merilo.service

import com.merilo.dto.CreateOrderRequest
import com.merilo.dto.OrderParticipantResponse
import com.merilo.dto.OrderResponse
import com.merilo.model.OrderEntity
import com.merilo.model.OrderStatus
import com.merilo.model.UserOrderEntity
import com.merilo.model.UserOrderStatus
import com.merilo.repository.OrderRepository
import com.merilo.repository.UserOrderRepository
import com.merilo.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val userOrderRepository: UserOrderRepository
) {

    @Transactional
    fun createOrder(currentUserId: Long, request: CreateOrderRequest): OrderResponse {
        val creator = userRepository.findById(currentUserId)
            .orElseThrow { RuntimeException("User not found") }

        val order = orderRepository.save(
            OrderEntity(
                creator = creator,
                status = OrderStatus.DRAFT,
                orderInfo = "[]",
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now()
            )
        )

        val participantIds = (request.participantIds + currentUserId).distinct()

        val participants = participantIds.map { userId ->
            val user = userRepository.findById(userId)
                .orElseThrow { RuntimeException("Participant $userId not found") }

            userOrderRepository.save(
                UserOrderEntity(
                    user = user,
                    order = order,
                    status = UserOrderStatus.PENDING
                )
            )
        }

        return mapToResponse(order, participants.map { it.user.id!! })
    }

    fun getOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { RuntimeException("Order not found") }

        val userOrders = userOrderRepository.findAllByOrderId(orderId)

        return OrderResponse(
            id = order.id!!,
            status = order.status,
            creatorId = order.creator.id!!,
            orderInfo = order.orderInfo,
            participants = userOrders.map {
                OrderParticipantResponse(
                    userId = it.user.id!!,
                    username = it.user.username,
                    status = it.status.name
                )
            },
            createdAt = order.createdAt
        )
    }

    private fun mapToResponse(order: OrderEntity, participantIds: List<Long>): OrderResponse {
        val participants = participantIds.map { userId ->
            val user = userRepository.findById(userId)
                .orElseThrow { RuntimeException("User not found") }

            OrderParticipantResponse(
                userId = user.id!!,
                username = user.username,
                status = UserOrderStatus.PENDING.name
            )
        }

        return OrderResponse(
            id = order.id!!,
            status = order.status,
            creatorId = order.creator.id!!,
            orderInfo = order.orderInfo,
            participants = participants,
            createdAt = order.createdAt
        )
    }
}