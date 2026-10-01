#!/usr/bin/env python3
"""Writes golden/MANIFEST.jsonl: one deterministic line per dataset key summarising what was recorded.
Wall-clock times are deliberately excluded (they live in runs/timing.jsonl).

Structural features ("features") of a structure, computed from its connection table and the
dataset's elements (see golden/FORMAT.md, "Structural features"):
  O-O, N-O, N-N   bond between these heteroatoms
  cumulated       an atom with two double bonds (allene, ketene, cumulene)
  triple          a triple bond
  ring3, ring4    a bond whose smallest ring has 3 / 4 members
"""
import glob, gzip, json, os, sys
from collections import deque

out = sys.argv[1]


def lines(path):
    op = gzip.open if path.endswith('.gz') else open
    with op(path, 'rt') as f:
        return [json.loads(l) for l in f if l.strip()]


def features(ct, el):
    n = len(el)
    adj = [dict() for _ in range(n)]
    for i, j, o in ct:
        adj[i][j] = o
        adj[j][i] = o
    f = set()
    for i, j, o in ct:
        pair = '-'.join(sorted((el[i], el[j]), key=lambda s: {'N': 0, 'O': 1}.get(s, 2)))
        if pair in ('O-O', 'N-O', 'N-N'):
            f.add(pair)
        if o == 3:
            f.add('triple')
        # smallest ring through bond i-j: shortest i..j path without the bond, + 1
        dist = {i: 0}
        q = deque([i])
        while q:
            x = q.popleft()
            for y in adj[x]:
                if (x == i and y == j) or y in dist:
                    continue
                dist[y] = dist[x] + 1
                q.append(y)
        if j in dist and dist[j] + 1 in (3, 4):
            f.add('ring%d' % (dist[j] + 1))
    for a in range(n):
        if sum(1 for o in adj[a].values() if o == 2) >= 2:
            f.add('cumulated')
    order = ['O-O', 'N-O', 'N-N', 'cumulated', 'triple', 'ring3', 'ring4']
    return [x for x in order if x in f]


keys = sorted({os.path.basename(p)[:-len('.echo.jsonl')] for p in
               glob.glob(os.path.join(out, 'datasets', '*.echo.jsonl'))}, key=str.lower)
for k in keys:
    rec = {"key": k}
    echo = lines(os.path.join(out, 'datasets', k + '.echo.jsonl'))
    if echo[0]['t'] == 'unreadable':
        rec['readable'] = False
        rec['reason'] = echo[0]['reason']
        print(json.dumps(rec, separators=(',', ':')))
        continue
    rec['readable'] = True
    d = echo[0]
    rec['title'] = d['title']; rec['formula'] = d['formula']; rec['nAtoms'] = d['nAtoms']
    rec['shiftsRead'] = d['shiftsRead']
    el = None
    for r in echo:
        if r['t'] == 'recipe' and r['profile'] == 'gui':
            rec['autoAssign'] = r['autoAssign']; rec['scheduleAdded'] = r['scheduleAdded']
            rec['hOverrides'] = len(r['hOverrides'])
        if r['t'] == 'atoms' and r['stage'] == 'configured_gui':
            rec['hSum'] = r['hSum']
            rec['shifts'] = sum(1 for a in r['atoms'] if a['shift'] is not None)
            el = [a['el'] for a in r['atoms']]
        if r['t'] == 'generatorInput':
            rec[r['profile'] + 'Judges'] = r['enabled']
            rec[r['profile'] + 'MaxScore'] = r['maxScore']
    sp = os.path.join(out, 'scores', k + '.scores.jsonl')
    if os.path.exists(sp):
        sc = lines(sp)
        rec['scores'] = {"structures": sum(1 for r in sc if r['t'] == 'structure'),
                         "skipped": [r['reason'] for r in sc if r['t'] == 'skipped']}
    rp = os.path.join(out, 'references', k + '.ref.jsonl')
    if os.path.exists(rp):
        rr = lines(rp)
        h = rr[0]
        ref = {"defined": h['defined']}
        if h['defined']:
            ref.update(name=h['name'], status=h['status'], mappable=h['mappable'])
            if not h['mappable']:
                ref['classDifference'] = h['classDifference']
            ref['features'] = features(h['refCt'], [a['el'] for a in h['refAtoms']])
            ref['best'] = {r['profile']: {"total": r['total'], "maxScore": r['maxScore'], "atMax": r['atMax']}
                           for r in rr[1:]}
        rec['reference'] = ref
    tr = {}
    for p in sorted(glob.glob(os.path.join(out, 'trajectories', k + '.*.seed*.jsonl.gz'))):
        prof = os.path.basename(p)[len(k) + 1:].split('.seed')[0]
        s = p.split('.seed')[1].split('.')[0]
        L = lines(p)
        end = [r for r in L if r['t'] == 'end']
        sched = [r for r in L if r['t'] == 'schedule']
        e = {"reason": end[0]['reason'], "steps": end[0]['stepsRecorded']} if end else {"reason": "missing_end"}
        if sched:
            e['start_kT'] = sched[0]['start_kT']
        tr.setdefault(prof, {})[s] = e
    if tr:
        rec['trajectories'] = {p: dict(sorted(v.items(), key=lambda x: int(x[0]))) for p, v in tr.items()}
    runs = {}
    for p in sorted(glob.glob(os.path.join(out, 'runs', k + '.*.seed*.json'))):
        prof = os.path.basename(p)[len(k) + 1:].split('.seed')[0]
        s = p.split('.seed')[1].split('.')[0]
        r = lines(p)[0]
        e = {x: r.get(x) for x in ('status', 'termination', 'iterations', 'bestScore', 'maxScore', 'start_kT', 'bestHash')
             if x in r}
        if r.get('bestCt') is not None and el is not None:
            e['bestFeatures'] = features(r['bestCt'], el)
        if 'reference' in r and r['reference'].get('name') is not None:
            e['bestEqualsReference'] = r['reference']['bestEqualsReference']
        if 'crash' in r:
            e['crash'] = r['crash'].get('exception')
        runs.setdefault(prof, {})[s] = e
    if runs:
        rec['runs'] = runs
    print(json.dumps(rec, separators=(',', ':')))
