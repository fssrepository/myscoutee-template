#!/usr/bin/env python3
"""Inspect the actual template DEB without installing it or starting a service."""
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
artifact = Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'packaging/dist/myscoutee-template_1.0.0_amd64.deb'
fields = subprocess.check_output(['dpkg-deb','-f',str(artifact),'Package','Version','Architecture'],text=True)
assert fields == 'Package: myscoutee-template\nVersion: 1.0.0\nArchitecture: amd64\n',fields
prefix='./opt/myscoutee-template/'
wanted=['packaging/IMAGE-MANIFEST.json','packaging/scripts/release-project.json',
        'packaging/templates/install-policy.json','packaging/scripts/release-system-seeds.json',
        'packaging/scripts/recovery-project.sh','packaging/scripts/sync-release-system-seeds.js']
content={};names=[];modes={}
process=subprocess.Popen(['dpkg-deb','--fsys-tarfile',str(artifact)],stdout=subprocess.PIPE)
with tarfile.open(fileobj=process.stdout,mode='r|') as stream:
    for entry in stream:
        assert entry.uid==entry.gid==0,entry.name
        assert not entry.issym() and not entry.islnk(),entry.name
        if not entry.isfile():continue
        names.append(entry.name);modes[entry.name]=entry.mode
        if entry.name in [prefix+name for name in wanted]:
            content[entry.name[len(prefix):]]=stream.extractfile(entry).read()
assert process.wait()==0
assert not any(name.startswith('./opt/myscoutee/') for name in names)
assert not any(name.startswith(prefix+'server/docker/scripts/') for name in names)
assert not any('/e2e_db/' in name or '/dev/' in name or name.endswith('/.env.secrets') for name in names)
assert not any(name.endswith(('/ensure-deployment-identity.sh','/validate-registry-state.jq')) for name in names)
policy=json.loads(content['packaging/templates/install-policy.json'])
assert policy['ownedDirectories']==policy['migrateOwnership']==policy['ensureSecrets']==[]
assert len(policy['freshSecrets'])==3
assert not any(key.startswith(('REGISTRY_','PAYMENT_','AFFINITY_')) for key in policy['environmentDefaults'])
seed_prefix=prefix+'server/docker/conf/mongodb/myscoutee_db/'
seed_names={Path(name).stem for name in names if name.startswith(seed_prefix) and name.endswith('.json')}
assert seed_names=={'accessPolicyDefinitions','appSettings','countryPartitions','deploymentConfigurations',
                    'helpCenterGuideFields','helpCenterRevisions','i18n_bundles','i18n_messages','notificationRuleConfigs'},seed_names
release_seeds=json.loads(content['packaging/scripts/release-system-seeds.json'])
assert {row['collection'] for row in release_seeds}=={'notificationRuleConfigs','countryPartitions','i18n_messages','i18n_bundles'}
manifest=json.loads(content['packaging/IMAGE-MANIFEST.json'])
assert manifest['releaseVersion']=='1.0.0' and manifest['bundled'] is True
assert len(manifest['images'])==5
assert len([name for name in names if name.startswith(prefix+'images/') and name.endswith('.tar')])==5
for row in manifest['images']:
    assert row['reference'].startswith('myscoutee-template-'),row
for name in ['backup-production.sh','restore-production.sh','myscoutee-compose.sh','myscoutee-update-worker.sh',
             'myscoutee-tls-worker.sh','qualify-deployment.sh','reset-demo-state.sh','migrate-production-data.sh']:
    assert modes[prefix+'packaging/scripts/'+name]==0o755,name
for path,body in content.items():
    if path!='packaging/IMAGE-MANIFEST.json':assert body==(ROOT/path).read_bytes(),path
with tempfile.TemporaryDirectory(prefix='template-deb-control-') as directory:
    control=Path(directory)
    subprocess.run(['dpkg-deb','-e',str(artifact),directory],check=True)
    for name in ['postinst','prerm']:
        assert (control/name).read_bytes()==(ROOT/'packaging/debian'/name).read_bytes(),name
    subprocess.run(['python3',str(ROOT/'packaging/release/render-postrm.py'),str(ROOT/'packaging'),str(control/'expected-postrm')],check=True)
    assert (control/'postrm').read_bytes()==(control/'expected-postrm').read_bytes()
    assert '@PACKAGE_' not in (control/'postrm').read_text()
    for name in ['postinst','prerm','postrm']:
        subprocess.run(['bash','-n',str(control/name)],check=True)
        assert (control/name).stat().st_mode & 0o777 == 0o755
print('PASS: actual DEB identity, five bundled images, nine production catalogs, selected recovery/seed policy, executable host helpers and rendered maintainer scripts; no dev/test/product runtime payload.')
