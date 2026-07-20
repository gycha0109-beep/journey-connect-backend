package com.jc.backend.domain.test.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/test")
class TestController {

    /** 프론트엔드에서 백엔드 연결과 언어 파라미터 전달을 확인하기 위한 공개 점검 API입니다. */
    @GetMapping("/welcome")
    fun getWelcomeMessage(
        @RequestParam(value = "lang", defaultValue = "ko") lang: String,
    ): ResponseEntity<Map<String, String>> {
        val message = when (lang) {
            "en" -> "Welcome to Journey Connect! Backend connection is successful."
            else -> "Journey Connect 백엔드 연결에 성공했습니다."
        }

        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "message" to message,
                "serverTime" to LocalDateTime.now().toString(),
            ),
        )
    }
}
