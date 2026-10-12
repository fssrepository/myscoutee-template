package com.raxim.myscoutee.domain.integrations.service;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
@Service
public class IntegrationPrivacyService implements com.raxim.myscoutee.domain.integrations.api.IntegrationPrivacy {
 private final MongoTemplate mongo;
 public IntegrationPrivacyService(MongoTemplate mongo) { this.mongo=mongo; }
 public void purgeParticipantData(String userId) {
  if(!StringUtils.hasText(userId))return;
  remove("integrationTokens",userId,"ownerUserId");
  remove("mcpAuthorizations",userId,"ownerUserId");
  remove("mcpEventSubscriptions",userId,"ownerUserId");
  remove("integrationWebhookDeliveries",userId,"ownerUserId");
 }
 private void remove(String collection,String userId,String...fields) {
  if(!StringUtils.hasText(userId))return;
  mongo.remove(Query.query(new Criteria().orOperator(java.util.Arrays.stream(fields).map(field->Criteria.where(field).is(userId)).toArray(Criteria[]::new))),collection);
 }
}
