package com.raxim.myscoutee.common.config.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import jakarta.servlet.http.Cookie;

@org.junit.jupiter.api.Tag("standard")
class SessionMongoRoutingFilterTest {
    @AfterEach
    void clearMode() {
        MongoSessionModeContext.clear();
    }

    @Test void oauthCannotBeRoutedToDemoByCallerHeaders() throws Exception {
        var filter = new SessionMongoRoutingFilter(new ConfigProperties());
        for (String path : java.util.List.of("/mcp/oauth/token", "/api/mcp/oauth/metadata", "/integrations/v1/connect")) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader(DemoSessionRequestSupport.SESSION_KIND_HEADER, "demo");
            if (path.endsWith("connect")) request.addHeader("Authorization", "Bearer mca_invalid");
            assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.REAL);
        }
    }

    @Test void explicitDevOauthRealmDoesNotDependOnCallerHeaders() throws Exception {
        var config = new ConfigProperties(); config.setMcpDemoEnabled(true);
        var filter = new SessionMongoRoutingFilter(config);
        var request = new MockHttpServletRequest("POST", "/api/mcp/oauth/token");
        assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.DEMO);
        request.addHeader(DemoSessionRequestSupport.SESSION_KIND_HEADER, "real");
        assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.DEMO);
        config.setFirebaseEnabled(true);
        assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.REAL);
    }

    @Test
    void bootstrapLoginAndHeaderAlwaysRouteToRealMongo() throws Exception {
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        SessionMongoRoutingFilter filter =
                new SessionMongoRoutingFilter(properties);

        MockHttpServletRequest login = new MockHttpServletRequest(
                "POST",
                OperatorBootstrapRequestSupport.LOGIN_PATH);
        login.addHeader(
                DemoSessionRequestSupport.SESSION_KIND_HEADER,
                "demo");
        assertThat(modeInside(filter, login))
                .isEqualTo(MongoSessionMode.REAL);

        MockHttpServletRequest authorized =
                new MockHttpServletRequest(
                        "GET",
                        "/operator/configuration");
        authorized.addHeader(
                "Authorization",
                "OperatorBootstrap signed-token");
        authorized.addHeader(
                DemoSessionRequestSupport.SESSION_KIND_HEADER,
                "demo");
        assertThat(modeInside(filter, authorized))
                .isEqualTo(MongoSessionMode.REAL);
    }

    @Test
    void firebaseDisabledOperatorWithoutBootstrapKeepsDemoRouting()
            throws Exception {
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        SessionMongoRoutingFilter filter =
                new SessionMongoRoutingFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/operator/configuration");
        request.setParameter("token", "query-token-is-not-auth");

        assertThat(modeInside(filter, request))
                .isEqualTo(MongoSessionMode.DEMO);
    }

    @Test
    void demoAdminRoutesToDemoButRealAdminStaysReal() throws Exception {
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        SessionMongoRoutingFilter filter =
                new SessionMongoRoutingFilter(properties);

        MockHttpServletRequest demo = new MockHttpServletRequest(
                "GET",
                "/admin/payment-simulator/configuration-access");
        demo.addHeader(DemoSessionRequestSupport.SESSION_KIND_HEADER, "demo");
        assertThat(modeInside(filter, demo)).isEqualTo(MongoSessionMode.DEMO);

        MockHttpServletRequest real = new MockHttpServletRequest(
                "GET",
                "/admin/payment-simulator/configuration-access");
        assertThat(modeInside(filter, real)).isEqualTo(MongoSessionMode.REAL);
    }

    @Test
    void protectedMediaUsesCredentialRealmInsteadOfCallerHeaders() throws Exception {
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        SessionMongoRoutingFilter filter = new SessionMongoRoutingFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/private");
        request.addHeader(DemoSessionRequestSupport.SESSION_KIND_HEADER, "real");
        request.setCookies(new Cookie("myscoutee_media_session", "demo.dmlld2Vy." + "a".repeat(64)));
        assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.DEMO);

        request.addHeader(DemoSessionRequestSupport.SESSION_KIND_HEADER, "demo");
        request.setCookies(new Cookie("myscoutee_media_session", "dmlld2Vy." + "a".repeat(64)));
        assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.REAL);
        request.setCookies();
        assertThat(modeInside(filter, request)).isEqualTo(MongoSessionMode.REAL);
    }

    private MongoSessionMode modeInside(
            SessionMongoRoutingFilter filter,
            MockHttpServletRequest request) throws Exception {
        AtomicReference<MongoSessionMode> observed =
                new AtomicReference<>();
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> observed.set(
                        MongoSessionModeContext.currentMode()));
        return observed.get();
    }
}
