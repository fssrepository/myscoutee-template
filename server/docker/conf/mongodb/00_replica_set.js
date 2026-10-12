// Shared fresh-volume hook and authenticated final-daemon initializer.
(() => {
  const name = (process.env.MONGO_REPLICA_SET_NAME || process.env.MONGODB_REPLICA_SET || '').trim();
  if (!name) return;
  const host = (process.env.MONGO_REPLICA_SET_HOST || 'mongodb:27017').trim();
  const ensureReady = process.env.MONGO_REPLICA_SET_ENSURE_READY === 'true';
  const admin = db.getSiblingDB('admin');
  if (ensureReady && !admin.auth(process.env.MONGO_INITDB_ROOT_USERNAME, process.env.MONGO_INITDB_ROOT_PASSWORD).ok) {
    throw new Error('Mongo replica bootstrap authentication failed');
  }
  const options = admin.runCommand({getCmdLineOpts: 1});
  if (!options.ok) throw new Error('Cannot inspect Mongo startup options');
  const configuredName = options.parsed.replication?.replSetName || options.parsed.replication?.replSet;
  if (!configuredName) {
    if (ensureReady) throw new Error('Waiting for final replica-enabled Mongo daemon');
    print('Replica initialization deferred until the official entrypoint starts the final daemon.');
    return;
  }
  if (configuredName !== name) throw new Error('Mongo replica startup name differs');
  let config;
  try {
    config = rs.conf();
  } catch (error) {
    if (error.code !== 94) throw error; // Only an uninitialized replica may be initialized.
    const result = rs.initiate({_id: name, members: [{_id: 0, host}]});
    if (result.ok !== 1) throw new Error('Mongo replica initialization rejected');
    config = rs.conf();
  }
  if (config._id !== name || !config.members.some(member => member.host === host)) {
    throw new Error('Existing Mongo replica identity differs; refusing reconfiguration');
  }
  if (ensureReady && !admin.runCommand({hello: 1}).isWritablePrimary) {
    throw new Error('Waiting for the Mongo replica primary');
  }
  print(`Replica set ${name} is ready.`);
})();
