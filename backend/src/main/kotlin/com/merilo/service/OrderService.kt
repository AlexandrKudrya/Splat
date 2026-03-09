package com.merilo.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.merilo.common.exception.BadRequestException
import com.merilo.common.exception.NotFoundException
import com.merilo.dto.CreateOrderRequest
import com.merilo.dto.OrderParticipantResponse
import com.merilo.dto.OrderResponse
import com.merilo.dto.OrderSummaryResponse
import com.merilo.dto.ParticipantSummaryDto
import com.merilo.dto.SelectItemsRequest
import com.merilo.dto.UpdateOrderItemsRequest
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
    private val userOrderRepository: UserOrderRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun createOrder(currentUserId: Long, request: CreateOrderRequest): OrderResponse {
        val creator = userRepository.findById(currentUserId)
            .orElseThrow { NotFoundException("User not found: $currentUserId") }

        val participantIds = (request.participantIds + currentUserId).distinct()
        val users = userRepository.findAllById(participantIds)

        if (users.size != participantIds.size) {
            throw NotFoundException("Some participants were not found")
        }

        val order = orderRepository.save(
            OrderEntity(
                creator = creator,
                status = OrderStatus.DRAFT,
                orderInfo = JsonNodeFactory.instance.arrayNode(),
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now()
            )
        )

        val userOrders = users.map { user ->
            UserOrderEntity(
                user = user,
                order = order,
                status = UserOrderStatus.PENDING
            )
        }

        val savedUserOrders = userOrderRepository.saveAll(userOrders)

        return mapToResponse(order, savedUserOrders)
    }

    fun getOrder(currentUserId: Long, orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order not found: $orderId") }

        val hasAccess = userOrderRepository.existsByOrderIdAndUserId(orderId, currentUserId) ||
                order.creator.id == currentUserId

        if (!hasAccess) {
            throw BadRequestException("Access denied to order: $orderId")
        }

        val userOrders = userOrderRepository.findAllByOrderId(orderId)

        return mapToResponse(order, userOrders)
    }

    @Transactional
    fun updateItems(
        currentUserId: Long,
        orderId: Long,
        request: UpdateOrderItemsRequest
    ): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order not found: $orderId") }

        if (order.creator.id != currentUserId) {
            throw BadRequestException("Only creator can update order items")
        }

        validateItems(request)

        order.orderInfo = objectMapper.valueToTree(request.items)
        order.status = OrderStatus.PENDING
        order.updatedAt = OffsetDateTime.now()

        val savedOrder = orderRepository.save(order)
        val userOrders = userOrderRepository.findAllByOrderId(orderId)

        return mapToResponse(savedOrder, userOrders)
    }

    fun getMyOrders(currentUserId: Long): List<OrderResponse> {
        val userOrders = userOrderRepository.findAllByUserId(currentUserId)

        return userOrders.map { userOrder ->
            val order = userOrder.order
            val participants = userOrderRepository.findAllByOrderId(order.id!!)

            mapToResponse(order, participants)
        }
    }

    private fun validateItems(request: UpdateOrderItemsRequest) {
        if (request.items.isEmpty()) {
            throw BadRequestException("Items must not be empty")
        }

        val itemIds = mutableSetOf<Long>()

        request.items.forEach { item ->
            if (item.id <= 0) {
                throw BadRequestException("Item id must be positive")
            }
            if (!itemIds.add(item.id)) {
                throw BadRequestException("Duplicate item id: ${item.id}")
            }
            if (item.name.isBlank()) {
                throw BadRequestException("Item name must not be blank")
            }
            if (item.price < 0) {
                throw BadRequestException("Item price must not be negative")
            }
            if (item.quantity <= 0.0) {
                throw BadRequestException("Item quantity must be greater than zero")
            }

            var splitSum = 0.0
            item.splits.forEach { split ->
                if (split.userId <= 0) {
                    throw BadRequestException("Split userId must be positive")
                }
                if (split.quantity <= 0.0) {
                    throw BadRequestException("Split quantity must be greater than zero")
                }
                splitSum += split.quantity
            }

            if (item.splits.isNotEmpty() && splitSum > item.quantity + 1e-9) {
                throw BadRequestException("Splits exceed item quantity for item id ${item.id}")
            }
        }
    }

    private fun mapToResponse(order: OrderEntity, userOrders: List<UserOrderEntity>): OrderResponse {
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

    @Transactional
    fun selectItems(
        currentUserId: Long,
        orderId: Long,
        request: SelectItemsRequest
    ): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order not found: $orderId") }

        val isParticipant = userOrderRepository.existsByOrderIdAndUserId(orderId, currentUserId)
        if (!isParticipant) {
            throw BadRequestException("User is not a participant of order: $orderId")
        }

        if (order.status != OrderStatus.PENDING && order.status != OrderStatus.ACTIVE) {
            throw BadRequestException("Items can be selected only for PENDING or ACTIVE orders")
        }

        validateSelections(request)

        val itemsArray = order.orderInfo.deepCopy<com.fasterxml.jackson.databind.node.ArrayNode>()
        val selectionsByItemId = request.selections.associateBy { it.itemId }

        val existingItemIds = itemsArray.mapNotNull { it.get("id")?.asLong() }.toSet()

        request.selections.forEach { selection ->
            if (selection.itemId !in existingItemIds) {
                throw BadRequestException("Item not found: ${selection.itemId}")
            }
        }

        itemsArray.forEach { itemNode ->
            val itemObject = itemNode as com.fasterxml.jackson.databind.node.ObjectNode
            val itemId = itemObject.get("id").asLong()
            val itemQuantity = itemObject.get("quantity").asDouble()

            val splitsNode = itemObject.get("splits")
            val splitsArray = when {
                splitsNode == null || !splitsNode.isArray ->
                    objectMapper.createArrayNode()
                else ->
                    splitsNode.deepCopy<com.fasterxml.jackson.databind.node.ArrayNode>()
            }

            val filteredSplits = objectMapper.createArrayNode()
            splitsArray.forEach { split ->
                val splitUserId = split.get("user_id")?.asLong()
                if (splitUserId != currentUserId) {
                    filteredSplits.add(split)
                }
            }

            val newSelection = selectionsByItemId[itemId]
            if (newSelection != null && newSelection.quantity > 0.0) {
                val newSplit = objectMapper.createObjectNode()
                newSplit.put("user_id", currentUserId)
                newSplit.put("quantity", newSelection.quantity)
                filteredSplits.add(newSplit)
            }

            var totalSelected = 0.0
            filteredSplits.forEach { split ->
                totalSelected += split.get("quantity").asDouble()
            }

            if (totalSelected > itemQuantity + 1e-9) {
                throw BadRequestException("Selected quantity exceeds available quantity for item id $itemId")
            }

            itemObject.set<com.fasterxml.jackson.databind.JsonNode>("splits", filteredSplits)
        }

        order.orderInfo = itemsArray
        order.updatedAt = OffsetDateTime.now()

        val userOrders = userOrderRepository.findAllByOrderId(orderId)

        val currentUserOrder = userOrders.firstOrNull { it.user.id == currentUserId }
            ?: throw BadRequestException("User is not linked to order: $orderId")

        currentUserOrder.status = UserOrderStatus.CONFIRMED
        userOrderRepository.save(currentUserOrder)

        val refreshedUserOrders = userOrderRepository.findAllByOrderId(orderId)
        val allConfirmedOrPaid = refreshedUserOrders.all {
            it.status == UserOrderStatus.CONFIRMED || it.status == UserOrderStatus.PAID
        }

        if (allConfirmedOrPaid) {
            order.status = OrderStatus.ACTIVE
        } else {
            order.status = OrderStatus.PENDING
        }

        val savedOrder = orderRepository.save(order)
        return mapToResponse(savedOrder, refreshedUserOrders)
    }

    private fun validateSelections(request: SelectItemsRequest) {
        val itemIds = mutableSetOf<Long>()

        request.selections.forEach { selection ->
            if (selection.itemId <= 0) {
                throw BadRequestException("itemId must be positive")
            }
            if (!itemIds.add(selection.itemId)) {
                throw BadRequestException("Duplicate selection for itemId ${selection.itemId}")
            }
            if (selection.quantity <= 0.0) {
                throw BadRequestException("Selection quantity must be greater than zero")
            }
        }
    }

    fun getOrderSummary(currentUserId: Long, orderId: Long): OrderSummaryResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order not found: $orderId") }

        val hasAccess = userOrderRepository.existsByOrderIdAndUserId(orderId, currentUserId) ||
                order.creator.id == currentUserId

        if (!hasAccess) {
            throw BadRequestException("Access denied to order: $orderId")
        }

        val userOrders = userOrderRepository.findAllByOrderId(orderId)

        val amountByUserId = mutableMapOf<Long, Long>() // в копейках

        val items = order.orderInfo
        if (items.isArray) {
            items.forEach { item ->
                val price = item.get("price")?.asLong() ?: 0L
                val splits = item.get("splits")

                if (splits != null && splits.isArray) {
                    splits.forEach { split ->
                        val splitUserId = split.get("user_id")?.asLong()
                            ?: split.get("userId")?.asLong()
                            ?: throw BadRequestException("Split user_id is missing")

                        val splitQuantity = split.get("quantity")?.asDouble()
                            ?: throw BadRequestException("Split quantity is missing")

                        val amount = (price * splitQuantity).toLong()
                        amountByUserId[splitUserId] = (amountByUserId[splitUserId] ?: 0L) + amount
                    }
                }
            }
        }

        val participants = userOrders.map { userOrder ->
            val uid = userOrder.user.id!!
            ParticipantSummaryDto(
                userId = uid,
                username = userOrder.user.username,
                amountDue = (amountByUserId[uid] ?: 0L) / 100.0,
                status = userOrder.status.name.lowercase()
            )
        }

        @Suppress("UNCHECKED_CAST")
        val paymentMethods = order.creator.paymentMethods ?: emptyMap<String, String>()

        return OrderSummaryResponse(
            orderId = order.id!!,
            participants = participants,
            paymentMethods = paymentMethods
        )
    }

}