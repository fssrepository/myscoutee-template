package com.raxim.myscoutee.microservices.bootstrap;

import com.raxim.myscoutee.common.config.CachingConfig;
import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.common.config.MongoConverterConfig;
import com.raxim.myscoutee.common.config.MongoRoutingConfig;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Infrastructure has explicit owners; no scan of the monolith configuration package. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
// Boot selects Spring Data Web or Data REST support from each assembly's
// classpath. Enabling both explicitly registers duplicate pageable resolvers.
@Import({com.raxim.myscoutee.common.runtime.RuntimeStatusPublisher.class, MongoRoutingConfig.class, MongoConverterConfig.class, JsonConfig.class,
        CachingConfig.class, ScheduledMongoSessionRunner.class,
        com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class})
public class ServiceInfrastructureConfiguration {}
