package com.sscore.entity

import jakarta.persistence.*

@Entity
@Table(name = "player_season_stats")
data class PlayerSeasonStatEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "player_id", nullable = false)
    val playerId: Long,

    val season: Int,
    val teamName: String,
    val leagueName: String,

    val appearances: Int,
    val goals: Int,
    val assists: Int,
    val minutesPlayed: Int,
    val rating: Double?,
    val yellowCards: Int,
    val redCards: Int
)