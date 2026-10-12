package com.raxim.myscoutee.domain.integrations.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.integrations.document.IntegrationWebhookDelivery;

public interface IntegrationWebhookDeliveriesRepository
        extends MongoRepository<IntegrationWebhookDelivery, String> {
}
