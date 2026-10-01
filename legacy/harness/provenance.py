#!/usr/bin/env python3
"""Writes golden/PROVENANCE.json: SHA-256 of every input that determines the golden data.

    provenance.py <repo>   (run.sh passes the recording parameters as environment variables)

Each tree is hashed as SHA-256 over the lines "<sha256 of file>  <relative path>\\n", sorted by
path (C locale), which is the output format of `shasum -a 256` run in that directory.
Contains no times and no host names, so two recordings of the same inputs produce the same file.
"""
import hashlib, json, os, subprocess, sys

repo = sys.argv[1]


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def tree(root, skip=lambda rel: os.path.basename(rel) == '.DS_Store'):
    lines = []
    for d, dirs, files in os.walk(root):
        dirs.sort()
        for fn in files:
            p = os.path.join(d, fn)
            rel = os.path.relpath(p, root)
            if skip(rel):
                continue
            lines.append((rel, sha(p)))
    lines.sort(key=lambda x: x[0].encode())
    text = ''.join(f'{h}  {rel}\n' for rel, h in lines)
    return {"files": len(lines), "sha256": hashlib.sha256(text.encode()).hexdigest()}


def files(root, pattern_ext):
    out = {}
    for fn in sorted(os.listdir(root), key=str.encode):
        if fn.endswith(pattern_ext):
            out[fn] = sha(os.path.join(root, fn))
    return out


L = os.path.join(repo, 'legacy')
H = os.path.join(L, 'harness')
java_home = os.environ.get('JAVA_HOME', '/opt/homebrew/opt/openjdk')
jv = subprocess.run([os.path.join(java_home, 'bin', 'java'), '-version'], capture_output=True, text=True).stderr.strip()

rec = {
    "legacySource": dict(tree(os.path.join(L, 'runnable', 'src')), path="legacy/runnable/src"),
    "legacyClasses": dict(tree(os.path.join(H, 'legacy-classes')), path="legacy/harness/legacy-classes",
                          note="compiled from legacy/runnable/src by legacy/harness/build.sh"),
    "originalJars": files(os.path.join(L, 'original', 'jar'), '.jar'),
    "datasets": files(os.path.join(L, 'original', 'sml'), '.sml'),
    "harnessSource": dict(tree(os.path.join(H, 'src')), path="legacy/harness/src"),
    "harnessClasses": dict(tree(os.path.join(H, 'classes')), path="legacy/harness/classes"),
    "harnessScripts": {fn: sha(os.path.join(H, fn)) for fn in
                       ('build.sh', 'gm.sh', 'run.sh', 'summarize.py', 'provenance.py', 'references.tsv')},
    "java": jv.splitlines(),
    "parameters": {k: os.environ.get(k) for k in
                   ('STAGES', 'DATASETS', 'SEEDS', 'STEPS', 'TRAJ_PROFILES', 'EXTRA_TRAJ', 'EXTRA_SEEDS',
                    'FULL_PROFILES', 'VARIANT_PROFILES', 'MANUAL_FULL', 'ITER_CAP')},
}
if rec["parameters"]["DATASETS"]:
    rec["parameters"]["DATASETS"] = ' '.join(rec["parameters"]["DATASETS"].split())
print(json.dumps(rec, indent=1, sort_keys=False))
