package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.web.server.ResponseStatusException;
import com.mongodb.client.MongoClients;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.common.config.mongo.SessionRoutingMongoDatabaseFactory;
import com.raxim.myscoutee.domain.access.api.ConsentAuthorization;
import com.raxim.myscoutee.domain.accounts.repository.PrivacyConsentsRepository;
import com.raxim.myscoutee.domain.help.document.HelpCenterRevision;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.PrivacyConsentSaveRequestDto;
import com.raxim.myscoutee.domain.help.repository.HelpCenterGuideFieldsRepository;
import com.raxim.myscoutee.domain.help.repository.HelpCenterRevisionsRepository;
import com.raxim.myscoutee.domain.help.service.HelpCenterService;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;

@Tag("embedded")
class ConsentRevisionRolloverPersistenceTest {
    @Test
    void rolloverRequiresNewAcceptanceAndKeepsRealAndDemoSeparateAfterReconstruction() {
        try (var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var mongo = new MongoTemplate(new SessionRoutingMongoDatabaseFactory(
                        new SimpleMongoClientDatabaseFactory(client, "consent_real"),
                        new SimpleMongoClientDatabaseFactory(client, "consent_demo")));
                var repositories = new MongoRepositoryFactory(mongo);
                var revisions = repositories.getRepository(HelpCenterRevisionsRepository.class);
                var authorization = mock(ConsentAuthorization.class);
                when(authorization.canCurrentActorSubmitPrivacyConsent("member")).thenReturn(true);
                var help = owner(mongo, authorization);
                for (var mode : MongoSessionMode.values()) {
                    MongoSessionModeContext.set(mode);
                    revisions.save(revision("privacy-v4", 4));
                    var accepted = help.savePrivacyConsent(request("privacy-v4", List.of("analytics")));
                    assertThat(accepted.revisionVersion()).isEqualTo(4);
                    assertThat(accepted.approvedOptionalSectionIds()).containsExactly("analytics");
                    revisions.save(revision("privacy-v5", 5));
                    assertThat(help.activeState("privacy", "en").activeRevision().id()).isEqualTo("privacy-v5");
                    // A missing acceptance returns its actual old identity, never a fabricated v5 acceptance.
                    var stale = help.privacyConsent("member", "privacy-v5", 5);
                    assertThat(stale.revisionId()).isEqualTo("privacy-v4");
                    assertThat(stale.revisionVersion()).isEqualTo(4);
                    assertThat(repositories.getRepository(PrivacyConsentsRepository.class)
                            .findByUserIdAndRevisionId("member", "privacy-v5")).isEmpty();
                }
                MongoSessionModeContext.set(MongoSessionMode.REAL);
                var real = help.savePrivacyConsent(request("privacy-v5", List.of("analytics", "required", "unknown", "analytics")));
                assertThat(real.revisionVersion()).isEqualTo(5); // Never trusts the caller's version 999.
                assertThat(real.approvedOptionalSectionIds()).containsExactly("analytics");
                MongoSessionModeContext.set(MongoSessionMode.DEMO);
                assertThat(help.privacyConsent("member", "privacy-v5", 5).revisionId()).isEqualTo("privacy-v4");
                var demo = help.savePrivacyConsent(request("privacy-v5", List.of()));
                assertThat(demo.approvedOptionalSectionIds()).isEmpty();
                assertThat(demo.id()).isNotEqualTo(real.id());

                // Recreate the owners and repositories; acceptance and choices must survive reload in both modes.
                help = owner(mongo, authorization);
                for (var mode : MongoSessionMode.values()) {
                    MongoSessionModeContext.set(mode);
                    var expected = mode == MongoSessionMode.REAL ? real : demo;
                    var loaded = help.privacyConsent("member", "privacy-v5", 5);
                    assertThat(loaded).isEqualTo(expected);
                    var repeated = help.savePrivacyConsent(request("privacy-v5", expected.approvedOptionalSectionIds()));
                    assertThat(repeated.id()).isEqualTo(expected.id());
                    assertThat(repeated.acceptedAtIso()).isEqualTo(expected.acceptedAtIso());
                    assertThat(repositories.getRepository(PrivacyConsentsRepository.class).count()).isEqualTo(2);
                    assertThat(help.privacyConsent("member", "privacy-v4", 4).approvedOptionalSectionIds())
                            .containsExactly("analytics");
                }
                var finalHelp = help;
                assertThatThrownBy(() -> finalHelp.savePrivacyConsent(new PrivacyConsentSaveRequestDto(
                        "another-user", "privacy-v5", 5, List.of(), "entry")))
                        .isInstanceOf(ResponseStatusException.class)
                        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));
            }
        } finally {
            MongoSessionModeContext.clear();
        }
    }

    private HelpCenterService owner(MongoTemplate mongo, ConsentAuthorization authorization) {
        var repositories = new MongoRepositoryFactory(mongo);
        return new HelpCenterService(repositories.getRepository(HelpCenterRevisionsRepository.class),
                repositories.getRepository(HelpCenterGuideFieldsRepository.class),
                new AccountConsentService(repositories.getRepository(PrivacyConsentsRepository.class)), authorization);
    }

    private PrivacyConsentSaveRequestDto request(String revision, List<String> choices) {
        return new PrivacyConsentSaveRequestDto("member", revision, 999, choices, "entry");
    }

    private HelpCenterRevision revision(String id, int version) {
        var revision = new HelpCenterRevision();
        revision.setId(id); revision.setDocumentType("privacy"); revision.setLang("en");
        revision.setVersion(version); revision.setActive(true); revision.setTitle(id);
        var required = new HelpCenterRevision.HelpCenterSection();
        required.setId("required"); required.setOptional(false);
        var optional = new HelpCenterRevision.HelpCenterSection();
        optional.setId("analytics"); optional.setOptional(true);
        revision.setSections(List.of(required, optional));
        return revision;
    }
}
