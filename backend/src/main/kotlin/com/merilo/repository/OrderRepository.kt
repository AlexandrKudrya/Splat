package com.merilo.repository

import com.merilo.model.OrderEntity
import com.merilo.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<OrderEntity, Long> {
    fun findAllByCreatorId(creatorId: Long): List<OrderEntity>
    fun findAllByStatus(status: OrderStatus): List<OrderEntity>
}