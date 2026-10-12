// Complete the current release's catalog upgrade using pinned production inputs.
// Existing operator records win by both stored and semantic identity. This is a
// recorded migration, not a recurring import of editable content on every start.
const fs = require('fs');
const directory = process.env.MYSCOUTEE_MIGRATION_SEED_DIRECTORY;
const read = name => EJSON.parse(fs.readFileSync(directory + '/' + name + '.json', 'utf8'));
const catalogs = read('catalog-defaults');
const seeds = read('helpCenterRevisions');
const fields = read('helpCenterGuideFields');
if (!Array.isArray(catalogs) || !Array.isArray(seeds) || !seeds.length
    || !Array.isArray(fields) || !fields.length) throw new Error('Invalid pinned release catalogs');

for (const {collection: name, identityFields, rows} of catalogs) {
    if (!/^[A-Za-z][A-Za-z0-9]*$/.test(name) || !Array.isArray(identityFields)
        || !identityFields.length || identityFields.some(key => !/^[A-Za-z][A-Za-z0-9]*$/.test(key))
        || !Array.isArray(rows) || rows.some(row => row._id == null)) {
        throw new Error('Invalid catalog selection');
    }
    const identity = row => Object.fromEntries(identityFields.map(key => [key, row[key] ?? null]));
    const operations = rows.map(row => ({updateOne: {
        filter: {$or: [{_id: row._id}, identity(row)]},
        update: {$setOnInsert: row}, upsert: true
    }}));
    if (operations.length) db.getCollection(name).bulkWrite(operations, {ordered: true});
    print('Migration016: missing defaults checked; existing values retained: ' + name);
}

// Follow the established migration013 default/custom/inactive policy for every
// selected document scope, including new guide contexts and group variants.
const canonical = value => {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === 'object') return Object.fromEntries(
        Object.keys(value).sort().map(key => [key, canonical(value[key])]));
    return value;
};
const scope = row => JSON.stringify([row.baseGroupId ?? null, row.documentType,
    row.lang, row.contextKey ?? null]);
const contentFields = ['documentType', 'lang', 'contextKey', 'baseGroupId', 'version',
    'title', 'summary', 'description', 'headerColor', 'sections', 'createdUser',
    'updatedUser', 'languageLabel', 'presentation', 'isSystem'];
const unchanged = (row, seed) => row && seed && scope(row) === scope(seed)
    && contentFields.every(key => JSON.stringify(canonical(row[key])) === JSON.stringify(canonical(seed[key])));
const seedById = new Map(seeds.map(row => [row._id, row]));
const scopes = new Map();
for (const row of seeds) {
    if (row._id == null || !['help', 'explanation', 'privacy', 'terms'].includes(row.documentType)
        || !row.lang || !Number.isInteger(row.version) || typeof row.active !== 'boolean') {
        throw new Error('Invalid pinned help revision');
    }
    if (!scopes.has(scope(row))) scopes.set(scope(row), {seeds: [], current: []});
    scopes.get(scope(row)).seeds.push(row);
}
for (const row of db.helpCenterRevisions.find({documentType: {$in: ['help','explanation','privacy','terms']}}).toArray()) {
    if (scopes.has(scope(row))) scopes.get(scope(row)).current.push(row);
}
const operations = [];
for (const {seeds: candidates, current} of scopes.values()) {
    const activeTargets = candidates.filter(row => row.active);
    if (activeTargets.length !== 1) throw new Error('Expected one active packaged revision per scope');
    const target = activeTargets[0];
    const active = current.filter(row => row.active === true);
    const followsDefault = active.length === 1 && Number(active[0].version) < target.version
        && unchanged(active[0], seedById.get(active[0]._id));
    let installedTarget = current.find(row => row._id === target._id);
    // Insert the new active default before retiring an older one. A failed
    // ordered write is safe to retry; existing explicit inactive values win.
    for (const candidate of [target, ...candidates.filter(row => row !== target)]) {
        if (current.some(row => row._id === candidate._id || row.version === candidate.version)) continue;
        const document = {...candidate, active: candidate === target && (current.length === 0 || followsDefault)};
        operations.push({insertOne: {document}});
        if (candidate === target) installedTarget = document;
    }
    if (installedTarget?.active === true && unchanged(installedTarget, target)) {
        for (const row of active) {
            if (Number(row.version) < target.version && unchanged(row, seedById.get(row._id))) {
                operations.push({updateOne: {filter: {_id: row._id, active: true, updatedUser: row.updatedUser},
                    update: {$set: {active: false}}}});
            }
        }
    }
}
if (operations.length) db.helpCenterRevisions.bulkWrite(operations, {ordered: true});
// Guide-field metadata is package-owned, as in migrations010/011. Custom IDs
// outside this pinned selection survive; editable help text is handled above.
if (fields.some(row => !row._id || !row.screenKey || !row.id)) throw new Error('Invalid guide-field metadata');
db.helpCenterGuideFields.bulkWrite(fields.map(row => ({replaceOne: {
    filter: {_id: row._id}, replacement: row, upsert: true
}})), {ordered: true});
print('Migration016: catalog upgrade completed; operator selections and user data retained.');
