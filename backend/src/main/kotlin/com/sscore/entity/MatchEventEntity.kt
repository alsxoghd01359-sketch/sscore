package com.sscore.entity

import jakarta.persistence.*

@Entity
@Table(name = "match_events")
class MatchEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null

    // 🌟 원본 MatchEntity의 fixtureId 타입(BIGINT)과 완벽히 일치하도록 Long으로 세팅
    @Column(name = "fixture_id", nullable = false)
    var fixtureId: Long = 0L

    @Column(name = "time")
    var time: Int = 0

    @Column(name = "extra_time")
    var extraTime: Int? = null

    @Column(name = "team_id")
    var teamId: Int = 0

    @Column(name = "team_name")
    var teamName: String = ""

    @Column(name = "type")
    var type: String = ""

    @Column(name = "detail")
    var detail: String? = null

    @Column(name = "player_name")
    var playerName: String? = null

    @Column(name = "assist_player_name")
    var assistPlayerName: String? = null

    // JPA 필수 기본 생성자 보완용 보조 생성자
    constructor()

    constructor(
        fixtureId: Long, time: Int, extraTime: Int?, teamId: Int, teamName: String,
        type: String, detail: String?, playerName: String?, assistPlayerName: String?
    ) {
        this.fixtureId = fixtureId
        this.time = time
        this.extraTime = extraTime
        this.teamId = teamId
        this.teamName = teamName
        this.type = type
        this.detail = detail
        this.playerName = playerName
        this.assistPlayerName = assistPlayerName
    }
}