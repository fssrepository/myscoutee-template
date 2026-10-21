// Publish the canonical keys before retiring their unchanged package defaults.
// The normal release seed sync publishes the advanced catalog version afterward.
const renames = JSON.parse(require('fs').readFileSync(
    process.env.MYSCOUTEE_MIGRATION_SEED_DIRECTORY + '/key-renames.json', 'utf8'));
if (!Array.isArray(renames) || !renames.length || renames.some(row =>
    typeof row.from !== 'string' || !row.from || !row.target?._id
    || typeof row.target.key !== 'string' || row.from === row.target.key
    || !['en', 'hu'].includes(row.target.lang)
    || typeof row.target.value !== 'string')) {
    throw new Error('Invalid UI translation key renames.');
}
db.i18n_messages.bulkWrite(renames.map(({target}) => ({updateOne: {
    filter: {_id: target._id}, update: {$setOnInsert: target}, upsert: true
}})), {ordered: true});
// Match the pinned default value too: a concurrently edited or customized
// legacy translation is retained, rather than deleted by its key alone.
const retired = db.i18n_messages.deleteMany({$or: renames.map(({from, target}) => ({
    key: from, lang: target.lang, value: target.value
}))});
print('Migration019: retired ' + retired.deletedCount + ' unchanged legacy UI translation keys.');
