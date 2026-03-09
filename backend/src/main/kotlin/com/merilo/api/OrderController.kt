package com.merilo.api

import com.merilo.dto.CreateOrderRequest
import com.merilo.dto.OrderResponse
import com.merilo.service.OrderService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
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
    private val orderService: OrderService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(
        @RequestAttribute("userId") userId: Long,
        @RequestBody request: CreateOrderRequest
    ): OrderResponse {
        return orderService.createOrder(userId, request)
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: Long): OrderResponse {
        return orderService.getOrder(id)
    }
}