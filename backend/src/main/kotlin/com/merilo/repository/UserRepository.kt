package com.merilo.repository

import com.merilo.model.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository: JpaRepository<UserEntity, Long> {
    fun findByTelegramId(telegramId: Long):UserEntity?
}