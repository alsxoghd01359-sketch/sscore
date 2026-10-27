package com.sscore.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
class JwtConfig(@Value("\${sscore.jwt.secret:}") private val secret: String) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // JWT_SECRET이 없으면 실행할 때마다 새 키를 만든다. 서버를 다시 켜면 모든 로그인이 풀리므로 .env에 설정하는 것이 좋다.
    @Bean
    fun jwtKey(): SecretKey {
        val bytes = if (secret.toByteArray().size >= 32) {
            secret.toByteArray()
        } else {
            log.warn("JWT_SECRET이 없거나 32자보다 짧아 임시 키를 사용합니다. 서버를 다시 켜면 모든 로그인이 풀립니다.")
            ByteArray(32).also { SecureRandom().nextBytes(it) }
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))

    @Bean
    fun jwtDecoder(key: SecretKey, clock: Clock): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
        // 만료 검사에 주입된 Clock을 쓴다(테스트에서 시간을 앞당길 수 있다)
        decoder.setJwtValidator(JwtTimestampValidator(Duration.ZERO).also { it.setClock(clock) })
        return decoder
    }
}
