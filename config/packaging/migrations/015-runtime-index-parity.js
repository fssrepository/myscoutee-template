// Bring retained installations to the existing DEV/import index definitions
// before release seeds are synchronized. No document is updated or removed.
// This release snapshot is immutable once applied, like the other migrations.
const fs = require('fs');
const directory = process.env.MYSCOUTEE_MIGRATION_SEED_DIRECTORY;
if (!directory) throw new Error('Missing pinned runtime index snapshot.');
const catalog = JSON.parse(fs.readFileSync(directory + '/runtime-indexes.json', 'utf8'));
for (const {collection, key, options} of catalog.indexes) {
    db.getCollection(collection).createIndex(key, options);
}
// Build the replacement constraints first. A genuine conflicting record stops
// the migration; never delete user data or retire a constraint to hide it.
for (const {collection, name} of catalog.retiredIndexes) {
    const target = db.getCollection(collection);
    if (target.getIndexes().some(index => index.name === name)) target.dropIndex(name);
}
print('Migration015: ' + catalog.indexes.length + ' existing runtime indexes ensured; documents unchanged.');
