#!/usr/bin/env python3
"""Application defaults for the shared non-delivering provider fixture."""
import os
from pathlib import Path
import runpy
import sys
project = Path(__file__).resolve().parents[5]
source = Path(os.environ.get('MYSCOUTEE_QA_SOURCE', project / 'tests/qa/node_modules/@fssrepository/myscoutee-qa'))
if '--retry-scenario' in sys.argv and '--retry-recipient' not in sys.argv:
    sys.argv.extend(['--retry-recipient', 'email-qa-0@example.invalid'])
runpy.run_path(str(source / 'providers/email-provider-stub.py'), run_name='__main__')
