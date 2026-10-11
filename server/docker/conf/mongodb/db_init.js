const requiredCollections = [
  "accessGrants",
  "accessPolicyDefinitions",
  "accountPurgeJobs",
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
  portalDb.integrationTokens.createIndex({ tokenHash: 1 }, { name: 'integration_token_hash', unique: true });
  portalDb.integrationTokens.createIndex({ ownerUserId: 1, createdAtIso: -1 }, { name: 'integration_token_owner_created' });
  portalDb.mcpOAuthClients.createIndex({ createdAt: 1 }, { name: 'createdAt' });
  portalDb.mcpOAuthClients.createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });
  portalDb.mcpAuthorizations.createIndex({ clientId: 1 }, { name: 'clientId' });
  portalDb.mcpAuthorizations.createIndex({ accessHash: 1 }, { name: 'accessHash' });
  portalDb.mcpAuthorizations.createIndex({ refreshHash: 1 }, { name: 'refreshHash' });
  portalDb.mcpAuthorizations.createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });

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
