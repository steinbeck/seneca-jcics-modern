#!/usr/bin/env python3
"""Compare two class files by a compiler-independent bytecode fingerprint.

Per method, builds a multiset of: invoked methods and accessed fields
(name + descriptor, owner class stripped), constants, allocations, array
and arithmetic opcodes. Ignored as compiler noise: branch/goto layout,
local-variable load/store/iinc, stack shuffles, StringBuffer/StringBuilder
concatenation plumbing. Two classes compiled from the same source by javac
1.3 (2001) and a modern javac yield equal fingerprints except for a few
known codegen differences: constant folding of adjacent string literals,
try/finally inlining (javac 1.3 used jsr/ret), tableswitch offsets, and
public bridge methods javac adds for public methods inherited from a
package-private superclass. Class literals (javac 1.3: class$() helper) and
synthetic this$0 fields are normalised away.

usage: bytecode_fingerprint.py A.class B.class   (prints differences only)
"""
import collections
import re
import subprocess
import sys

SKIP_OPS = {'goto', 'goto_w', 'jsr', 'jsr_w', 'ret', 'nop', 'pop', 'pop2', 'dup', 'dup_x1', 'dup_x2', 'dup2', 'iinc', 'swap'}


def fingerprint(path):
    out = subprocess.run(['javap', '-c', '-p', path], capture_output=True, text=True).stdout
    methods = collections.OrderedDict()
    cur = None
    for line in out.splitlines():
        m = re.match(r'^  (\S.*);$', line)
        if m:
            cur = m.group(1)
            methods.setdefault(cur, collections.Counter())
            continue
        m = re.match(r'^\s+\d+: (\w+)\s*(.*)$', line)
        if not (m and cur):
            continue
        op, rest = m.group(1), m.group(2)
        if op.startswith('if') or op in SKIP_OPS:
            continue
        if re.match(r'^[adfil](load|store)(_\d)?$', op):
            continue
        c = re.search(r'// (.*)$', rest)
        ref = c.group(1) if c else ''
        if 'StringBuffer' in ref or 'StringBuilder' in ref:
            continue
        if op.startswith('invoke'):
            op = 'invoke'
        if op in ('bipush', 'sipush'):
            op, ref = 'const', rest.strip()
        elif re.match(r'^[ilfd]const_', op):
            op, ref = 'const', op.split('_')[-1].replace('m1', '-1')
        elif op.startswith('ldc'):
            op = 'const'
        ref = re.sub(r'^(String|int|long|double|float) ', '', ref)
        ref = re.sub(r'^(Method|Field|InterfaceMethod) (?:[\w/$]+\.)?', r'\1 ', ref)
        ref = ref.replace('InterfaceMethod ', 'Method ')
        if 'class$' in ref:  # javac 1.3 class-literal helper (class$ / class$x$y$Z)
            continue
        if op == 'const' and ref.startswith('class '):  # modern class literal -> dotted name
            ref = ref[6:].replace('/', '.')
        methods[cur][(op, ref)] += 1
    return methods


def synthetic(member):
    # compiler-generated members whose presence/modifiers differ between javac versions
    return 'this$0' in member or 'class$' in member


def main():
    fa, fb = fingerprint(sys.argv[1]), fingerprint(sys.argv[2])
    for k in sorted(set(fa) - set(fb)):
        if not synthetic(k):
            print('  only A:', k)
    for k in sorted(set(fb) - set(fa)):
        if not synthetic(k):
            print('  only B:', k)
    for k in sorted(set(fa) & set(fb)):
        if fa[k] != fb[k]:
            print('  DIFF', k)
            for x, n in (fa[k] - fb[k]).items():
                print('     A +%d %s' % (n, x))
            for x, n in (fb[k] - fa[k]).items():
                print('     B +%d %s' % (n, x))


if __name__ == '__main__':
    main()
