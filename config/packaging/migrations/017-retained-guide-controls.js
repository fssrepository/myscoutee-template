// Retained native controls changed identifiers. Update only package-owned guide
// metadata; preserve custom IDs, editable guide text and all user/session data.
const fs = require('fs');
const directory = process.env.MYSCOUTEE_MIGRATION_SEED_DIRECTORY;
const {fields, removedIds, revisions} = EJSON.parse(fs.readFileSync(directory + '/guide-field-updates.json', 'utf8'));
if (!Array.isArray(fields) || !fields.length || fields.some(row => !row._id || !row.screenKey || !row.id)
    || !Array.isArray(revisions) || revisions.some(row => !row.previous?._id || row.previous._id !== row.target?._id)
    || !Array.isArray(removedIds) || removedIds.some(id => typeof id !== 'string' || !id)) {
    throw new Error('Invalid retained guide-field metadata');
}
// Same package-owned metadata policy as migration016. Upsert first so an
// interrupted run can be repeated without leaving the retained controls absent.
db.helpCenterGuideFields.bulkWrite(fields.map(row => ({replaceOne: {
    filter: {_id: row._id}, replacement: row, upsert: true
}})), {ordered: true});
if (removedIds.length) db.helpCenterGuideFields.deleteMany({_id: {$in: removedIds}});
const canonical = value => Array.isArray(value) ? value.map(canonical)
    : value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
for (const {previous, target} of revisions) {
    const stored = db.helpCenterRevisions.findOne({_id: previous._id});
    if (JSON.stringify(canonical(stored)) === JSON.stringify(canonical(previous))) {
        db.helpCenterRevisions.replaceOne({_id: previous._id}, target);
    }
}
print('Migration017: retained native guide controls updated; custom fields and user data retained.');
