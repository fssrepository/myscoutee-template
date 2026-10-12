package com.raxim.myscoutee.common.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.util.StringUtils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.raxim.myscoutee.common.config.mongo.SessionRoutingMongoDatabaseFactory;

@Configuration
public class MongoRoutingConfig {

    @Bean(name = "realMongoClient", destroyMethod = "close")
    @Primary
    MongoClient realMongoClient(
            @Value("${SPRING_MONGODB_URI:}") String springMongoUri,
            @Value("${spring.mongodb.host:${MONGODB_HOST:localhost}}") String host,
            @Value("${spring.mongodb.port:${MONGODB_PORT:27017}}") Integer port,
            @Value("${spring.mongodb.username:${MONGODB_USERNAME:${MONGO_APP_USERNAME:guest}}}") String username,
            @Value("${spring.mongodb.password:${MONGODB_PASSWORD:${MONGO_APP_PASSWORD:guest}}}") String password,
            @Value("${spring.mongodb.database:${MONGODB_DATABASE:demo_db}}") String database,
            @Value("${MONGODB_REPLICA_SET:}") String replicaSet) {
        String realMongoUri = StringUtils.hasText(springMongoUri)
                ? springMongoUri.trim()
                : buildMongoUri(host, port, username, password, database, replicaSet);
        return MongoClients.create(realMongoUri);
    }

    @Bean(name = "demoMongoClient", destroyMethod = "close")
    MongoClient demoMongoClient(
            @Value("${MONGODB_DEMO_URI:}") String demoMongoUri,
            @Value("${spring.mongodb.host:${MONGODB_HOST:localhost}}") String host,
            @Value("${spring.mongodb.port:${MONGODB_PORT:27017}}") Integer port,
            @Value("${spring.mongodb.username:${MONGODB_USERNAME:${MONGO_APP_USERNAME:guest}}}") String username,
            @Value("${spring.mongodb.password:${MONGODB_PASSWORD:${MONGO_APP_PASSWORD:guest}}}") String password,
            @Value("${MONGODB_DEMO_DATABASE:${spring.mongodb.database:${MONGODB_DATABASE:demo_db}}}") String demoDatabase,
            @Value("${MONGODB_REPLICA_SET:}") String replicaSet) {
        String resolvedDemoMongoUri = StringUtils.hasText(demoMongoUri)
                ? demoMongoUri.trim()
                : buildMongoUri(host, port, username, password, demoDatabase, replicaSet);
        return MongoClients.create(resolvedDemoMongoUri);
    }

    @Bean(name = "realMongoDatabaseFactory")
    MongoDatabaseFactory realMongoDatabaseFactory(
            @Qualifier("realMongoClient") MongoClient realMongoClient,
            @Value("${spring.mongodb.database:${MONGODB_DATABASE:demo_db}}") String database) {
        return MongoDatabaseFactory.create(realMongoClient, database.trim());
    }

    @Bean(name = "demoMongoDatabaseFactory")
    MongoDatabaseFactory demoMongoDatabaseFactory(
            @Qualifier("demoMongoClient") MongoClient demoMongoClient,
            @Value("${MONGODB_DEMO_DATABASE:${spring.mongodb.database:${MONGODB_DATABASE:demo_db}}}") String demoDatabase) {
        return MongoDatabaseFactory.create(demoMongoClient, demoDatabase.trim());
    }

    @Bean(name = "mongoDatabaseFactory")
    @Primary
    MongoDatabaseFactory routingMongoDatabaseFactory(
            @Qualifier("realMongoDatabaseFactory") MongoDatabaseFactory realMongoDatabaseFactory,
            @Qualifier("demoMongoDatabaseFactory") MongoDatabaseFactory demoMongoDatabaseFactory) {
        return new SessionRoutingMongoDatabaseFactory(realMongoDatabaseFactory, demoMongoDatabaseFactory);
    }

    private String buildMongoUri(
            String host,
            Integer port,
            String username,
            String password,
            String database,
            String replicaSet) {
        String normalizedHost = StringUtils.hasText(host) ? host.trim() : "localhost";
        int normalizedPort = port == null || port.intValue() <= 0 ? 27017 : port.intValue();
        String normalizedDatabase = StringUtils.hasText(database) ? database.trim() : "demo_db";
        String uri = "mongodb://"
                + encodeUriComponent(defaultIfBlank(username, "guest"))
                + ":"
                + encodeUriComponent(defaultIfBlank(password, "guest"))
                + "@"
                + normalizedHost
                + ":"
                + normalizedPort
                + "/"
                + encodeUriComponent(normalizedDatabase)
                + "?authSource="
                + encodeUriComponent(normalizedDatabase);
        if (StringUtils.hasText(replicaSet)) {
            uri += "&replicaSet=" + encodeUriComponent(replicaSet.trim());
        }
        return uri;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String encodeUriComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
