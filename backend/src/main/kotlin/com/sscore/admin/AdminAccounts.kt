package com.sscore.admin

import com.sscore.member.MemberRepository
import com.sscore.member.MemberRole
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// ADMIN 계정은 .env의 ADMIN_EMAILS(쉼표로 구분)에 이메일을 적어 지정한다.
// 가입을 마칠 때 이 목록에 있으면 ADMIN이 되고, 이미 가입한 회원은 서버를 시작할 때 ADMIN으로 바뀐다.
@Component
class AdminAccounts(
    @Value("\${sscore.admin.emails:}") emails: String,
    private val members: MemberRepository
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val adminEmails: Set<String> =
        emails.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun roleFor(email: String): MemberRole =
        if (email.trim().lowercase() in adminEmails) MemberRole.ADMIN else MemberRole.USER

    @Transactional
    override fun run(args: ApplicationArguments) {
        adminEmails.forEach { email ->
            val member = members.findByEmail(email) ?: return@forEach
            if (member.role != MemberRole.ADMIN) {
                member.role = MemberRole.ADMIN
                log.info("ADMIN 권한을 부여했습니다: {}", email)
            }
        }
    }
}
