package com.raxim.myscoutee.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.Alias;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import com.raxim.myscoutee.domain.accounts.document.Users;

class MongoConverterConfigTest {
    @Test
    @org.junit.jupiter.api.Tag("standard")
    void integrationAccessRetainsItsLegacyIdentityAndRevisionAcrossProfileReload() {
        var converter = domainConverter();
        var value = new com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess(7,
                com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.READ,
                java.util.Map.of("client", com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.FULL));
        var document = new Document(); converter.write(new Payload(value), document);
        assertThat(document.get("value", Document.class).getString("_class")).isEqualTo("com.raxim.myscoutee.profile.data.document.IntegrationAccess");
        assertThat(converter.read(Payload.class, document).value()).isEqualTo(value);
        var user = new Users(); user.setUserId("member"); user.setIntegrationAccess(value);
        var profile = new Document(); converter.write(user, profile);
        profile.get("integrationAccess", Document.class).remove("_class");
        assertThat(converter.read(Users.class, profile).getIntegrationAccess()).isEqualTo(value);
    }





    @Test
    @org.junit.jupiter.api.Tag("standard")
    void feedCounterValueRetainsHistoricalIdentityAndCounts() {
        var converter = domainConverter();
        var value = new com.raxim.myscoutee.domain.accounts.dto.FeedCountersRecord(7, java.util.Map.of("accepted", 3L));
        var document = new Document(); converter.write(new Payload(value), document);
        assertThat(document.get("value", Document.class).getString("_class"))
            .isEqualTo("com.raxim.myscoutee.profile.data.document.Users$FeedCountersRecord");
        assertThat(converter.read(Payload.class, document).value()).isEqualTo(value);
    }

    @Test

    @org.junit.jupiter.api.Tag("standard")
    void preservesDottedKeysWhenAnotherPostProcessorCreatesTheConverterEarly() {
        try (var context = new AnnotationConfigApplicationContext(
                MongoConverterConfig.class, EarlyConverterConfiguration.class)) {
            var snapshot = new CounterValues(Map.of("profile.registered", 3L));
            var document = new Document();
            context.getBean(MappingMongoConverter.class).write(snapshot, document);
            assertThat(document.get("counters", Document.class))
                    .containsEntry("profile.registered", 3L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EarlyConverterConfiguration {
        @Bean
        static BeanPostProcessor eagerConverterConsumer(MappingMongoConverter converter) {
            return new BeanPostProcessor() {};
        }

        @Bean
        MappingMongoConverter earlyConverter() {
            MongoMappingContext mappingContext = new MongoMappingContext();
            mappingContext.afterPropertiesSet();
            return new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        }
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void preservesDottedMapKeysWhenWritingDocuments() {
        MappingMongoConverter converter = mappingMongoConverter();
        BeanPostProcessor postProcessor = MongoConverterConfig.preserveDottedMongoMapKeys();
        postProcessor.postProcessBeforeInitialization(converter, "mappingMongoConverter");

        var snapshot = new CounterValues(Map.of("profile.registered", 3L));

        Document document = new Document();
        converter.write(snapshot, document);

        assertThat(document.get("counters", Document.class))
                .containsEntry("profile.registered", 3L);
    }





    private MappingMongoConverter mappingMongoConverter() {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.afterPropertiesSet();
        return converter;
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void readsAndWritesTheHistoricalRootDocumentTypeWithoutADataMigration() {
        var converter = domainConverter();
        var user = new Users();
        user.setUserId("domain-test-user");
        var document = new Document();
        converter.write(user, document);

        assertThat(document.getString("_class"))
                .isEqualTo("com.raxim.myscoutee.profile.data.document.Users");
        assertThat(converter.read(Object.class, document)).isInstanceOfSatisfying(Users.class,
                restored -> assertThat(restored.getUserId()).isEqualTo("domain-test-user"));
    }





    @Test
    @org.junit.jupiter.api.Tag("standard")
    void everyStoredAliasResolvesToItsCurrentTypeAndWritesTheOriginalIdentity() throws Exception {
        var aliases = new Properties();
        try (var input = new ClassPathResource("mongo-domain-types.properties").getInputStream()) {
            aliases.load(input);
        }
        var mapper = new MongoConverterConfig.DomainTypeInformationMapper();
        for (var stored : aliases.stringPropertyNames()) {
            var current = Class.forName(aliases.getProperty(stored), false, getClass().getClassLoader());
            assertThat(mapper.resolveTypeFrom(Alias.of(stored)).getType()).isEqualTo(current);
            assertThat(mapper.createAliasFor(TypeInformation.of(current))).isEqualTo(Alias.of(stored));
        }
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void newUnmappedTypesKeepTheirExplicitSpringTypeAlias() {
        var converter = domainConverter();
        var document = new Document();
        converter.write(new FutureSnapshot("value"), document);
        assertThat(document.getString("_class")).isEqualTo("future-snapshot");
        assertThat(converter.read(Object.class, document)).isEqualTo(new FutureSnapshot("value"));
    }





    @Test
    @org.junit.jupiter.api.Tag("standard")
    void encryptedEnvelopeRetainsHistoricalNestedIdentityAndFieldValues() {
        var converter = domainConverter();
        var envelope = new com.raxim.myscoutee.domain.platform.dto.EncryptedSecret();
        envelope.setAlgorithm("A256GCM"); envelope.setKeyId("key");
        envelope.setNonce("nonce"); envelope.setCiphertext("ciphertext");
        var document = new Document();
        converter.write(new Payload(envelope), document);
        assertThat(document.get("value", Document.class).getString("_class"))
                .isEqualTo("com.raxim.myscoutee.profile.data.document.DeploymentConfiguration$EncryptedSecret");
        var restored = (com.raxim.myscoutee.domain.platform.dto.EncryptedSecret) converter.read(Payload.class, document).value();
        assertThat(restored).usingRecursiveComparison().isEqualTo(envelope);
    }




    private MappingMongoConverter domainConverter() {
        var converter = mappingMongoConverter();
        MongoConverterConfig.preserveDottedMongoMapKeys()
                .postProcessBeforeInitialization(converter, "mappingMongoConverter");
        return converter;
    }

    record Payload(Object value) {}
    record CounterValues(Map<String, Long> counters) {}

    @org.springframework.data.annotation.TypeAlias("future-snapshot")
    record FutureSnapshot(String value) {}
}
