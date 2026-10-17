// Match the retained media worker before services start. The existing runtime
// initializer owns retirement of its pre-status development index.
const fs = require('fs');
const directory = process.env.MYSCOUTEE_MIGRATION_SEED_DIRECTORY;
if (!directory) throw new Error('Missing pinned media worker index snapshot.');
const catalog = JSON.parse(fs.readFileSync(directory + '/runtime-indexes.json', 'utf8'));
for (const {collection, key, options} of catalog.indexes) {
    db.getCollection(collection).createIndex(key, options);
}
print('Migration018: retained media worker index ensured; documents unchanged.');
