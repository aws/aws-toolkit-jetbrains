import json
import re
import sys

if __name__ == '__main__':
    arg = sys.argv[1:][0]
    if arg == '-':
        data = json.load(sys.stdin)
    else:
        with open(arg) as f:
            data = json.load(f)

    xml = ['<?xml version="1.0" encoding="UTF-8"?>', '<plugins>']

    buildRegex = r'.*(\d{3}).zip'
    # given plugin-amazonq-3.39-SNAPSHOT-1731096007-241.zip,
    # capture 3.39-SNAPSHOT-1731096007-241 in group 1
    # or
    # given plugin-amazonq-3.39-SNAPSHOT-1731096007.241.zip,
    # capture 3.39-SNAPSHOT-1731096007.241 in group 1
    versionRegex = r'.*?\-(\d.*[.-]\d{3})\.zip$'
    for asset in data['assets']:
        name = asset['name']
        plugin = 'amazon.q'
        build = re.match(buildRegex, name)
        if build == None:
            continue
        build = build.group(1)
        version = re.match(versionRegex, name).group(1)

        xml.append(f'''<plugin id="{plugin}" url="{asset['url']}" version="{version}">
    <idea-version since-build="{build}" until-build="{build}.*"/>
</plugin>''')

    xml.append('</plugins>')

    print('\n'.join(xml))
