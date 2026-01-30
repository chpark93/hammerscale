package com.ch.hammerscale.controller.infrastructure.security

import com.ch.hammerscale.controller.domain.port.out.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ").trim()
            val payload = jwtService.parseAccessToken(
                token = token
            )

            if (payload != null) {
                val user = userRepository.findById(
                    id = payload.userId
                )

                if (user != null) {
                    val auth = UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                    )

                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
