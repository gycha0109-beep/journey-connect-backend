package com.jc.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@CanonicalPostgresTest
@AutoConfigureMockMvc
class SecurityErrorResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedApiReturnsUnifiedUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }


    @Test
    void myPostsPathDoesNotFallThroughToPublicUserPostsMatcher() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void publicKotlinControllerIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/test/welcome").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
