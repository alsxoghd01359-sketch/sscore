package com.sscore.config

import com.sscore.auth.SessionService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    // 조회 기능은 게스트도 쓸 수 있다. 개인화 기능과 관리 기능만 로그인이 필요하다.
    @Bean
    fun filterChain(
        http: HttpSecurity,
        sessions: SessionService
    ): SecurityFilterChain {
        val entryPoint = AuthenticationEntryPoint { _, response, _ ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = "${MediaType.APPLICATION_JSON_VALUE};charset=UTF-8"
            response.writer.write("""{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}""")
        }

        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                it.anyRequest().permitAll()
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .oauth2ResourceServer { rs ->
                rs.authenticationEntryPoint(entryPoint)
                rs.jwt { it.jwtAuthenticationConverter(sessionAwareConverter(sessions)) }
            }

        return http.build()
    }

    // 서명·만료가 맞는 토큰이라도 로그아웃 등으로 세션이 지워졌으면 거절한다.
    private fun sessionAwareConverter(sessions: SessionService) =
        Converter<Jwt, AbstractAuthenticationToken> { jwt ->
            val sid = jwt.getClaimAsString("sid")
            val memberId = jwt.subject?.toIntOrNull()
            if (sid == null || memberId == null || !sessions.isActive(sid, memberId)) {
                throw InvalidBearerTokenException("세션이 유효하지 않습니다.")
            }
            val role = jwt.getClaimAsString("role") ?: "USER"
            JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_$role")))
        }
}
