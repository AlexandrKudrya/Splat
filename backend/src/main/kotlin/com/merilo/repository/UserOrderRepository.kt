package com.merilo.repository

import com.merilo.model.UserOrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserOrderRepository : JpaRepository<UserOrderEntity, Long> {
    fun findAllByOrderId(orderId: Long): List<UserOrderEntity>
    fun findAllByUserId(userId: Long): List<UserOrderEntity>
    fun existsByOrderIdAndUserId(orderId: Long, userId: Long): Boolean
}