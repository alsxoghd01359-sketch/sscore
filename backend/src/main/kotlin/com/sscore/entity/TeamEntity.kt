package com.sscore.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "teams")
data class TeamEntity(
    @Id
    @Column(name = "team_id")
    val teamId: Int,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(length = 50)
    val country: String?,

    @Column(name = "logo_url", length = 255)
    val logoUrl: String?
)