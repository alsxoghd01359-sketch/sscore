package com.sscore.search

import com.sscore.dto.SearchResultDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/soccer")
class SearchController(private val search: SearchService) {

    /** 통합 검색 (선수·팀·리그). 검색어가 2글자보다 짧으면 빈 목록 */
    @GetMapping("/search")
    fun search(@RequestParam q: String): List<SearchResultDto> = search.search(q)
}
