package com.raxim.myscoutee.domain.accounts.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.accounts.document.*;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.*;
import com.raxim.myscoutee.domain.accounts.repository.*;
import com.raxim.myscoutee.domain.accounts.security.firebase.*;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
@Tag("embedded")
class AccountSessionCredentialTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }
    @Test void allRolesShareExpiryRevocationAndHashedOpaqueCredentials() {
        try (var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var mongo = new MongoTemplate(client, "account-session-contract");
                var audits = new MongoRepositoryFactory(mongo).getRepository(UserLoginAuditsRepository.class);
                var service = new UserLoginSessionService(mongo, audits, mock(NotificationDelivery.class), 2);
                for (String role : List.of("U", "A", "O")) {
                    var user = new Users(); user.setUserId("account-" + role); user.setName("Account");
                    var principal = new FirebasePrincipal(user.getUserId(), "account@example.test", List.of("U", role), user, true);
                    SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(principal,
                            new AccountLoginService.PasswordCredential(), principal.getAuthorities()));
                    var accepted = service.registerLogin("session", new UserSessionLoginRequestDto("attempt-" + role, "password", null, null), new MockHttpServletRequest());
                    var response = service.completeLogin(accepted, "session");
                    String token = response.session().accessToken();
                    assertThat(response.session().profile().id()).isEqualTo(user.getUserId());
                    assertThat(Instant.parse(response.session().expiresAt())).isBetween(Instant.now().plusSeconds(28790), Instant.now().plusSeconds(28810));
                    assertThat(service.authenticateAccessToken(token).userId()).isEqualTo(user.getUserId());
                    String stored = mongo.getCollection("userSessionStates").find().into(new java.util.ArrayList<>()).toString();
                    assertThat(stored).doesNotContain(token);
                    assertThat(service.authenticateAccessToken(token.substring(0, token.length()-1) + (token.endsWith("a") ? "b" : "a"))).isNull();
                    assertThat(service.authenticateAccessToken("raw-firebase-proof")).isNull();
                    mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getUserId())),
                            new Update().set("activeSessions.0.accessTokenExpiresAt", Instant.now().minusSeconds(1)), UserSessionState.class);
                    assertThat(service.authenticateAccessToken(token)).isNull();
                    mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getUserId())),
                            new Update().set("activeSessions.0.accessTokenExpiresAt", Instant.now().plusSeconds(60)), UserSessionState.class);
                    assertThat(service.authenticateAccessToken(token)).isNotNull();
                    // Revocation follows the credential's root account even while a group profile is selected.
                    var selected = new FirebasePrincipal("group-profile", "account@example.test", List.of("U", role), user);
                    var credential = service.authenticateAccessToken(token);
                    SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(selected, credential, selected.getAuthorities()));
                    assertThat(service.revokeCurrentSession(null)).isTrue();
                    assertThat(service.authenticateAccessToken(token)).isNull();
                }
            }
        }
    }
}
