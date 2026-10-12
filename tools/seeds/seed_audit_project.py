"""Selected template collections and their environment boundaries."""
import copy
import json
from pathlib import Path

SHARED = ('accessPolicyDefinitions', 'countryPartitions', 'helpCenterGuideFields',
          'helpCenterRevisions', 'i18n_bundles', 'i18n_messages')
CONFIGURED = ('appSettings', 'deploymentConfigurations', 'notificationRuleConfigs')
RUNTIME = ()
DEMO_ONLY_RUNTIME = ('chatMessages', 'chats', 'contactChatAccess', 'sampleCards', 'userNotifications', 'users')


def audit_project(data, errors):
    demo = data['demo_db']
    local = json.loads((Path(__file__).resolve().parents[2] / 'frontend/src/app/shared/core/local/seed/data/deployment-configuration.json').read_text())
    for key in ('productName', 'homeLabel', 'logoUrl', 'logoCharacterIndex', 'revision'):
        if demo['deploymentConfigurations'][0]['branding'][key] != local[key]:
            errors.append(f'demo_db/deploymentConfigurations: browser-local branding differs: {key}')
    for mode in ('myscoutee_db', 'e2e_db'):
        target = data[mode]
        for name in SHARED:
            if target[name] != demo[name]:
                errors.append(f'{mode}/{name}: shared catalog differs')
        for name in CONFIGURED:
            expected = copy.deepcopy(demo[name])
            if mode == 'myscoutee_db' and name == 'appSettings':
                expected[0]['adminEmails'] = []
            if mode == 'myscoutee_db' and name == 'deploymentConfigurations':
                expected[0]['integration']['publicBaseUrl'] = None
            if target[name] != expected:
                errors.append(f'{mode}/{name}: unexpected configuration difference')
        if mode == 'myscoutee_db':
            for name in DEMO_ONLY_RUNTIME + ('adminUsers',):
                if target.get(name, []) != []:
                    errors.append(f'{mode}/{name}: production identities/runtime data must be empty')
        else:
            for name in DEMO_ONLY_RUNTIME:
                if target[name] != demo[name]:
                    errors.append(f'{mode}/{name}: isolated fixture differs from demo')
    if any(row.get('schemaVersion') != 5 for mode in data.values() for row in mode['deploymentConfigurations']):
        errors.append('deployment configuration must retain schema v5')
