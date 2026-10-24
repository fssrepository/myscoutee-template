// Integration indexes belong to Mongo initialization and versioned migrations.
// Keep existing names/options and all stored credentials, grants and delivery data.
db.getCollection('integrationTokens').createIndex({ tokenHash: 1 }, { name: 'integration_token_hash', unique: true });
db.getCollection('integrationTokens').createIndex({ ownerUserId: 1, createdAtIso: -1 }, { name: 'integration_token_owner_created' });
db.getCollection('mcpOAuthClients').createIndex({ createdAt: 1 }, { name: 'createdAt' });
db.getCollection('mcpOAuthClients').createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });
db.getCollection('mcpAuthorizations').createIndex({ clientId: 1 }, { name: 'clientId' });
db.getCollection('mcpAuthorizations').createIndex({ accessHash: 1 }, { name: 'accessHash' });
db.getCollection('mcpAuthorizations').createIndex({ refreshHash: 1 }, { name: 'refreshHash' });
db.getCollection('mcpAuthorizations').createIndex({ expiresAt: 1 }, { name: 'expiresAt', expireAfterSeconds: 0 });
print('Migration012: integration indexes ensured.');
