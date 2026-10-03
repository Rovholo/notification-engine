package com.bitkulcha.notification_engine.config;

import com.bitkulcha.notification_engine.controller.NotificationController;
import com.bitkulcha.notification_engine.domain.model.Role;
import com.bitkulcha.notification_engine.security.AccessToken;
import com.bitkulcha.notification_engine.security.JwtAuthenticationFilter;
import com.bitkulcha.notification_engine.security.JwtService;
import com.bitkulcha.notification_engine.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
class SecurityConfigTest {

    private static final String EMAIL_REQUEST =
            "{\"type\":\"EMAIL\",\"message\":{\"to\":[\"a@b.com\"],\"subject\":\"Hi\",\"body\":\"Hello\"}}";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, NotificationController.class})
    static class TestConfig {
        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(jwtService, notificationService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void notification_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/notification").contentType(MediaType.APPLICATION_JSON).content(EMAIL_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void notification_asUserWithoutAdminRole_isForbidden() throws Exception {
        when(jwtService.validate("user-token")).thenReturn(Optional.of(new AccessToken(UUID.randomUUID(), Set.of())));

        mockMvc.perform(post("/notification")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }

    @Test
    void notification_asAdmin_isAllowed() throws Exception {
        when(jwtService.validate("admin-token"))
                .thenReturn(Optional.of(new AccessToken(UUID.randomUUID(), Set.of(Role.ADMIN))));

        mockMvc.perform(post("/notification")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST))
                .andExpect(status().isOk());

        verify(notificationService).sendEmailMessage(any());
    }
}
