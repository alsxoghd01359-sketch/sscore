package com.sscore.entity

import jakarta.persistence.*

@Entity
@Table(name = "player_stats")
class PlayerStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null

    @Column(name = "fixture_id", nullable = false)
    var fixtureId: Long = 0L

    @Column(name = "team_id")
    var teamId: Int = 0

    @Column(name = "player_id")
    var playerId: Long = 0L

    @Column(name = "player_name")
    var playerName: String = ""

    @Column(name = "rating")
    var rating: Double? = null

    @Column(name = "position")
    var position: String? = null

    @Column(name = "minutes_played")
    var minutesPlayed: Int? = null

    @Column(name = "goals")
    var goals: Int = 0

    @Column(name = "assists")
    var assists: Int = 0

    @Column(name = "xg")                    // ← 추가
    var xg: Double? = null

    @Column(name = "passes_total")
    var passesTotal: Int = 0

    @Column(name = "passes_accuracy")
    var passesAccuracy: Int = 0

    @Column(name = "passes_key")
    var passesKey: Int = 0

    @Column(name = "shots_total")
    var shotsTotal: Int = 0

    @Column(name = "shots_on_target")
    var shotsOnTarget: Int = 0

    @Column(name = "shots_off_target")
    var shotsOffTarget: Int = 0

    @Column(name = "ball_touches")
    var ballTouches: Int = 0

    @Column(name = "turnovers")
    var turnovers: Int = 0

    @Column(name = "tackles_total")
    var tacklesTotal: Int = 0

    @Column(name = "tackles_blocks")
    var tacklesBlocks: Int = 0

    @Column(name = "tackles_interceptions")
    var tacklesInterceptions: Int = 0

    @Column(name = "duels_total")
    var duelsTotal: Int = 0

    @Column(name = "duels_won")
    var duelsWon: Int = 0

    @Column(name = "fouls_drawn")
    var foulsDrawn: Int = 0

    @Column(name = "fouls_committed")
    var foulsCommitted: Int = 0

    @Column(name = "goalkeeper_saves")
    var goalkeeperSaves: Int = 0

    @Column(name = "goalkeeper_conceded")
    var goalkeeperConceded: Int = 0

    @Column(name = "goalkeeper_saves_inside_box")
    var goalkeeperSavesInsideBox: Int = 0

    @Column(name = "goalkeeper_punches")
    var goalkeeperPunches: Int = 0

    constructor()

    constructor(
        fixtureId: Long, teamId: Int, playerId: Long, playerName: String, rating: Double?,
        position: String?, minutesPlayed: Int?, goals: Int, assists: Int, xg: Double?,
        passesTotal: Int, passesAccuracy: Int, passesKey: Int,
        shotsTotal: Int, shotsOnTarget: Int, shotsOffTarget: Int, ballTouches: Int,
        turnovers: Int, tacklesTotal: Int, tacklesBlocks: Int, tacklesInterceptions: Int,
        duelsTotal: Int, duelsWon: Int, foulsDrawn: Int, foulsCommitted: Int,
        goalkeeperSaves: Int, goalkeeperConceded: Int,
        goalkeeperSavesInsideBox: Int, goalkeeperPunches: Int
    ) {
        this.fixtureId = fixtureId; this.teamId = teamId; this.playerId = playerId
        this.playerName = playerName; this.rating = rating; this.position = position
        this.minutesPlayed = minutesPlayed; this.goals = goals; this.assists = assists
        this.xg = xg
        this.passesTotal = passesTotal; this.passesAccuracy = passesAccuracy
        this.passesKey = passesKey; this.shotsTotal = shotsTotal
        this.shotsOnTarget = shotsOnTarget; this.shotsOffTarget = shotsOffTarget
        this.ballTouches = ballTouches; this.turnovers = turnovers
        this.tacklesTotal = tacklesTotal; this.tacklesBlocks = tacklesBlocks
        this.tacklesInterceptions = tacklesInterceptions
        this.duelsTotal = duelsTotal; this.duelsWon = duelsWon
        this.foulsDrawn = foulsDrawn; this.foulsCommitted = foulsCommitted
        this.goalkeeperSaves = goalkeeperSaves; this.goalkeeperConceded = goalkeeperConceded
        this.goalkeeperSavesInsideBox = goalkeeperSavesInsideBox
        this.goalkeeperPunches = goalkeeperPunches
    }
}