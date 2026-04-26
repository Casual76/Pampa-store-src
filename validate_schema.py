import sys
sys.path.append(r'C:\VibeCoded Projects\Pampa-store-src\plugins\pampa-store-publisher\mcp')
from server import TOOLS

missing = False
for t in TOOLS:
    props = t['inputSchema'].get('properties', {})
    for k, v in props.items():
        if 'type' not in v and 'anyOf' not in v:
            print(f"Tool {t['name']} property {k} is missing type.")
            missing = True

if not missing:
    print("All good!")
