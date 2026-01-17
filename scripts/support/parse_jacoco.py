#!/usr/bin/env python3
"""Parse JaCoCo XML and list classes with missed lines."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
p = Path('target/site/jacoco/jacoco.xml')
if not p.exists():
    print('ERROR: target/site/jacoco/jacoco.xml not found')
    sys.exit(2)
try:
    tree = ET.parse(p)
except ET.ParseError as e:
    print('ERROR: failed to parse XML:', e)
    sys.exit(2)
root = tree.getroot()
found = 0
for package in root.findall('package'):
    pkg = package.get('name')
    for cls in package.findall('class'):
        name = cls.get('name')
        for counter in cls.findall('counter'):
            if counter.get('type') == 'LINE':
                missed = int(counter.get('missed'))
                covered = int(counter.get('covered'))
                if missed > 0:
                    print(f"{pkg.replace('/', '.')}.{name} missed={missed} covered={covered}")
                    found += 1
if found == 0:
    print('No classes with missed lines found')
sys.exit(0)
