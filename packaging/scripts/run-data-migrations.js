{
// Ordered, recorded migration chain shared by package startup and partial releases.
const fs = require('fs');
const crypto = require('crypto');
const directory = process.env.MYSCOUTEE_MIGRATION_DIRECTORY;
if (!directory) throw new Error('Missing migration directory.');
const configuration = db.deploymentConfigurations.findOne({_id: 'deployment-configuration'});
const supportedSchema = 5;
if (configuration && (!Number.isSafeInteger(Number(configuration.schemaVersion))
        || Number(configuration.schemaVersion) < 3 || Number(configuration.schemaVersion) > supportedSchema)) {
    throw new Error('Unsupported source schema; no migration applied.');
}
const files = fs.readdirSync(directory).filter(name => /^\d{3}-[a-z0-9-]+\.js$/.test(name)).sort();
if (!files.length) throw new Error('No release migrations found.');
for (const filename of files) {
    const source = fs.readFileSync(directory + '/' + filename, 'utf8');
    const checksum = crypto.createHash('sha256').update(source).digest('hex');
    const id = filename.replace(/\.js$/, '');
    const applied = db.schemaMigrations.findOne({_id: id});
    if (applied) {
        if (applied.sha256 !== checksum) throw new Error('Applied migration changed: ' + id);
        print('Already applied: ' + id);
        continue;
    }
    const executionFile = directory + '/.running-migration.js';
    fs.writeFileSync(executionFile, '{\n' + source + '\n}', {mode: 0o600});
    try { load(executionFile); } finally { fs.unlinkSync(executionFile); }
    db.schemaMigrations.insertOne({_id: id, sha256: checksum, appliedAt: new Date()});
    print('Recorded migration: ' + id);
}

}
