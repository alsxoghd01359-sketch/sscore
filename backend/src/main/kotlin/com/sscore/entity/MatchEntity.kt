package com.sscore.entity

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty

@Entity
@Table(name = "matches")
data class MatchEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "fixture_id")
    @get:JsonProperty("fixtureId")
    val fixtureId: Long? = null,

    @Column(name = "match_round")
    @get:JsonProperty("matchRound")
    val matchRound: String? = null,

    @Column(name = "stadium")
    @get:JsonProperty("stadium")
    val stadium: String? = null,

    @Column(name = "home_team")
    @get:JsonProperty("homeTeam")
    val homeTeam: String,

    @Column(name = "away_team")
    @get:JsonProperty("awayTeam")
    val awayTeam: String,

    @Column(name = "home_team_id")
    @get:JsonProperty("homeTeamId")
    val homeTeamId: Int? = null,

    @Column(name = "away_team_id")
    @get:JsonProperty("awayTeamId")
    val awayTeamId: Int? = null,

    @Column(name = "match_date")
    @get:JsonProperty("matchDate")
    val matchDate: String,

    @Column(name = "match_time")
    @get:JsonProperty("matchTime")
    val matchTime: String,

    @Column(name = "score")
    @get:JsonProperty("score")
    val score: String,

    @Column(name = "league_id")
    @get:JsonProperty("leagueId")
    val leagueId: Int,

    @Column(name = "season")
    @get:JsonProperty("season")
    val season: Int,

    @Column(name = "referee")
    @get:JsonProperty("referee")
    val referee: String? = null
)