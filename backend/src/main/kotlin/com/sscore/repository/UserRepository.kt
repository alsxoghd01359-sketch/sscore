package com.sscore.repository

import com.sscore.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Int> {
    // 이메일로 기존 가입자가 있는지 찾는 기능 추가
    fun findByEmail(email: String): User?
}