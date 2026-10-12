"""Project isolation checks; no host/VM/database mutations."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import runtime_config as runtime
import workloads


class RuntimeConfigTest(unittest.TestCase):
    def test_state_cannot_escape_or_select_an_absolute_host_path(self):
        for value in ('/var/lib/libvirt', '../other', 'lab/../../other', ''):
            with self.subTest(value=value):
                config = copy.deepcopy(runtime.CONFIG)
                config['lab']['stateDirectory'] = value
                with self.assertRaises(ValueError):
                    runtime.validate(config)

    def test_real_demo_and_bootstrap_cannot_share_a_database(self):
        config = copy.deepcopy(runtime.CONFIG)
        config['databases']['demo'] = config['databases']['real']
        with self.assertRaisesRegex(ValueError, 'distinct'):
            runtime.validate(config)

    def test_vm_network_rejects_public_loopback_and_link_local_subnets(self):
        for value in ('8.8.8.0/24', '127.0.0.0/24', '169.254.1.0/24', '192.168.173.0/25'):
            with self.subTest(value=value):
                config = copy.deepcopy(runtime.CONFIG)
                config['lab']['networkCidr'] = value
                with self.assertRaises(ValueError):
                    runtime.validate(config)

    def test_project_ports_do_not_alias_monitoring_and_application(self):
        config = copy.deepcopy(runtime.CONFIG)
        config['lab']['grafanaPort'] = config['localPort']
        with self.assertRaisesRegex(ValueError, 'distinct'):
            runtime.validate(config)

    def test_omitted_dependencies_do_not_create_product_credentials_or_objects(self):
        config = copy.deepcopy(workloads.CONFIG)
        for dependency in ('registry', 'payment-simulator'):
            config['dependencies'].pop(dependency, None)
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(workloads, 'CONFIG', config), \
             patch.object(workloads, 'TRANSPORTS', [binding for binding in workloads.TRANSPORTS
                                                   if binding['domain'] not in ('payments', 'registry')]), \
             patch.object(workloads, 'SECRET_FILE', Path(directory) / 'secrets.json'):
            first = workloads.secrets_data()
            second = workloads.secrets_data()
            self.assertEqual(first, second)
            self.assertFalse(any(key.startswith('paymentSimulator') for key in first))
            self.assertFalse(any(name in ('registry', 'registry-client', 'payment-simulator')
                                 for _, name in workloads.chart_objects()))
            self.assertIn(('Secret', 'deployment-configuration'), workloads.chart_objects())
            self.assertIn(('Service', 'mcp'), workloads.chart_objects())


if __name__ == '__main__':
    unittest.main()
