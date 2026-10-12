#!/usr/bin/env python3
"""Generate a Dockerfile per configured assembly from the common image recipe."""
import importlib.util
from pathlib import Path
import json
from runtime_config import CONFIG, LAB

spec = importlib.util.spec_from_file_location('assembly_audit', Path(__file__).with_name('audit-assemblies.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
services, _ = audit.configuration()
template = (audit.SHELL / 'deploy/images/service.Dockerfile.template').read_text()
output = audit.SHELL / 'build/deployment'
output.mkdir(parents=True, exist_ok=True)
for name, service in services.items():
    stage = 'media-runtime' if 'media' in service['domains'] else 'jre-runtime'
    if service['runtimeStage'] != stage:
        raise ValueError(f'{name}: runtimeStage must be {stage} for its domains')
    text = (template.replace('@SERVICE@', name).replace('@RUNTIME_IMAGE@', CONFIG['images']['prefix'] + stage + ':' + CONFIG['images']['buildTag'])
            .replace('@HEAP_MIB@', str(service['heapMiB'])).replace('@ISSUE@', json.loads((audit.SHELL / 'assemblies.json').read_text())['issue']))
    (output / f'{name}.Dockerfile').write_text(text)
    print(f'{name}: {stage}, heap {service["heapMiB"]} MiB / container {service["memoryMiB"]} MiB')

# JSON is valid YAML; the target list follows the same deployment inventory.
import json
import vm
runtime = json.loads((audit.SHELL / 'deploy/runtime.json').read_text())
monitoring = audit.SHELL / 'build/observability'
monitoring.mkdir(parents=True, exist_ok=True)
(monitoring / 'prometheus.yml').write_text(json.dumps({
    'global': {'scrape_interval': '30s', 'scrape_timeout': '5s'},
    'scrape_configs': [
        {'job_name': 'prometheus', 'static_configs': [{'targets': ['127.0.0.1:' + str(LAB['prometheusPort'])]}]},
        {'job_name': 'myscoutee',
         'static_configs': [{'targets': [vm.IP + ':' + str(runtime['metricsNodePort'])], 'labels': {'service': name}} for name in services],
         'relabel_configs': [{'source_labels': ['service'], 'target_label': '__metrics_path__', 'replacement': '/metrics/$1'}]}
    ]
}, indent=2) + '\n')

# Reuse the versioned dashboard and provisioning; only project labels/ports vary.
for source in (audit.SHELL / 'observability/grafana').rglob('*'):
    if not source.is_file():
        continue
    target = monitoring / 'grafana' / source.relative_to(audit.SHELL / 'observability/grafana')
    target.parent.mkdir(parents=True, exist_ok=True)
    text = source.read_text()
    for key, value in {'DISPLAY_NAME': LAB['displayName'],
                       'ISSUE': json.loads((audit.SHELL / 'assemblies.json').read_text())['issue'],
                       'PROMETHEUS_PORT': str(LAB['prometheusPort'])}.items():
        text = text.replace('@' + key + '@', json.dumps(value)[1:-1])
    target.write_text(text)
