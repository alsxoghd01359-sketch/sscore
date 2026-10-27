package com.sscore.entity

import jakarta.persistence.*

@Entity
@Table(name = "players")
data class PlayerEntity(
    @Id
    val playerId: Long,

    @Column(nullable = false)
    val name: String,

    val nationality: String?,
    val photoUrl: String?,
    val position: String?,
    val age: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    val team: TeamEntity?
)