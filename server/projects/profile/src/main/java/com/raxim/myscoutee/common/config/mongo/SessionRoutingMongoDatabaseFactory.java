package com.raxim.myscoutee.common.config.mongo;

import org.bson.codecs.configuration.CodecRegistry;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.util.Assert;

import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;

public class SessionRoutingMongoDatabaseFactory implements MongoDatabaseFactory {
    private final MongoDatabaseFactory realFactory;
    private final MongoDatabaseFactory demoFactory;

    public SessionRoutingMongoDatabaseFactory(
            MongoDatabaseFactory realFactory,
            MongoDatabaseFactory demoFactory) {
        Assert.notNull(realFactory, "Real MongoDatabaseFactory must not be null");
        Assert.notNull(demoFactory, "Demo MongoDatabaseFactory must not be null");
        this.realFactory = realFactory;
        this.demoFactory = demoFactory;
    }

    @Override
    public MongoDatabase getMongoDatabase() {
        return currentFactory().getMongoDatabase();
    }

    @Override
    public MongoDatabase getMongoDatabase(String dbName) {
        return currentFactory().getMongoDatabase(dbName);
    }

    @Override
    public org.springframework.dao.support.PersistenceExceptionTranslator getExceptionTranslator() {
        return currentFactory().getExceptionTranslator();
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return currentFactory().getCodecRegistry();
    }

    @Override
    public ClientSession getSession(ClientSessionOptions options) {
        return currentFactory().getSession(options);
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSessionOptions options) {
        return new SessionRoutingMongoDatabaseFactory(
                realFactory.withSession(options),
                demoFactory.withSession(options));
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSession session) {
        return new SessionRoutingMongoDatabaseFactory(
                realFactory.withSession(session),
                demoFactory.withSession(session));
    }

    private MongoDatabaseFactory currentFactory() {
        return MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO
                ? demoFactory
                : realFactory;
    }
}
