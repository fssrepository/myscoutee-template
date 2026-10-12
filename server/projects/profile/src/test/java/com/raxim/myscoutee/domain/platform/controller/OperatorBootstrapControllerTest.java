package com.raxim.myscoutee.domain.platform.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapAuthenticationService;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;

@org.junit.jupiter.api.Tag("standard")
class OperatorBootstrapControllerTest {
    @Test
    void returnsNoStoreTokenResponseWithoutEchoingThePassword()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        when(service.login(
                "operator@deployment.invalid",
                "install-password"))
                .thenReturn(
                        new OperatorBootstrapAuthenticationService
                                .LoginResult(
                                "OperatorBootstrap",
                                "signed-access-token",
                                "operator@deployment.invalid",
                                Instant.parse(
                                        "2026-07-29T17:00:00Z")));
        ConfigProperties properties = new ConfigProperties();
        properties.setOperatorBootstrapAllowInsecure(true);
        var mockMvc = MockMvcBuilders.standaloneSetup(
                new OperatorBootstrapController(
                        service,
                        properties)).build();

        mockMvc.perform(post("/auth/operator-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operator@deployment.invalid",
                                  "password": "install-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        org.hamcrest.Matchers.containsString(
                                "no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.tokenType")
                        .value("OperatorBootstrap"))
                .andExpect(jsonPath("$.accessToken")
                        .value("signed-access-token"))
                .andExpect(jsonPath("$.email")
                        .value("operator@deployment.invalid"))
                .andExpect(jsonPath("$.expiresAt")
                        .value("2026-07-29T17:00:00Z"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "install-password"))));

        verify(service).login(
                "operator@deployment.invalid",
                "install-password");
    }

    @Test
    void enforcesBoundedJsonCredentialFields() throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setOperatorBootstrapAllowInsecure(true);
        var mockMvc = MockMvcBuilders.standaloneSetup(
                new OperatorBootstrapController(
                        service,
                        properties)).build();

        mockMvc.perform(post("/auth/operator-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/operator-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operator@deployment.invalid",
                                  "password": "%s"
                                }
                                """.formatted("x".repeat(513))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCleartextLoginUnlessDevelopmentOverrideIsEnabled()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        ConfigProperties properties = new ConfigProperties();
        var mockMvc = MockMvcBuilders.standaloneSetup(
                new OperatorBootstrapController(
                        service,
                        properties)).build();

        mockMvc.perform(post("/auth/operator-bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operator@deployment.invalid",
                                  "password": "install-password"
                                }
                                """))
                .andExpect(status().isUpgradeRequired());

        verify(service, never()).login(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsDemoSessionHeadersOnRealBootstrapLogin()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setOperatorBootstrapAllowInsecure(true);
        var mockMvc = MockMvcBuilders.standaloneSetup(
                new OperatorBootstrapController(
                        service,
                        properties)).build();

        mockMvc.perform(post("/auth/operator-bootstrap")
                        .header(
                                DemoSessionRequestSupport
                                        .SESSION_KIND_HEADER,
                                "demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "operator@deployment.invalid",
                                  "password": "install-password"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).login(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
