# Project-owned collection policy. Sourced by mongo_import.sh; not a Mongo entrypoint.
# Keep product migrations and indexes here; the import engine is shared.

allowed_collections_by_db="${MONGO_IMPORT_ALLOWED_COLLECTIONS_BY_DB:-demo_db:*;e2e_db:*;myscoutee_db:countryPartitions,i18n_bundles,i18n_messages,helpCenterRevisions,helpCenterGuideFields,appSettings,notificationRuleConfigs,accessPolicyDefinitions,deploymentConfigurations;production_db:countryPartitions,i18n_bundles,i18n_messages,helpCenterRevisions,helpCenterGuideFields,appSettings,notificationRuleConfigs,accessPolicyDefinitions,deploymentConfigurations}"

declare -A seed_import_handlers=(
  [appSettings]=import_app_settings_seed
  [deploymentConfigurations]=import_deployment_configuration_seed
)

clear_collection() {
  local target_db="$1"
  local collection_name="$2"
  local clear_command="db.getCollection('${collection_name}').deleteMany({})"

  if [ "${collection_name}" = "helpCenterRevisions" ] || [ "${collection_name}" = "helpCenterGuideFields" ]; then
    clear_command="db.getCollection('${collection_name}').drop()"
  fi

  mongosh \
    --host "${mongo_host}" \
    --port "${mongo_port}" \
    --username "${mongo_username}" \
    --password "${mongo_password}" \
    --authenticationDatabase "${target_db}" \
    --quiet \
    "${target_db}" \
    --eval "${clear_command}" >/dev/null
}

collection_should_replace_on_import() {
  local collection_name="$1"

  if [ "${collection_name}" = "adminUsers" ] \
      || [ "${collection_name}" = "appSettings" ] \
      || [ "${collection_name}" = "deploymentConfigurations" ]; then
    return 1
  fi

  [ "${replace_collections}" = "true" ] \
    || [ "${collection_name}" = "helpCenterRevisions" ] \
    || [ "${collection_name}" = "helpCenterGuideFields" ]
}

target_collection_name() {
  local collection_name="$1"

  if [ "${collection_name}" = "adminUsers" ]; then
    printf '%s\n' "users"
    return 0
  fi

  printf '%s\n' "${collection_name}"
}


import_app_settings_seed() {
  import_singleton_seed "$1" "$2" appSettings app-settings-global
}

import_deployment_configuration_seed() {
  local target_db="$1"
  local jsonfile="$2"

  # Template 1.0.0 starts with schema 5. Existing operator values remain authoritative.
  import_singleton_seed "${target_db}" "${jsonfile}" deploymentConfigurations deployment-configuration
}

ensure_collection_indexes() {
  local target_db="$1"
  local collection_name="$2"

  case "${collection_name}" in
    accountDeletionWarnings)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('accountDeletionWarnings').createIndex({ status: 1, nextAttemptAtIso: 1, _id: 1 }, { name: 'account_warning_retry_due' });
db.getCollection('accountDeletionWarnings').createIndex({ status: 1, startedAtIso: 1 }, { name: 'account_warning_stale_claim' });
db.getCollection('accountDeletionWarnings').createIndex({ userId: 1 }, { name: 'account_warning_user' });
" >/dev/null
      ;;
    integrationTokens|mcpOAuthClients|mcpAuthorizations|integrationWebhookDeliveries|mcpEventSubscriptions)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.integrationWebhookDeliveries.createIndex({protocol: 1, status: 1, ownerUserId: 1}, {name: 'integration_delivery_protocol_status'});
db.integrationWebhookDeliveries.createIndex({status: 1, nextAttemptAtIso: 1, createdAtIso: 1}, {name: 'integration_webhook_delivery_due'});
db.integrationWebhookDeliveries.createIndex({subscriptionId: 1, createdAtIso: -1}, {name: 'integration_webhook_delivery_subscription'});
db.integrationWebhookDeliveries.createIndex({deleteAfter: 1}, {name: 'deleteAfter', expireAfterSeconds: 0});
db.mcpEventSubscriptions.createIndex({ownerUserId: 1, name: 1, resourceId: 1, enabled: 1}, {name: 'mcp_events_owner_event'});
db.mcpEventSubscriptions.createIndex({ownerUserId: 1, tokenId: 1, clientId: 1, callbackUrl: 1}, {name: 'mcp_events_callback'});
db.mcpEventSubscriptions.createIndex({deleteAfter: 1}, {name: 'deleteAfter', expireAfterSeconds: 0});
db.getCollection('integrationTokens').createIndex({ tokenHash: 1 }, { name: 'integration_token_hash', unique: true });
db.getCollection('integrationTokens').createIndex({ ownerUserId: 1, createdAtIso: -1 }, { name: 'integration_token_owner_created' });
db.getCollection('mcpOAuthClients').createIndex({ createdAt: 1 }, { name: 'createdAt' });
db.getCollection('mcpOAuthClients').createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });
db.getCollection('mcpAuthorizations').createIndex({ clientId: 1 }, { name: 'clientId' });
db.getCollection('mcpAuthorizations').createIndex({ accessHash: 1 }, { name: 'accessHash' });
db.getCollection('mcpAuthorizations').createIndex({ refreshHash: 1 }, { name: 'refreshHash' });
db.getCollection('mcpAuthorizations').createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });
" >/dev/null
      ;;
    users)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('users').createIndex({ syncKey: 1 }, { name: 'users_sync_key', unique: true, sparse: true });
db.getCollection('users').createIndex({ userId: 1 }, { name: 'users_user_id', unique: true });
db.getCollection('users').createIndex({ email: 1 }, { name: 'users_email', unique: true, sparse: true });
db.getCollection('users').createIndex({ partitionKey: 1 }, { name: 'users_partition_key', sparse: true });
db.getCollection('users').createIndex({ profileStatus: 1 }, { name: 'users_profile_status' });
db.getCollection('users').createIndex({ profileStatus: 1, deletedAtIso: 1 }, { name: 'users_profile_status_deleted' });
db.getCollection('users').createIndex({ name: 1, userId: 1 }, { name: 'users_name_user_id', collation: { locale: 'en', strength: 2 } });
" >/dev/null
      ;;
    appSettings)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('appSettings').createIndex({ settingsKey: 1 }, { name: 'app_settings_key', unique: true });
" >/dev/null
      ;;
    notificationRuleConfigs)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('notificationRuleConfigs').createIndex({ syncKey: 1 }, { name: 'notification_rule_config_sync_key', unique: true, sparse: true });
db.getCollection('notificationRuleConfigs').createIndex({ baseGroupId: 1, ruleKey: 1 }, { name: 'notification_rule_group_key', unique: true });
if (db.getCollection('notificationRuleConfigs').getIndexes().some(index => index.name === 'notification_rule_config_rule_key')) db.getCollection('notificationRuleConfigs').dropIndex('notification_rule_config_rule_key');
" >/dev/null
      ;;
    userNotifications)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('userNotifications').createIndex({ syncKey: 1 }, { name: 'user_notifications_sync_key', unique: true, sparse: true });
db.getCollection('userNotifications').createIndex({ recipientUserId: 1, readAtIso: 1, createdDate: -1, _id: -1 }, { name: 'user_notifications_recipient_read_created' });
db.getCollection('userNotifications').createIndex({ recipientUserId: 1, createdDate: -1, _id: -1 }, { name: 'user_notifications_recipient_created' });
db.getCollection('userNotifications').createIndex({ sourceType: 1, sourceId: 1, createdDate: -1 }, { name: 'user_notifications_source_created' });
db.getCollection('userNotifications').createIndex({ recipientUserId: 1, kind: 1, sourceType: 1, sourceId: 1, readAtIso: 1 }, { name: 'user_notifications_recipient_kind_source' });
" >/dev/null
      ;;
    chats)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('chats').createIndex({ ownerUserId: 1, id: 1 }, { name: 'chat_owner_public_id', unique: true });
db.getCollection('chats').createIndex({ id: 1 }, { name: 'chat_public_id_lookup' });
db.getCollection('chats').createIndex({ ownerUserId: 1, dateIso: -1 }, { name: 'chat_owner_date' });
db.getCollection('chats').createIndex({ memberIds: 1, dateIso: -1 }, { name: 'chat_member_date' });
db.getCollection('chats').createIndex({ channelType: 1, dateIso: -1 }, { name: 'chat_channel_date' });
" >/dev/null
      ;;
    chatMessages)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('chatMessages').createIndex({ chatId: 1, id: 1 }, { name: 'chat_message_lookup', unique: true });
db.getCollection('chatMessages').createIndex({ chatId: 1, sentAtIso: -1, id: -1 }, { name: 'chat_message_page_desc' });
db.getCollection('chatMessages').createIndex({ messageSenderUserIds: 1, sentAtIso: -1 }, { name: 'chat_message_sender_date' });
db.getCollection('chatMessages').createIndex({ messageReadByUserIds: 1, sentAtIso: -1 }, { name: 'chat_message_reader_date' });
db.getCollection('chatMessages').createIndex({ messageReactionUserIds: 1, sentAtIso: -1 }, { name: 'chat_message_reaction_date' });
db.getCollection('chatMessages').createIndex({ messageAttachmentKeys: 1, sentAtIso: -1 }, { name: 'chat_message_attachment_date' });
db.getCollection('chatMessages').createIndex({ pollVoterUserIds: 1, sentAtIso: -1 }, { name: 'chat_poll_voter_date' });
db.getCollection('chatMessages').createIndex({ sentAtIso: 1, 'senderAvatar.id': 1, deletedAtIso: 1 }, { name: 'chat_message_qmau_window' });
" >/dev/null
      ;;
    privacyConsents)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('privacyConsents').createIndex({ userId: 1, revisionId: 1 }, { name: 'privacy_consent_user_revision', unique: true });
db.getCollection('privacyConsents').createIndex({ userId: 1 }, { name: 'privacy_consent_user' });
db.getCollection('privacyConsents').createIndex({ revisionId: 1 }, { name: 'privacy_consent_revision' });
" >/dev/null
      ;;
    i18n_bundles)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('i18n_bundles').createIndex({ lang: 1 }, { name: 'i18n_bundles_lang', unique: true });
" >/dev/null
      ;;
    i18n_messages)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('i18n_messages').createIndex({ key: 1, lang: 1 }, { name: 'i18n_messages_key_lang', unique: true });
" >/dev/null
      ;;
    helpCenterRevisions)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('helpCenterRevisions').createIndex({ baseGroupId: 1, documentType: 1, lang: 1, contextKey: 1, version: -1 }, { name: 'help_revision_group_document_lang_context_version', unique: true });
if (db.getCollection('helpCenterRevisions').getIndexes().some(index => index.name === 'help_revision_document_lang_context_version')) db.getCollection('helpCenterRevisions').dropIndex('help_revision_document_lang_context_version');
db.getCollection('helpCenterRevisions').createIndex({ baseGroupId: 1, documentType: 1, lang: 1, contextKey: 1, active: 1, version: -1 }, { name: 'help_revision_group_document_lang_context_active' });
db.getCollection('helpCenterRevisions').createIndex({ documentType: 1 }, { name: 'help_revision_document_type' });
db.getCollection('helpCenterRevisions').createIndex({ lang: 1 }, { name: 'help_revision_lang' });
db.getCollection('helpCenterRevisions').createIndex({ contextKey: 1 }, { name: 'help_revision_context_key' });
db.getCollection('helpCenterRevisions').createIndex({ active: 1 }, { name: 'help_revision_active' });
" >/dev/null
      ;;
    helpCenterGuideFields)
      mongosh \
        --host "${mongo_host}" \
        --port "${mongo_port}" \
        --username "${mongo_username}" \
        --password "${mongo_password}" \
        --authenticationDatabase "${target_db}" \
        --quiet \
        "${target_db}" \
        --eval "
db.getCollection('helpCenterGuideFields').createIndex({ screenKey: 1, id: 1 }, { name: 'help_guide_field_screen_id', unique: true });
db.getCollection('helpCenterGuideFields').createIndex({ screenKey: 1, order: 1 }, { name: 'help_guide_field_screen_order' });
" >/dev/null
      ;;
  esac
}

ensure_collection_versions() {
  # No selected collection requires the product owner-version migration.
  :
}

ensure_runtime_indexes() {
  local target_db="$1"
  ensure_collection_indexes "${target_db}" "integrationTokens"
  ensure_collection_indexes "${target_db}" "accountDeletionWarnings"
}

ensure_collection_indexes_before_import() {
  local target_db="$1"
  local target_collection="$2"

  case "${target_collection}" in
    notificationRuleConfigs|helpCenterRevisions)
      ensure_collection_indexes "${target_db}" "${target_collection}"
      ;;
  esac
}
