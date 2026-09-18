#!/usr/bin/env python3
"""Prepare a signed APK locally, then explicitly publish that exact artifact."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def run(*args, capture=True):
    result = subprocess.run(args, cwd=ROOT, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else None


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


def sdk_tools():
    config = properties(ROOT / 'local.properties') if (ROOT / 'local.properties').exists() else {}
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or config.get('sdk.dir', str(Path.home() / 'Library/Android/sdk')))
    versions = sorted((sdk / 'build-tools').glob('*'),
                      key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.name)))
    for folder in reversed(versions):
        if (folder / 'apksigner').exists() and (folder / 'aapt').exists():
            return folder
    raise ValueError('Android build-tools with apksigner and aapt are required.')


def inspect_apk(apk):
    tools = sdk_tools()
    cert = run(str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk))
    if 'CN=Android Debug' in cert:
        raise ValueError('Refusing a debug signing certificate. Configure the release keystore first.')
    info = run(str(tools / 'aapt'), 'dump', 'badging', str(apk))
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", info)
    if not match or match[1] != 'com.pelonot' or 'application-debuggable' in info:
        raise ValueError('Expected a non-debuggable com.pelonot APK.')
    return int(match[2]), match[3]


def digest(path):
    with path.open('rb') as source:
        checksum = hashlib.sha256()
        for block in iter(lambda: source.read(1024 * 1024), b''):
            checksum.update(block)
        return checksum.hexdigest()


def repository():
    remote = run('git', 'remote', 'get-url', 'origin')
    match = re.fullmatch(r'(?:https://github.com/|git@github.com:)([\w.-]+/[\w.-]+?)(?:\.git)?', remote)
    if not match:
        raise ValueError('origin must name a GitHub repository.')
    return match[1]


def prepare(args):
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', args.version):
        raise ValueError('Use a version such as 1.0.1 or 1.0.1-beta.1.')
    if not args.notes.strip() or len(args.notes) > 1000:
        raise ValueError('Supply a short rider-facing release note (1–1000 characters).')
    if run('git', 'status', '--porcelain'):
        raise ValueError('Commit or move untracked files and changes before preparing a release.')
    version_file = ROOT / 'version.properties'
    original = version_file.read_text()
    current = properties(version_file)
    code = int(current['versionCode']) + 1
    live = ROOT / 'web/update.json'
    if live.exists():
        code = max(code, int(json.loads(live.read_text())['version_code']) + 1)
    repo = repository()
    folder = ROOT / 'app/build/releases' / args.version
    if folder.exists():
        raise ValueError(f'{folder} already exists; inspect it before preparing again.')
    updated = re.sub(r'^versionCode=.*$', f'versionCode={code}', original, flags=re.M)
    updated = re.sub(r'^versionName=.*$', f'versionName={args.version}', updated, flags=re.M)
    version_file.write_text(updated)
    try:
        run('./gradlew', 'assembleRelease', 'testDebugUnitTest', capture=False)
        metadata = json.loads((ROOT / 'app/build/outputs/apk/release/output-metadata.json').read_text())
        elements = metadata['elements']
        if len(elements) != 1:
            raise ValueError('Expected one universal APK.')
        source = ROOT / 'app/build/outputs/apk/release' / elements[0]['outputFile']
        if inspect_apk(source) != (code, args.version):
            raise ValueError('APK version does not match version.properties.')
        folder.mkdir(parents=True)
        apk = folder / f'pelonot-{args.version}.apk'
        shutil.copyfile(source, apk)
        manifest = dict(version_code=code, version_name=args.version, notes=args.notes.strip(),
                        url=f'https://github.com/{repo}/releases/download/v{args.version}/{apk.name}',
                        sha256=digest(apk))
        (folder / 'update.json').write_text(json.dumps(manifest, indent=2) + '\n')
        (folder / 'notes.txt').write_text(args.notes.strip() + '\n')
        (folder / 'source.json').write_text(json.dumps({'base_commit': run('git', 'rev-parse', 'HEAD'),
                                                       'repository': repo}, indent=2) + '\n')
    except BaseException:
        version_file.write_text(original)
        raise
    print(f'Prepared {folder}\nReview and commit version.properties, then run:\n'
          f'  ./tools/release.sh publish {args.version}')


def publish(args):
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', args.version):
        raise ValueError('Invalid version.')
    if run('git', 'status', '--porcelain'):
        raise ValueError('Commit or move untracked files and changes before publishing.')
    folder = ROOT / 'app/build/releases' / args.version
    manifest = json.loads((folder / 'update.json').read_text())
    source = json.loads((folder / 'source.json').read_text())
    repo = repository()
    if source['repository'] != repo:
        raise ValueError('The repository changed since preparation.')
    # Only the version bump may change after the binary was built.
    changed = run('git', 'diff', '--name-only', source['base_commit'], 'HEAD').splitlines()
    if set(changed) - {'version.properties'}:
        raise ValueError('Source changed after preparation; prepare a fresh release.')
    current = properties(ROOT / 'version.properties')
    apk = folder / f'pelonot-{args.version}.apk'
    if (int(current['versionCode']), current['versionName']) != inspect_apk(apk):
        raise ValueError('The prepared APK no longer matches the committed version.')
    if manifest['version_code'] != int(current['versionCode']) or manifest['version_name'] != args.version:
        raise ValueError('Manifest version mismatch.')
    expected_url = f'https://github.com/{repo}/releases/download/v{args.version}/{apk.name}'
    if manifest['url'] != expected_url or digest(apk) != manifest['sha256']:
        raise ValueError('The prepared artifact or manifest changed.')
    head = run('git', 'rev-parse', 'HEAD')
    # GitHub must already have the commit to attach the release to it.
    run('gh', 'api', f'repos/{repo}/commits/{head}', '--jq', '.sha')
    run('gh', 'release', 'create', f'v{args.version}', str(apk), '--repo', repo,
        '--target', head, '--title', f'Pelonot {args.version}',
        '--notes-file', str(folder / 'notes.txt'), capture=False)
    # Advertising the update is the last step, after GitHub accepted the APK.
    shutil.copyfile(folder / 'update.json', ROOT / 'web/update.json')
    print('APK published. Review and commit web/update.json, then git push to deploy the update offer.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    p = commands.add_parser('prepare', help='Bump the version, build and verify locally; never publish')
    p.add_argument('version')
    p.add_argument('--notes', required=True)
    p = commands.add_parser('publish', help='Upload the prepared APK to GitHub and stage its manifest in web/')
    p.add_argument('version')
    args = parser.parse_args()
    try:
        (prepare if args.command == 'prepare' else publish)(args)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))


if __name__ == '__main__':
    main()
