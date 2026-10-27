package com.sscore.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "champions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["league_id", "season"])]
)
data class ChampionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "league_id", nullable = false)
    val leagueId: Int,

    @Column(nullable = false)
    val season: Int,

    @Column(name = "team_id", nullable = false)
    val teamId: Int,

    @Column(nullable = false)
    val teamName: String,

    val country: String? = null
)