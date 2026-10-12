"""Selected template collections and their environment boundaries."""
import copy
import json
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

SHARED = ('accessPolicyDefinitions', 'countryPartitions', 'helpCenterGuideFields',
          'helpCenterRevisions', 'i18n_bundles', 'i18n_messages')
CONFIGURED = ('appSettings', 'deploymentConfigurations', 'notificationRuleConfigs')
RUNTIME = ()
DEMO_ONLY_RUNTIME = ('chatMessages', 'chats', 'contactChatAccess', 'sampleCards', 'userNotifications', 'users')


def audit_fixture(data, label, errors):
    """Check selected references against the native message/counter contracts."""
    root = Path(__file__).resolve().parents[2]
    users = {row.get('userId', row.get('id')): row for row in data['users']}
    chats = data['chats']
    messages = data['chatMessages']
    notifications = data['userNotifications']

    def require(condition, detail):
        if not condition:
            errors.append(f'{label}: {detail}')

    channels = {chat['id']: set(chat['memberIds']) for chat in chats}
    for chat in chats:
        owner, channel = chat['ownerUserId'], chat['id']
        require(chat['channelType'] == 'contact', 'omitted chat domain in fixture')
        require(owner in channels[channel] <= users.keys(), 'invalid chat member/owner reference')
        thread = [message for message in messages if message['chatId'] == channel]
        unread = sum(message['senderAvatar']['id'] != owner
                     and owner not in message['messageReadByUserIds']
                     and not message.get('deletedAtIso') for message in thread)
        require(chat['unread'] == unread, f'{owner}: chat unread counter differs')
        if thread:
            latest = max(thread, key=lambda message: message['sentAtIso'])
            require((chat['lastMessage'], chat['lastSenderId'], chat['dateIso']) ==
                    (latest['text'], latest['senderAvatar']['id'], latest['sentAtIso']),
                    f'{owner}: chat summary differs from latest message')
    for message in messages:
        members = channels.get(message['chatId'], set())
        sender = message['senderAvatar']['id']
        readers = {reader['id'] for reader in message['readBy']}
        require(sender in members and readers <= members, 'invalid message member reference')
        require(set(message['messageSenderUserIds']) == {sender}
                and set(message['messageReadByUserIds']) == readers, 'message lookup IDs differ')
    for access in data['contactChatAccess']:
        pair = {access['leftUserId'], access['rightUserId']}
        require(pair <= users.keys() and access['requestedBy'] in pair,
                'invalid contact access reference')
    for notification in notifications:
        require(notification['recipientUserId'] in users and notification['sourceType'] == 'user'
                and notification['sourceId'] in users, 'invalid notification reference/domain')
    for owner, user in users.items():
        unread = sum(chat['unread'] for chat in chats if chat['ownerUserId'] == owner)
        activity = user['activities']
        require(activity['chats'] == activity['chat']['all'] == activity['chat']['contacts'] == unread
                and activity['chat']['appSupport'] == 0, f'{owner}: user chat counters differ')
        require(activity['notifications'] == sum(n['recipientUserId'] == owner and not n['readAtIso']
                                                for n in notifications),
                f'{owner}: notification counter differs')

    def media(value):
        if isinstance(value, dict):
            for child in value.values():
                media(child)
        elif isinstance(value, list):
            for child in value:
                media(child)
        elif isinstance(value, str):
            if value.startswith('/media/public?'):
                key = parse_qs(urlsplit(value).query).get('key', [''])[0]
                require(bool(key) and (root / 'server/docker/conf/minio/media' / key).is_file(),
                        f'missing packaged media: {key}')
            elif value.startswith('assets/'):
                require((root / 'frontend/src' / value).is_file(), f'missing browser asset: {value}')
    media(data)


def audit_project(data, errors):
    demo = data['demo_db']
    for mode in ('demo_db', 'e2e_db'):
        audit_fixture(data[mode], mode, errors)
    local_root = Path(__file__).resolve().parents[2] / 'frontend/src/app/shared/core/local/seed/data'
    local_fixture = {name: json.loads((local_root / (filename + '.json')).read_text())
                     for name, filename in [('users', 'users'), ('chats', 'chats'),
                                            ('chatMessages', 'chatMessages'),
                                            ('contactChatAccess', 'contactChatAccess'),
                                            ('userNotifications', 'notifications'),
                                            ('sampleCards', 'sample-cards')]}
    audit_fixture(local_fixture, 'frontend-local', errors)
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
