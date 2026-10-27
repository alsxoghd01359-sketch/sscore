package com.sscore.entity

import jakarta.persistence.*

@Entity
@Table(name = "leagues")
data class LeagueEntity(
    @Id
    @Column(name = "league_id")
    val leagueId: Int,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(length = 50)
    val country: String?,

    @Column(name = "logo_url", length = 255)
    val logoUrl: String?,

    @Column(name = "league_type", length = 20)
    val leagueType: String?   // "League" | "Cup"
)