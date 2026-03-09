package com.merilo.api

import com.merilo.dto.AddParticipantsRequest
import com.merilo.dto.AddParticipantsResponse
import com.merilo.dto.CreateOrderRequest
import com.merilo.dto.OrderResponse
import com.merilo.dto.OrderSummaryResponse
import com.merilo.dto.SelectItemsRequest
import com.merilo.dto.UpdateOrderItemsRequest
import com.merilo.service.OrderParticipantService
import com.merilo.service.OrderService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderParticipantService: OrderParticipantService
) {
    private val log = LoggerFactory.getLogger(OrderController::class.java)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(
        @RequestAttribute("userId") userId: Long,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        log.info("Creating order for user $userId")
        return orderService.createOrder(userId, request)
    }

    @GetMapping("/{id}")
    fun getOrder(
        @RequestAttribute("userId") userId: Long,
        @PathVariable id: Long
    ): OrderResponse {
        return orderService.getOrder(userId, id)
    }

    @GetMapping("/my")
    fun getMyOrders(
        @RequestAttribute("userId") userId: Long
    ): List<OrderResponse> {
        return orderService.getMyOrders(userId)
    }

    @PostMapping("/{id}/participants")
    fun addParticipants(
        @RequestAttribute("userId") userId: Long,
        @PathVariable id: Long,
        @RequestBody body: AddParticipantsRequest
    ): AddParticipantsResponse {
        return orderParticipantService.addParticipants(userId, id, body)
    }

    @PatchMapping("/{id}/items")
    fun updateItems(
        @RequestAttribute("userId") userId: Long,
        @PathVariable id: Long,
        @RequestBody body: UpdateOrderItemsRequest
    ): OrderResponse {
        return orderService.updateItems(userId, id, body)
    }

    @PostMapping("/{id}/select")
    fun selectItems(
        @RequestAttribute("userId") userId: Long,
        @PathVariable id: Long,
        @RequestBody body: SelectItemsRequest
    ): OrderResponse {
        return orderService.selectItems(userId, id, body)
    }

    @GetMapping("/{id}/summary")
    fun getOrderSummary(
        @RequestAttribute("userId") userId: Long,
        @PathVariable id: Long
    ): OrderSummaryResponse {
        return orderService.getOrderSummary(userId, id)
    }
}