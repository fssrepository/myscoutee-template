const requiredCollections = [
  "accessGrants",
  "accessPolicyDefinitions",
  "accountPurgeJobs",
  "accountDeletionWarnings",
  "operatorEmailConfigurations",
  "emailUnsubscriptions",
  "appSettings",
  "chatMessages",
  "chats",
  "contactChatAccess",
  "countryPartitions",
  "deploymentConfigurations",
  "helpCenterGuideFields",
  "helpCenterRevisions",
  "i18n_bundles",
  "i18n_messages",
  "notificationOutbox",
  "notificationRuleConfigs",
  "privacyConsents",
  "userFriendships",
  "userReports",
  "integrationWebhookDeliveries",
  "mcpEventSubscriptions",
  "userFeedback",
  "userLoginAudits",
  "userNotifications",
  "userSessionActions",
  "userSessionStates",
  "users"
];

const appUsername = process.env.MONGO_APP_USERNAME || 'guest';
const appPassword = process.env.MONGO_APP_PASSWORD || 'guest';

const configuredDatabases = [
  'demo_db',
  'myscoutee_db',
  process.env.MONGO_INITDB_DATABASE,
  process.env.MONGO_APP_DATABASE,
  process.env.MONGO_DEMO_DATABASE,
  process.env.MONGO_REAL_DATABASE,
  process.env.MONGO_E2E_DATABASE
]
  .map(value => (value || '').trim())
  .filter(Boolean);

Array.from(new Set(configuredDatabases)).forEach(databaseName => {
  const portalDb = db.getSiblingDB(databaseName);
  const existingCollections = new Set(portalDb.getCollectionNames());

  requiredCollections.forEach(collectionName => {
    if (!existingCollections.has(collectionName)) {
      portalDb.createCollection(collectionName);
    }
  });

  portalDb.countryPartitions.createIndex(
    { partitionKey: 1 },
    { name: 'country_partition_key', unique: true }
  );
  portalDb.countryPartitions.createIndex(
    { countryCode: 1 },
    { name: 'country_partition_code', unique: true, sparse: true }
  );
  portalDb.countryPartitions.createIndex(
    { geometry: '2dsphere' },
    { name: 'country_partition_geometry' }
  );
  // Selected account-warning delivery claims; the _id remains the native episode key.
  portalDb.accountDeletionWarnings.createIndex({ status: 1, nextAttemptAtIso: 1, _id: 1 }, { name: 'account_warning_retry_due' });
  portalDb.accountDeletionWarnings.createIndex({ status: 1, startedAtIso: 1 }, { name: 'account_warning_stale_claim' });
  portalDb.accountDeletionWarnings.createIndex({ userId: 1 }, { name: 'account_warning_user' });
  portalDb.integrationTokens.createIndex({ tokenHash: 1 }, { name: 'integration_token_hash', unique: true });
  portalDb.integrationTokens.createIndex({ ownerUserId: 1, createdAtIso: -1 }, { name: 'integration_token_owner_created' });
  portalDb.mcpOAuthClients.createIndex({ createdAt: 1 }, { name: 'createdAt' });
  portalDb.mcpOAuthClients.createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });
  portalDb.mcpAuthorizations.createIndex({ clientId: 1 }, { name: 'clientId' });
  portalDb.mcpAuthorizations.createIndex({ accessHash: 1 }, { name: 'accessHash' });
  portalDb.mcpAuthorizations.createIndex({ refreshHash: 1 }, { name: 'refreshHash' });
  portalDb.mcpAuthorizations.createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });

  portalDb.integrationWebhookDeliveries.createIndex({protocol: 1, status: 1, ownerUserId: 1}, {name: 'integration_delivery_protocol_status'});
  portalDb.integrationWebhookDeliveries.createIndex({status: 1, nextAttemptAtIso: 1, createdAtIso: 1}, {name: 'integration_webhook_delivery_due'});
  portalDb.integrationWebhookDeliveries.createIndex({subscriptionId: 1, createdAtIso: -1}, {name: 'integration_webhook_delivery_subscription'});
  portalDb.integrationWebhookDeliveries.createIndex({deleteAfter: 1}, {name: 'deleteAfter', expireAfterSeconds: 0});
  portalDb.mcpEventSubscriptions.createIndex({ownerUserId: 1, name: 1, resourceId: 1, enabled: 1}, {name: 'mcp_events_owner_event'});
  portalDb.mcpEventSubscriptions.createIndex({ownerUserId: 1, tokenId: 1, clientId: 1, callbackUrl: 1}, {name: 'mcp_events_callback'});
  portalDb.mcpEventSubscriptions.createIndex({deleteAfter: 1}, {name: 'deleteAfter', expireAfterSeconds: 0});

  portalDb.userReports.createIndex({syncKey: 1}, {name: 'user_report_sync_key', unique: true, sparse: true});
  portalDb.userReports.createIndex({userId: 1, createdDate: -1}, {name: 'user_report_user_created'});
  portalDb.userReports.createIndex({resolvedAtIso: 1}, {name: 'user_report_resolved'});
  portalDb.userFeedback.createIndex({syncKey: 1}, {name: 'user_feedback_sync_key', unique: true, sparse: true});
  portalDb.userFeedback.createIndex({userId: 1, createdDate: -1}, {name: 'user_feedback_user_created'});
  portalDb.userFeedback.createIndex({resolvedAtIso: 1}, {name: 'user_feedback_resolved'});

  portalDb.users.createIndex(
    { syncKey: 1 },
    { name: 'users_sync_key', unique: true, sparse: true }
  );
  portalDb.users.createIndex(
    { userId: 1 },
    { name: 'users_user_id', unique: true }
  );
  portalDb.users.createIndex(
    { email: 1 },
    { name: 'users_email', unique: true, sparse: true }
  );
  portalDb.users.createIndex(
    { partitionKey: 1 },
    { name: 'users_partition_key', sparse: true }
  );
  portalDb.users.createIndex(
    { profileStatus: 1 },
    { name: 'users_profile_status' }
  );
  portalDb.users.createIndex(
    { profileStatus: 1, deletedAtIso: 1 },
    { name: 'users_profile_status_deleted' }
  );
  portalDb.users.createIndex(
    { name: 1, userId: 1 },
    { name: 'users_name_user_id', collation: { locale: 'en', strength: 2 } }
  );
  portalDb.appSettings.createIndex(
    { settingsKey: 1 },
    { name: 'app_settings_key', unique: true }
  );
  portalDb.notificationRuleConfigs.createIndex(
    { syncKey: 1 },
    { name: 'notification_rule_config_sync_key', unique: true, sparse: true }
  );
  portalDb.notificationRuleConfigs.createIndex(
    { ruleKey: 1 },
    { name: 'notification_rule_config_rule_key', unique: true }
  );
  portalDb.userNotifications.createIndex(
    { syncKey: 1 },
    { name: 'user_notifications_sync_key', unique: true, sparse: true }
  );
  portalDb.userNotifications.createIndex(
    { recipientUserId: 1, readAtIso: 1, createdDate: -1, _id: -1 },
    { name: 'user_notifications_recipient_read_created' }
  );
  portalDb.userNotifications.createIndex(
    { recipientUserId: 1, createdDate: -1, _id: -1 },
    { name: 'user_notifications_recipient_created' }
  );
  portalDb.userNotifications.createIndex(
    { sourceType: 1, sourceId: 1, createdDate: -1 },
    { name: 'user_notifications_source_created' }
  );
  portalDb.userNotifications.createIndex(
    { recipientUserId: 1, kind: 1, sourceType: 1, sourceId: 1, readAtIso: 1 },
    { name: 'user_notifications_recipient_kind_source' }
  );
  portalDb.accessPolicyDefinitions.createIndex(
    { syncKey: 1 },
    { name: 'access_policy_definition_sync_key', unique: true, sparse: true }
  );
  portalDb.accessPolicyDefinitions.createIndex(
    { policyKey: 1 },
    { name: 'access_policy_definition_key', unique: true }
  );
  portalDb.accessGrants.createIndex(
    { syncKey: 1 },
    { name: 'access_grant_sync_key', unique: true, sparse: true }
  );
  portalDb.accessGrants.createIndex(
    { subjectUserId: 1, permission: 1, resourceType: 1, resourceId: 1 },
    { name: 'access_grant_subject_permission_resource' }
  );
  portalDb.accessGrants.createIndex(
    { resourceType: 1, resourceId: 1, permission: 1 },
    { name: 'access_grant_resource_permission' }
  );
  portalDb.chats.createIndex(
    { ownerUserId: 1, id: 1 },
    { name: 'chat_owner_public_id', unique: true }
  );
  portalDb.chats.createIndex(
    { id: 1 },
    { name: 'chat_public_id_lookup' }
  );
  portalDb.chats.createIndex(
    { ownerUserId: 1, dateIso: -1 },
    { name: 'chat_owner_date' }
  );
  portalDb.chats.createIndex(
    { memberIds: 1, dateIso: -1 },
    { name: 'chat_member_date' }
  );
  portalDb.chats.createIndex(
    { channelType: 1, dateIso: -1 },
    { name: 'chat_channel_date' }
  );
  portalDb.chatMessages.createIndex(
    { chatId: 1, id: 1 },
    { name: 'chat_message_lookup', unique: true }
  );
  portalDb.chatMessages.createIndex(
    { chatId: 1, sentAtIso: -1, id: -1 },
    { name: 'chat_message_page_desc' }
  );
  portalDb.chatMessages.createIndex(
    { messageSenderUserIds: 1, sentAtIso: -1 },
    { name: 'chat_message_sender_date' }
  );
  portalDb.chatMessages.createIndex(
    { messageReadByUserIds: 1, sentAtIso: -1 },
    { name: 'chat_message_reader_date' }
  );
  portalDb.chatMessages.createIndex(
    { messageReactionUserIds: 1, sentAtIso: -1 },
    { name: 'chat_message_reaction_date' }
  );
  portalDb.chatMessages.createIndex(
    { messageAttachmentKeys: 1, sentAtIso: -1 },
    { name: 'chat_message_attachment_date' }
  );
  portalDb.chatMessages.createIndex(
    { pollVoterUserIds: 1, sentAtIso: -1 },
    { name: 'chat_poll_voter_date' }
  );
  portalDb.chatMessages.createIndex(
    { sentAtIso: 1, 'senderAvatar.id': 1, deletedAtIso: 1 },
    { name: 'chat_message_qmau_window' }
  );
  portalDb.privacyConsents.createIndex(
    { userId: 1, revisionId: 1 },
    { name: 'privacy_consent_user_revision', unique: true }
  );
  portalDb.privacyConsents.createIndex(
    { userId: 1 },
    { name: 'privacy_consent_user' }
  );
  portalDb.privacyConsents.createIndex(
    { revisionId: 1 },
    { name: 'privacy_consent_revision' }
  );
  portalDb.i18n_bundles.createIndex(
    { lang: 1 },
    { name: 'i18n_bundles_lang', unique: true }
  );
  portalDb.i18n_messages.createIndex(
    { key: 1, lang: 1 },
    { name: 'i18n_messages_key_lang', unique: true }
  );

  if (!portalDb.getUser(appUsername)) {
    portalDb.createUser({
      user: appUsername,
      pwd: appPassword,
      roles: [
        { role: 'readWrite', db: databaseName }
      ]
    });
  }
});
