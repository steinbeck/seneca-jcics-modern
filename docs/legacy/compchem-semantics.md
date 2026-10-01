# compchem / jmdraw: what SENECA relies on, and the CDK 2.13 mapping

Reference for the port of SENECA (JCICS 2001, 41, 1500) to modern Java + CDK. It pins down the exact
semantics of the binary-only pre-CDK library `compchem.jar` (and the display library `jmdraw.jar`) as far
as SENECA uses them, and states for each algorithm whether CDK reproduces it bit-for-bit.

## 0. Sources and method

| Item | Location |
|---|---|
| Decompiled compchem (Vineflower 1.11.1, reference only, never compiled into the port) | `legacy/decompiled/compchem/compchem/*.java` |
| Decompiled jmdraw | `legacy/decompiled/jmdraw/jmdraw/*.java` |
| Decompiled 2001 build `legacy/dist-2001/seneca-client.jar` (contains `SingleRandomStructureGenerator`, which is **missing from `legacy/original/src`**) | `legacy/decompiled/seneca-client-2001/` |
| Side-by-side harness: original `compchem.jar` bytecode vs CDK 2.13 | `docs/legacy/harness/LegacyVsCdk.java`, `run.sh` |

- compchem.jar classes are class-file version 45 (Java 1.1), built 2001-04-18. They load and run unchanged on JDK 25.
- Latest CDK release on Maven Central: **2.13** (`cdk-bundle` metadata, lastUpdated 2026-08-17). All CDK statements below
  refer to 2.13 source jars (`cdk-core`, `cdk-standard`, `cdk-extra`, `cdk-legacy`).
- Line numbers "Lnn" refer to the decompiled files.
- The decompiler output was cross-checked against `javap -c` where it looked odd (e.g. `Atom.setBondOrder`), and every
  score-relevant algorithm was executed from the original jar in the harness (section 14), so statements about behaviour
  do not depend on decompiler accuracy.

Verdict vocabulary: **CDK identical** (same numbers on SENECA's input domain, verified), **CDK differs (how)**,
**no CDK equivalent**.

## 1. Summary

| Legacy (compchem) | Used by SENECA in | Closest CDK 2.13 | Verdict | Port action |
|---|---|---|---|---|
| `Atom`, `Molecule` data model, `StandardElements` | everywhere | `IAtomContainer`, `IAtom.getImplicitHydrogenCount`, `IBond.Order` | CDK differs (valence table, adjacency order, `number`==index) | Keep a small internal graph (bond matrix + ordered adjacency + H counts); reproduce `StandardElements` table (sec. 3) |
| `HOSECodeGenerator.getHOSECode(atoms, atom, 1)` | `HOSECodeJudge` | `tools.HOSECodeGenerator` + `makeBremserCompliant()` | CDK identical (287,849 atoms, 0 mismatches) | Re-implement one-sphere code (20 lines, no canonical labelling per call); keep CDK as test oracle |
| `BremserOneSphereHOSECodePredictor` | `HOSECodeJudge` | `tools.BremserOneSphereHOSECodePredictor` (cdk-extra) | CDK identical for every key the generator can produce | Use CDK class or copy table; both fine |
| `SetOfRingsFinder.findSSSR` + `Ring`/`setOfRings` | `FeatureJudge` (ring-size scores) | `Cycles.sssr`, `ringsearch.FiguerasSSSRFinder` (legacy) | CDK differs (sec. 6; ring sizes differ for 3.0 % of random isomers vs `Cycles.sssr`, 0.2 % vs Figueras) | **Re-implement legacy algorithm verbatim**, incl. adjacency-order side effects; decide on its failure modes (sec. 6.3: the degree-2-branch exception ended 13 of 30 golden `gui` runs) |
| `DataStructureTools.computeMorganMatrix` | `SymmetryJudge`, `IsomorphismTester` | `graph.invariant.MorganNumbersTools.getMorganNumbers` | CDK identical **after `(int)` cast** (legacy wraps in 32-bit) | Use CDK + `(int)` cast, or re-implement with `int` |
| `IsomorphismTester.isIsomorph` | `StochasticGenerator.notInStock`, result list de-duplication | `isomorphism.IsomorphismTester` | CDK differs (inverted condition, see sec. 7) | Re-implement legacy test (heuristic, not a real isomorphism test) |
| `PathUtils.getPathLength` | `TwoDSpectrumJudge` (HMBC, HH-COSY) | `AllPairsShortestPaths` / `ShortestPaths.distanceTo` | CDK identical (with legacy cutoff/-1 convention) | Use CDK or BFS |
| `PathUtils.isConnected` | `StochasticGenerator` (every step) | `ConnectivityChecker.isConnected` | CDK identical | either |
| `ShortestPathFinder.computeFloydAPSP` | `SingleRandomStructureGenerator` (connectivity of start structure) | `PathTools.computeFloydAPSP(int[][])` | CDK identical (same code) | either |
| Wiener index (`WienerIndexJudge` own loop over `attic.apsp`) | not in the default judge list | `WienerNumbersDescriptor` | CDK identical number; but legacy judge reads a stale matrix (sec. 8) | port the judge as-is if at all |
| `MFAnalyser` | dataset setup, formula display, LUCY import | `MolecularFormulaManipulator` | CDK differs (order of atoms/elements, mass values) | Re-implement parser and formula string (trivial) |
| `StandardIsotopes`, `getMass` | mass display only | `Isotopes` | CDK differs in 4th-7th digit | display only; keep legacy table if exact display matters |
| `ChemFileUtils.writeMDLMol` / `readMDLMolFile` | result strings (output!), `notInStock`, file export/import | `MDLV2000Writer` / `MDLV2000Reader` | CDK differs (format); H counts identical on SENECA's structures | Re-implement writer byte-exactly; round trip can be dropped internally (sec. 10) |
| `ChemFileUtils.readLUCYStructure` | `StrucDispFrame` (display) | none | no CDK equivalent | port if LUCY result import is kept |
| `LIFOQueue(30)` | `StructureGeneratorResult` | none (trivial) | no CDK equivalent | re-implement |
| `DataStructureTools.faculty`, `cloneAtomSet`, `synchronizeBonds`, `createConnectionTable`, `removeIsomorphism`, `getDuration` | judges, generator, UI | none | no CDK equivalent | re-implement where needed |
| `jmdraw.JMDraw`, `RenderingEngine`, `RenderingAttributes` | structure display | `layout.StructureDiagramGenerator` + `depict.DepictionGenerator` | CDK differs (layout), display only | use CDK |
| `SMILESParser`, `RingSet`/`Path`/`getAllRings`, `normalize`/`isAromatic`, `Sorter`, `SetOfRingsFinder.makeRingList` path | **not used** by SENECA | - | - | do not port |

## 2. What SENECA calls (inventory)

All SENECA files use `import compchem.*;` / `import jmdraw.*;`, so usage was determined per class name and member.
Note: `legacy/original/src/seneca/core/SenecaDataset.java` is ISO-8859-1 with CRLF; plain `grep` under a UTF-8 locale
silently skips it (use `LC_ALL=C` or `iconv`).

| Class | Members used | Callers (score path in **bold**) |
|---|---|---|
| `Atom` | fields `symbol, number, name, degree, nodeTable[], bondTable[], HCount, maxBondOrder, maxBondOrderSum, charge, x/y/z`; `getBondCount()`, `isSaturated()`, `isBondedTo()`, `getCurrentMaxBondOrder()`, `formBond()`, `reset()`, `get/setPhysicalProperty()`, `clone()` | **judges**, **StochasticGenerator**, **SingleRandomStructureGenerator** (2001 jar), SpecML/CML I/O, client tables |
| `Molecule` | `Molecule(Atom[])`, `getAtoms()` | `StochasticGenerator.setMolecule`, `SGServerPanel`, `JMDrawPanel`, RMI (dropped) |
| `StandardElements` | `getMaxBondOrder`, `getMaxBondOrderSum` | `SenecaDataset.initializeNodes`, internally by `Atom`, `ChemFileUtils` |
| `HOSECodeGenerator` | `getHOSECode(Atom[], Atom, 1)` | **HOSECodeJudge** |
| `BremserOneSphereHOSECodePredictor` | `predict`, `getConfidenceLimit` | **HOSECodeJudge** |
| `SetOfRingsFinder`, `setOfRings`, `Ring` | `findSSSR(Atom[])`, `size()`, `elementAt()` | **FeatureJudge**, JMDraw |
| `DataStructureTools` | `computeMorganMatrix`, `cloneAtomSet`, `synchronizeBonds`, `createConnectionTable`, `synchronizeConnectionTable`, `cloneInt2D`, `faculty`, `removeIsomorphism`, `getMass` | **SymmetryJudge**, **StochasticGenerator**, StructureGenerator, StrucDispFrame, SenecaDatasetFrame, WienerIndexJudge |
| `IsomorphismTester` | `IsomorphismTester(Atom[])`, `isIsomorph(Atom[])`, `isIsomorph(Atom[],Atom[])` | **StochasticGenerator.notInStock**, StructureGeneratorResult, SGServerPanel |
| `PathUtils` | `getPathLength`, `isConnected` | **TwoDSpectrumJudge**, **StochasticGenerator** |
| `ShortestPathFinder` | `computeFloydAPSP(int[][])` | **SingleRandomStructureGenerator** (via `StructureGenerator.structureConnected`) |
| `ChemFileUtils` | `writeMDLMol`, `writeMDLMolFile`, `readMDLMolFile`, `readLUCYStructure` | **StochasticGenerator** (result strings, notInStock), StructureGeneratorResult, SGServerPanel, StrucDispFrame |
| `MFAnalyser` | `MFAnalyser(String)`, `MFAnalyser(Atom[])`, `getSetOfHeavyNodes`, `getMolecularFormula`, `analyseSetOfNodes`, `getAtomCount`, `getMass` | SenecaDataset, LUCYFileHandler, CarbonShiftAssigner, AtomPropertyPanel, StochasticGenerator (log/status) |
| `StandardIsotopes` | constructed in `SenecaDatasetFrame` (unused field), internally by `getMass`, HOSE rank fallback | display |
| `LIFOQueue` | `LIFOQueue(int)`, `push`, `pop`, `elementAt`, `size` | StructureGeneratorResult |
| `RingSet` | type of unused field `Attic.setOfAllRings` | - |
| `jmdraw.JMDraw` | `JMDraw()`, `JMDraw(Atom[])`, `atomSet`, `draw()`, `getAtomSet()`, `getRenderingAttributes()` | client display |
| `jmdraw.RenderingEngine` | `scaleIntoArea`, `translateAllPositive`, `center`, `drawMolecule` | `JMDrawPanel` |
| `jmdraw.RenderingAttributes` | `getBackColor()` | `JMDrawPanel` |

## 3. Data model: `Atom`, `Molecule`, `StandardElements`

### 3.1 Graph representation

A structure is an `Atom[]` (heavy atoms only; hydrogens are never explicit atoms in SENECA). Each atom stores its
neighbours itself:

- `nodeTable[0..degree-1]`: neighbour `Atom` references, `bondTable[k]`: bond order to `nodeTable[k]`.
  Fixed capacity 10 (`ArrayIndexOutOfBounds` beyond degree 10).
- Bond order codes: 1 single, 2 double, 3 triple, 4 aromatic, 5-8 single with stereo (MDL wedge/hash, direction
  dependent). The SENECA generator only ever produces 1-3.
- `number`: **must equal the array index**. HOSE codes (`visited[atom.number]`), `PathUtils` and ring perception
  index arrays by it. `SenecaDataset.initializeNodes()` sets `number = f`; `readMDLMolFile` sets it too.
- `Atom` does not override `equals`/`hashCode`: all `Vector.contains/indexOf/removeElement` and `equals` calls are
  identity comparisons.
- `Molecule` is a thin `Vector<Atom>` wrapper; `getAtoms()` returns the atoms in insertion order, no copy of atoms.
- `Bond` (package-private) and `Edge` exist only for ring perception internals.

**Adjacency order is part of the semantics.** `DataStructureTools.synchronizeBonds(atoms, bm)` (called for every
candidate structure) rebuilds all `nodeTable`s from the symmetric bond matrix:

```
for i in 0..n-1: atoms[i].degree = 0
for i in 0..n-2:
  for j in i+1..n-1:
    if bm[i][j] > 0:
      append (atoms[j], bm[i][j]) to atoms[i]; append (atoms[i], bm[i][j]) to atoms[j]
```

so every atom's neighbours are in **ascending atom index**. Ring perception (sec. 6) depends on this order. The port
must use exactly this order (CDK's `getConnectedAtomsList` order follows bond insertion order; do not rely on it).

`DataStructureTools.cloneAtomSet(atoms)`: `Atom.clone()` of each atom (copies scalar fields and the four arrays;
**does not copy `physicalProperties`**), then remaps `nodeTable` entries to the clones (identity lookup). Order preserved.

`createConnectionTable(atoms)`: `ct[i][index(nodeTable[k])] = bondTable[k]` (index by identity search).
`synchronizeConnectionTable(ct)`: copy upper triangle to lower. `cloneInt2D`: deep copy.

### 3.2 Valence table and hydrogen handling

`StandardElements` (the only valence model in SENECA):

| symbol | H | C | Si | N | P | O | S | F | Cl | Br | I | X |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| maxBondOrder | 1 | 3 | 3 | 3 | 3 | 2 | 2 | 1 | 1 | 1 | 1 | 3 |
| maxBondOrderSum | 1 | 4 | 4 | 3 | 3 | 2 | 2 | 1 | 1 | 1 | 1 | 10 |

Unknown symbol (e.g. `B`) returns -1 for both. Consequences: no hypervalent S/P, no charged N, boron unusable.
`X` is a wildcard heavy atom.

Hydrogens are a per-atom count `HCount` supplied by the dataset (DEPT/LUCY/SpecML; -1 = undefined). They are never
derived from valence during generation. Methods:

```
getBondCount():                       // float accumulation, truncated
  s = 0f; for k < degree: o = bondTable[k]; if o > 3: o = (o == 4) ? 1.5f : 1f; s += o
  return (int) s                      // e.g. 3 aromatic bonds -> 4.5 -> 4
isSaturated():  getBondCount() + HCount == maxBondOrderSum
saturate():     (fills maxBondOrder/Sum from StandardElements if either is 0)
                HCount = maxBondOrderSum - getBondCount()      // only used by the MDL/LUCY readers
getCurrentMaxBondOrder(): min(maxBondOrder, maxBondOrderSum - HCount - bondOrderSum)
formBond(other, o): if other already a neighbour: set its order, bondOrderSum = getBondCount()
                    else append (other, o); bondOrderSum += o
reset():        degree = 0; bondOrderSum = 0       // nodeTable entries are not cleared
```

`HybridizationJudge` uses `degree` and `getBondCount()` of carbons. `bondOrderSum` is only maintained by
`addBond/formBond/removeBond/reset` and is used by `SingleRandomStructureGenerator`.

Known legacy bugs, unused by SENECA: `setBondOrder`, `incrementBondOrder`, `decrementBondOrder` only examine
`nodeTable[0]` (bytecode returns inside the first loop iteration, confirmed with `javap -c`). `Molecule.removeAtom`
calls `removeElement` inside its loop.

CDK mapping: `IAtom.getImplicitHydrogenCount()` holds `HCount`; `IBond.Order.SINGLE/DOUBLE/TRIPLE` hold 1-3.
The port needs its own `StandardElements` table (CDK's valence models allow S(IV/VI), P(V), charged N, B) and its own
ordered adjacency arrays.

## 4. `HOSECodeGenerator`

SENECA calls only `getHOSECode(atomSet, root, maxSphere = 1)` (`HOSECodeJudge.evaluate`, every carbon, every step).

### 4.1 Constants

```
rankedSymbols  = C    O    N    S    P    Si   B    F    Cl   Br   ;    I    #    &    ,
symbolRankings = 9000 8900 8800 8700 8600 8500 8400 8300 8200 8100 8000 7900 1200 1100 1000
other symbols:   800000 - StandardIsotopes.getMajorIsotope(sym).atomicMass   (H -> 799999; unknown -> NPE)
bondRankings[order]  = {0:0, 1:0, 2:200000, 3:300000, 4:100000}   (orders > 4: no bond rank)
bondSymbols[order]   = {0:"", 1:"", 2:"=", 3:"%", 4:"*"}           (orders > 4: no symbol)
output symbol mapping: Si->Q, Cl->X, Br->Y, ","->"" ; others unchanged
sphereDelimiters = ( / / ) / / / / / / / /
```

### 4.2 Algorithm (general form)

TreeNode = (symbol, source atom, atom, bondType, long score).

```
getHOSECode(atoms, root, maxSphere):
  visited[*] = false; visited[root.number] = true; code = ""; sphere = 0
  nodes = [ TreeNode(nb.symbol, root, nb, root.bondTable[k], 0) for k < root.degree ]   // adjacency order
  mark all those nb visited
  nextSphere(nodes)
  for s in sphere..4: code += sphereDelimiters[s-1]            // fillUpSphereDelimiters, sphere already incremented
  strip all leading 'H' characters
  return code

nextSphere(nodes):
  sphere++
  if sphere > maxSphere: return
  code += sphereCode(nodes)            // sorts `nodes` in place
  next = []
  for t in nodes (sorted order):
    if t.symbol in "&;#:,": continue
    a = t.atom
    if a.degree == 1: next += TreeNode(",", source=a, atom=null, 0, t.score*1000000)
    else for k < a.degree:
      b = a.nodeTable[k]
      if !visited[b.number]: next += TreeNode(atoms[b.number].symbol, a, b, a.bondTable[k], t.score*1000000); visited[b.number] = true
      else if b != t.source: next += TreeNode("&", a, b, a.bondTable[k], t.score*1000000)
  nextSphere(next)

sphereCode(nodes):
  if empty: return ""                  // no delimiter appended in that case
  for t in nodes: t.score += rank(t.symbol) + (t.bondType <= 4 ? bondRankings[t.bondType] : 0)
  stable bubble sort by score, descending (only swaps on strictly greater)
  emit groups: group = nodes with equal `source`, groups in order of first occurrence in the sorted list,
               within a group in sorted order; each node emits bondSymbol + mappedSymbol; groups separated by ","
  append sphereDelimiters[sphere-1]
```

Notes on higher spheres (not used by SENECA, documented for completeness): child scores are `parent.score * 10^6 + rank`,
so scores overflow `long` from sphere 4 on (deterministic wrap). Terminal atoms produce an empty "," node that only
yields a group separator. Ties are broken by generation order, i.e. by adjacency order, so multi-sphere legacy codes are
not canonical.

### 4.3 What SENECA actually gets (maxSphere = 1)

```
code1(root) = concat over neighbours sorted by (bondRankings[o] + rank(symbol)) descending, ties in adjacency order,
              of bondSymbol(o) + mappedSymbol(symbol)
              + "(//)"
```

Ties have identical text, so the result is independent of adjacency order. Examples: `CH3-CH2-` carbon -> `C(//)`;
carbonyl carbon of a ketone -> `=OCC(//)`; nitrile carbon -> `%NC(//)`; CCl3 group carbon -> `CXXX(//)` (C ranks 9000, Cl 8200; this is also the Bremser key).
An isolated atom (degree 0) would give `"//)"` (unreachable: structures are connected).

### 4.4 CDK 2.13

`org.openscience.cdk.tools.HOSECodeGenerator.getHOSECode(mol, atom, 1)` returns `C-4;<sphere1>(//)`; 
`makeBremserCompliant()` strips the centre code up to `;`. Differences in general: centre code, charge codes after
atoms, aromatic flag -> bond type 4, explicit H skipped instead of stripped, an extra `ranking` term (sum of child
degrees, at most 12) and canonical pre-sorting, ring-closure rank handling. For one sphere, neutral atoms, bond orders
1-3 and SENECA's element set none of these change the string; the harness found **0 mismatches in 287,849 atoms**.

Verdict: **CDK identical** on SENECA's domain. Recommendation: re-implement `code1` directly (CDK runs
`CanonicalLabeler.canonLabel` on the whole molecule on every call, i.e. once per carbon per annealing step) and use the
CDK generator only as a test oracle.

## 5. `BremserOneSphereHOSECodePredictor`

- `Hashtable(700)` filled by 651 `put`s (Bremser, Magn. Reson. Chem. 1985, 23, 271). 648 distinct keys:
  three keys are put twice and the **second** value wins: `=CC$C(//)` -> 113.5 / 21.5, `=CC$(//)` -> 109.8 / 5.0,
  `PP(//)` -> 35.9 / 32.9 (shift / confidence limit).
- 256 keys contain `$`, which `compchem.HOSECodeGenerator` never emits; those rows are unreachable from SENECA. One
  key is corrupted in the jar: `C$C§CN(//)` (also unreachable).
- `predict(code)` -> `shift`; `getConfidenceLimit(code)` -> `confidenceLimit`; unknown code -> `Exception("No such HOSE code")`.

How `HOSECodeJudge` scores (for reference; judge code is SENECA, not compchem):

```
for f in atoms with symbol "C":
  code = code1(atoms[f])
  try: shift = predict(code); cl = getConfidenceLimit(code)
       dev = |shift - carbonShifts[f]|            // carbonShifts indexed by atom index f
       count++; sumDev += dev
       if dev < cl: scoreSum += 100
  catch: nothing                                   // unknown code scores 0 and is excluded from the mean
```

In the harness 0.8 % of carbon environments in random isomers have no table entry (e.g. `CNNN(//)`).

CDK 2.13 `org.openscience.cdk.tools.BremserOneSphereHOSECodePredictor` (cdk-extra) has 650 puts / 647 keys: the same
three duplicates with the same winners, identical values for all other keys, and the corrupted key removed.
Verdict: **CDK identical** for every reachable key (harness: 0 mismatches in 238,003 carbon predictions). CDK throws
`CDKException`, legacy `Exception`.

## 6. Ring perception: `SetOfRingsFinder.findSSSR`

Used by `FeatureJudge.judgeRingScore()` (called twice per evaluation; the first result is discarded):
`score = sum over rings r in findSSSR(atomSet) of ringSizeScores.get(r.size())` (default configuration penalises size
3 with -500 and size 4 with -300). Only the **multiset of ring sizes** is consumed, but the algorithm is order dependent
and not a minimum cycle basis, so the exact procedure has to be reproduced.

### 6.1 Algorithm (Figueras 1996 as implemented in compchem)

```
findSSSR(atoms):
  sssr = []                                   // setOfRings (Vector<Ring>)
  g = cloneAtomSet(atoms); g[i].number = i    // works on clones; input untouched
  trimmed = []                                // Vector, identity membership
  do
    minDeg = 7; smallest = null; n2 = []
    for i in 0..n-1:
      if g[i].degree == 0 and g[i] not in trimmed: trimmed += g[i]
      if g[i].degree == 2 and g[i] not in trimmed: n2 += g[i]
      if 0 < g[i].degree < minDeg: smallest = g[i]; minDeg = g[i].degree     // first atom of minimal degree
    if smallest == null: break
    if minDeg == 1:
      trim(smallest); trimmed += smallest
    else if minDeg == 2:
      broken = []
      for a in n2 (in order):
        r = findSRing(a)
        if r.size() > 0 and not sssr.ringAlreadyInSet(r): sssr += r; broken += a
      if broken empty: broken = [n2[0]]
      for a in broken: breakBond(a)           // removes a's LAST neighbour; `broken` is fixed before the loop,
                                              // so an entry can reach degree 0 before its turn -> crash (6.3)
    else if minDeg == 3:
      r = findSRing(smallest)
      if r.size() > 0 and not sssr.ringAlreadyInSet(r): sssr += r
      (u, v) = checkEdges(r)
      breakBond(u, v)
    // minDeg >= 4: nothing happens -> infinite loop (see 6.3)
  while trimmed.size() < n
  return sssr                                 // in discovery order, Ring atoms are clones (number = index)
```

```
findSRing(root):                              // BFS, smallest ring through root
  Q = FIFO; path[0..n-1] = empty lists        // FGsource[] is an instance field, rewritten before every read
  for k < root.degree:
    nb = root.nodeTable[k]
    if nb.degree > 0: FGsource[nb] = root; Q.push(nb); path[nb] = [nb, root]
  while Q not empty:
    node = Q.pop(); src = FGsource[node]
    for k < node.degree:
      m = node.nodeTable[k]
      if m.degree > 0 and m != src:
        if path[m] not empty:
          if |path[node] ∩ path[m]| == 1:     // intersection computed in path[node] order
            union = path[node] + [x in reverse(path[m]) if x not in path[node]]
            return Ring(union).sort()
        else:
          path[m] = [m] + path[node]; FGsource[m] = node; Q.push(m)
  return empty Ring
```

```
trim(a):  for each neighbour b of a: remove a from b.nodeTable (shift left), b.degree--;  a.degree = 0
breakBond(a):    b = a.nodeTable[a.degree-1]; a.degree--; remove a from b.nodeTable (shift left), b.degree--
                 // no degree check: a.degree == 0 reads nodeTable[-1] -> ArrayIndexOutOfBoundsException
breakBond(u,v):  remove v from u.nodeTable and u from v.nodeTable (shift left), degrees--
restoreBond(u,v): append v to u.nodeTable, u to v.nodeTable  (APPENDS at the end -> permutes adjacency order)

checkEdges(r):                                // choose the edge to break at a degree-3 node
  cand = []
  for i in 0..r.size()-1:
    (u, v) = r.getEdge(i)                     // (r[i], r[i+1]) for i < size-1, else (r[0], r[size-1])
    breakBond(u, v)
    r1 = findSRing(u); r2 = findSRing(v)      // r2 is computed but never used (legacy bug)
    cand += r1                                // both branches of the size comparison add r1
    restoreBond(u, v)
  i* = first index with minimal cand[i].size() (strict <, so ties -> lowest i; empty rings have size 0 and win)
  return r.getEdge(i*)
```

`Ring.sort()` normalises the cyclic order: rotate so the atom with the smallest `number` is first and continue in
the direction of its smaller-numbered ring neighbour (implementation: `shiftLeft`/`shiftRight` plus `invert()`, which
is a correct reversal). `ringAlreadyInSet` compares rings by the sorted list of atom numbers joined with `-`.
`Ring.getEdge(i)` depends on this normalised order, and `checkEdges` depends on `getEdge`, so `Ring.sort()` must be
reproduced exactly.

### 6.2 Properties

- Result depends on adjacency order (`nodeTable`), on the order of `n2`, and on the permutation done by
  `restoreBond`. Port on an int-array graph with neighbours in ascending index (sec. 3.1).
- Not a minimum cycle basis: it can return more rings than the cyclomatic number
  (bicyclo[2.2.2]octa-2,5-diene `C1=CC2C=CC1C=C2`: legacy [6,6,6], cycle rank 2) and different sizes
  (`C12C3C1C3C4C5C2C54`: legacy [3,3,3,4,6], CDK [3,3,3,3,6]).

### 6.3 Failure modes (reproduce or decide explicitly)

Three ways the legacy finder fails. Only the first one occurs in practice.

1. **Exception in the degree-2 branch (the one that happens).** `breakBond(Atom)` does not check the
   degree. The list `broken` is fixed before the cutting loop starts, and each `breakBond(a)` also lowers the
   degree of a's last neighbour. If an entry x of `broken` has already lost all its bonds to earlier entries,
   `breakBond(x)` reads `nodeTable[-1]`:
   `java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 10`
   (`compchem.SetOfRingsFinder.breakBond`, source line 302).
   Pattern in all 13 recorded cases: three adjacent degree-2 atoms u-x-v, with u and v before x in `n2`
   (lower index). Since neighbour lists are ascending, x is the last neighbour of both u and v.
   `breakBond(u)` and `breakBond(v)` remove both of x's bonds, then `breakBond(x)` throws. All three atoms
   must have added a ring that was not yet in the set. That happens when the chain lies on several smallest
   rings of the same size: the BFS in `findSRing` then returns a different ring depending on the start atom.
   Example (bn-ye1 seed 3): rings of size 10 from atoms 27, 28, 30 and 31. `breakBond(27)` and
   `breakBond(28)` each cut their bond to 31, and `breakBond(31)` then throws.
   The trigger is larger polycyclic candidates, which annealing produces and a random walk from small seed
   molecules rarely does (section 14: 0 in 24,277 random-walk isomers).
   **Effect on a run:** the exception leaves `FeatureJudge.evaluate` (`judgeRingScore`, called twice per
   evaluation) and reaches the outer `catch (Exception e)` of `StochasticGenerator.run()`. That catch logs
   "Error in StoicGent.run()" and the run ends. `running` stays true and the GUI shows no error.
   In the golden data this ended 13 of 30 `gui` runs (FeatureJudge enabled) and one trajectory
   (`golden/FORMAT.md`, finding 1). Re-scoring each recorded crash candidate (`crash.candidateCt`) with the
   original jar throws this exception every time (harness, section 14).
2. **Exception in the degree-3 branch.** If `findSRing` returns an empty ring, `checkEdges` reads
   `cand.elementAt(0)` of an empty vector (`ArrayIndexOutOfBoundsException: 0 >= 0`). The effect is the same
   as in 1. Not observed: not in the harness, not in the golden runs.
3. **Hang.** If every remaining atom with degree > 0 has degree >= 4, nothing is removed and the
   `do/while` never terminates (e.g. a ring core made only of quaternary carbons). Not observed, but
   reachable for H-poor formulas. In 2001 this froze the generator thread.

The port must decide, and document, what to do in each case. A 1:1 port reproduces 1 and 2: it ends the
run at the same candidate, which is what the golden `gui` runs record. A port that does not crash will
diverge from those runs at the recorded crash iteration. A hang cannot sensibly be ported. Suggestion:
detect the min-degree >= 4 condition and treat it like an exception.

### 6.4 CDK 2.13

- `Cycles.sssr(mol)` (minimum cycle basis): different ring-size multiset in 726 of 24,277 random isomers (3.0 %),
  different number of 3- or 4-rings (what the default FeatureJudge penalises) in 29 (0.12 %).
- `org.openscience.cdk.ringsearch.FiguerasSSSRFinder` (cdk-legacy, derived from the same code but changed:
  breaks the first bond instead of the last neighbour, `checkEdges` keeps the larger of r1/r2, edge order from
  `ring.bonds()`): different ring sizes in 56 of 24,277 isomers (0.2 %). It does not throw on the 13
  golden crash candidates (it has no `breakBond(Atom)`).

Verdict: **CDK differs**. Re-implement 6.1 verbatim and test it against `compchem.jar` (harness pattern).

## 7. Morgan numbers, `IsomorphismTester`, symmetry

### 7.1 `DataStructureTools.computeMorganMatrix(atoms)`

```
cur[i] = atoms[i].degree                      // heavy-atom degree; element, H count, bond order ignored
repeat n times (n = atoms.length):
  nxt[i] = sum over neighbours j of cur[j]    // int arithmetic, wraps on overflow
  cur = nxt
return cur                                    // int[]
```

An unused `Hashtable` is allocated. No normalisation, so values overflow 32 bits for larger molecules (harness: 862 of
24,277 structures, all ~30-atom triterpenes; the datasets go up to 35 heavy atoms). Overflow is defined behaviour in
Java and identical on every JVM.

CDK `MorganNumbersTools.getMorganNumbers(mol)`: same recurrence (initial value = number of non-H neighbours, `order`
iterations) in `long`. Because the recurrence is a sum, `(int) cdk[i] == legacy[i]` exactly (verified: 0 mismatches in
24,277 structures); without the cast 862 differ. Requires no explicit H atoms and the same atom order.
Verdict: **CDK identical after `(int)` cast**.

`SymmetryJudge.evaluate` (SENECA): for each user symmetry class, for each pair (g < h) of member atoms,
`if mm[g] == mm[h]: scoreSum += score (500)`. `calcMaxScore` = sum over classes of `faculty(k)/2` (int factorial,
overflows for k >= 13; note this is k!/2, not 500 * k(k-1)/2, so maxScore and achieved score are on different scales;
this is SENECA judge logic and must be kept 1:1).

### 7.2 `IsomorphismTester`

```
isIsomorph(base, cmp):
  mb = Morgan(base); mc = Morgan(cmp)
  if sort(mb) != sort(mc) elementwise: return false        // assumes equal length
  for f in base indices:
    found = false
    for g in cmp indices:
      if mb[f] == mc[g]:
        if base[f].symbol != cmp[g].symbol or base[f].HCount != cmp[g].HCount: return false
        found = true
    if not found: return false
  return true
```

This is a heuristic, not an isomorphism test. Bond orders enter only through `HCount`. Harness, over 720,870 pairs
from the last 30 accepted structures of each random walk:

- false positives (non-identical molecules declared isomorphic, e.g. cubane-type cage isomers): 2,580,
- false negatives (same molecule, different numbering declared different, whenever two atoms with different
  element/H count share a Morgan value): 976.

CDK `org.openscience.cdk.isomorphism.IsomorphismTester` uses the same scheme with `long` Morgan numbers but the
element/H test is written `!(Z1.equals(Z2)) && Objects.equals(H1, H2)` (returns false only if elements differ AND H
counts are equal): 4,838 disagreements with legacy. Verdict: **CDK differs**; re-implement the 10 lines above.

Where it matters: `StochasticGenerator.notInStock(latestResult)` builds the tester on the new best structure and
compares it with each of the (at most 30, LIFO) stored result strings re-read via `readMDLMolFile`. A false positive
suppresses a genuinely new best structure; a false negative stores a duplicate. Either changes the result list and
`bestScore` updates, so the heuristic is part of the observable behaviour.

## 8. Paths, connectivity, Wiener index

`PathUtils.getPathLength(atoms, a, b, cutoff)`: level-by-level BFS from `a` over `nodeTable` (visited by `number`);
returns the sphere number (= topological distance) at which `b` is first reached, or -1 if not reached within `cutoff`
spheres. `a == b` returns -1. Used by `TwoDSpectrumJudge` with `cutoff` = 1 + highest index with a positive score
(HMBC default scores {100,100,5} -> cutoff 3; HH-COSY {100} -> cutoff 1): `if plength > 0: scoreSum += scores[plength-1]`.
CDK: `new AllPairsShortestPaths(mol).from(i).distanceTo(j)`, then map `i==j or d > cutoff` to -1.
Verdict: **CDK identical** (0 mismatches, all pairs, cutoffs 1-3).

`PathUtils.isConnected(atoms)`: BFS from `atoms[0]`, true if all atoms visited. CDK `ConnectivityChecker.isConnected`:
**identical** (32,000 cases). Called for every mutated structure; disconnected ones are discarded without scoring
and without consuming further random numbers.

`ShortestPathFinder.computeFloydAPSP(ct)`: Floyd-Warshall on `ct[i][j] == 0 ? 999999999 : 1`, diagonal 0, loop order
k, i, j. CDK `PathTools.computeFloydAPSP(int[][])` is the same code. **Identical**. Only use:
`StructureGenerator.structureConnected` (any `apsp[i][j] > n` -> disconnected) for the start structure.

Wiener index: `WienerIndexJudge` sums `attic.apsp[f][g]` over f < g. Equal to CDK `WienerNumbersDescriptor` path number
for the same matrix. However, `attic.apsp` is computed only once, in `SingleRandomStructureGenerator.generate()`, and the
recomputation in `StochasticGenerator` is commented out, so the judge always sees the start structure. The judge is not
in `SenecaDataset.init()`'s judge list and has no configurator; it is effectively dead code.

## 9. `MFAnalyser`, `StandardIsotopes`

`MFAnalyser(String mf)` parser:

```
for each char c (index i):
  'A'..'Z': symbol = c; count = "0"
  'a'..'z': symbol += c
  '0'..'9': count += c
  if i is last or next char is upper case: repeat max(1, int(count)) times: atoms += new Atom(symbol)
```

No parentheses, charges or isotopes. Atoms appear in formula order (`C10H16O` -> 10 C, 16 H, 1 O).
`getSetOfHeavyNodes()` drops `"H"` atoms keeping order: **this order defines the atom numbering of every SENECA dataset
created from a formula** (all C first, then the other elements as written).

`MFAnalyser(Atom[])` / `analyseSetOfNodes()`: element symbols in order of first appearance with counts (count 1
omitted); total H = sum of positive `HCount`s, inserted after the first element:
`C10H16O`, but `C19H30O3N2` if O atoms precede N atoms in the node list. CDK Hill order would give `C19H30N2O3`.
`getAtomCount(sym)`: for "H" returns the summed `HCount` if > 0 (Atom[] constructor only), else counts atoms.
`SenecaDataset.analyseSetOfNodes()` is a separate copy that also adds negative (undefined) `HCount`s.

`getMass()`: float sum of `exactMass` of the major isotope (first table entry with abundance 100) from
`StandardIsotopes` (2001 values); returns 0 if an element is unknown. Differs from CDK
`MolecularFormulaManipulator.getMass(f, MonoIsotopic)` in the 4th-7th significant digit (e.g. C10H16O: 152.12016 vs
152.120115). Display only (dataset table, LUCY import). Verdict: **CDK differs**; trivial to re-implement, keep the
legacy isotope table only if displayed masses must match the 2001 program.

## 10. `ChemFileUtils` (MDL molfile)

SENECA's result list consists of molfile **strings** written by `writeMDLMol`, so this format is program output.

`writeMDLMol(writer, title, atoms)` writes exactly:

```
<title>\n
\n
\n
%3d(n)%3d(nBonds)  0  0  0  0  0  0  0  0  1 V2000\n
for each atom:  F(x)F(y)F(z) + " " + symbol padded right to 3 + " 0  0  0  0  0  0  0  0  0  0  0  0\n"
                F(v) = NumberFormat(Locale.US, 4 fraction digits, max 4 integer digits, no grouping), left-padded to width 10
for i < j with ct[i][j] > 0 (row-major):  %3d(i+1)%3d(j+1)%3d(order)%3d(stereo)  0  0  0\n
                orders 5/6 -> order 1 stereo 1 (6 swaps i/j); 7/8 -> order 1 stereo 6 (8 swaps)
M  END\n
```

`%3d` is `NumberFormat.getNumberInstance()` in the **default locale**, at most 3 integer digits (higher digits are
silently dropped), left-padded with spaces. SENECA titles are `"Score: <score>"`. Coordinates are 0 for generated
structures. No charges, no `M  CHG`, no `$$$$` (the generator log appends `$$$$` itself).
CDK `MDLV2000Writer` writes a program/timestamp line, charges, different spacing: **CDK differs**; re-implement the
writer byte for byte if the result strings are to remain identical.

`readMDLMolFile(BufferedReader)`: skips 3 header lines; counts line parsed after inserting a space at position 3;
atom lines tokenised (x, y, z, symbol); bond lines split at fixed columns 3/6/9/12; stereo 0 -> order as given, stereo 1
-> 5 (a->b) / 6 (b->a), stereo 6 -> 7 / 8, other stereo values reuse the previous bond's codes (bug); `M  CHG` parsed into
`charge` and echoed to stdout; stops at `M  END`. Then `number = index`, adjacency rebuilt from the matrix in ascending
index, `maxBondOrderSum` from `StandardElements`, `saturate()` -> `HCount = maxBondOrderSum - getBondCount()`.
Exceptions are caught and printed; a partially read structure is returned.

On SENECA's own output the round trip is lossless for everything `IsomorphismTester` looks at: every generated
structure is saturated (`getBondCount + HCount == maxBondOrderSum` for all atoms), so the recomputed `HCount` equals
the dataset `HCount` (harness: 0 mismatches for both the legacy reader and CDK `MDLV2000Reader`). The port may
therefore keep stored results as (bond matrix, H counts) and compare directly, provided it still emits the identical
molfile strings for display/output.

`readLUCYStructure(file)`: LUCY result file (title, "nAtoms nBonds", atom lines with symbol and H count in tokens 2
and 5, bond lines "a b order"); bonds then promoted greedily (`for degree 1..3, for atoms in index order, raise the
first neighbour bond whose both ends are unsaturated by one`) to fill valences. Display only. **No CDK equivalent.**

## 11. Small helpers

- `LIFOQueue(size)`: `push` inserts at index 0 and truncates to `size` (oldest dropped); `pop` removes index 0.
  `StructureGeneratorResult` keeps two parallel queues of 30 (molfile strings, `ScoreSummary`s), newest first.
- `DataStructureTools.faculty(n)`: int factorial, overflows for n >= 13.
- `DataStructureTools.removeIsomorphism(Vector<Atom[]>)` (StrucDispFrame) and
  `StructureGeneratorResult.removeIsomorphism()` (SGServerPanel, compares only entries with equal score) use the
  tester of 7.2. UI-level de-duplication.
- `DataStructureTools.getMass(mf)` = `MFAnalyser(mf)` atoms summed like `MFAnalyser.getMass`.
- Not used by SENECA: `SMILESParser`, `Sorter`, `Path`, `RingSet`, `SetOfRingsFinder.getAllRings`,
  `DataStructureTools.normalize/isAromatic/saturate/analyseSetOfNodes`, `HOSECodeGenerator.makeRingList`,
  `setOfRings.sort/makeNodeRingLists` (JMDraw only).

## 12. jmdraw

`JMDraw` computes 2D coordinates from the constitution (uses `SetOfRingsFinder.findSSSR`, ring templates, substituent
angle optimisation); `RenderingEngine` draws bonds/symbols with AWT; `RenderingAttributes` holds colours/fonts/flags.
Display only, no influence on scores. CDK `StructureDiagramGenerator` + `DepictionGenerator` (or the AWT renderer) is the
replacement: **CDK differs** (different but better layouts); not a 1:1 requirement.

## 13. Hash and iteration order, other JVM dependence

Score path:

| Place | Collection | Order-sensitive? |
|---|---|---|
| `BremserOneSphereHOSECodePredictor.ht` | `Hashtable<String,…>` | lookup only; duplicate keys resolved by `put` order (last wins), same in CDK |
| `Atom.physicalProperties` | `Hashtable` | lookup only (iteration only in `toString`); not copied by `clone()` |
| `FeatureJudge.ringSizeScores` | `Hashtable<Integer,Integer>` | `calcMaxScore` sums `elements()` (order-free); `FeatureJudgeConfigurator` lists `keys()` (display order only; Integer hash = value, deterministic) |
| `computeMorganMatrix` | unused `Hashtable` | none |
| all `Vector`s (`setOfRings`, `Ring`, BFS queues, `judges`, results) | `Vector` | deterministic insertion order |

No `HashMap`/`HashSet` and no identity-hash-ordered iteration anywhere on the score path; `Atom` equality is identity
but only used for membership tests. **Nothing in compchem makes scores depend on hash iteration order or JVM version.**

Remaining non-determinism / JVM dependence (relevant for the port as a whole):

- `Math.random()`: one JVM-global generator. Besides `StochasticGenerator` (L255, L450-475), the start structure comes
  from `SingleRandomStructureGenerator.generate()` (2001 jar only), which calls `Math.random()` in
  `getAnotherUnsaturatedNode` (random start index) and for the bond order `max(1, (int)(random * maxOrder))`.
  In-process generators sharing `Math.random()` interleave their streams; give each run its own seeded
  `java.util.Random` (its LCG and `nextDouble()` are specified by the Java SE API, so a fixed seed reproduces on any JVM).
- `Math.exp`/`Math.log` (annealing acceptance and start temperature) may differ by 1 ulp between JVMs/CPUs (intrinsics);
  use `StrictMath` for bitwise reproducibility.
- Integer overflow (Morgan `int`, HOSE `long` scores beyond sphere 3, `faculty`) is defined by the JLS and identical on
  all JVMs; the port must keep the same widths.
- `formatMDLInt` uses the default locale; with CLDR locale data (default since JDK 9) some locales format with
  non-ASCII digits, which `readMDLMolFile` cannot parse. Use `Locale.ROOT`/`Locale.US`.
- Ring perception and `synchronizeBonds` depend on atom index order only (deterministic), see 3.1 and 6.

## 14. Harness results

`docs/legacy/harness/run.sh 2000`: 16 seed molecules (camphor, alpha-pinene, menthol, cubane, adamantane, nicotine,
caffeine, a lanostane-type C30 triterpenoid, S/Cl/Br, Si, P, I, F, N=O, coumarin-type and polyacetylene examples),
2,000 Faulon mutations each (verbatim copy of `StochasticGenerator.mutate`, seeded `java.util.Random(20011500)`,
every move accepted), 24,277 connected isomers. Legacy = original `compchem.jar`, CDK = 2.13.
In addition, `run.sh` extracts the 13 structures on which the original generator crashed in the golden runs
(`golden/runs/*.json`, `crash.candidateCt`, atoms from `golden/datasets/<key>.echo.jsonl` stage `configured_gui`) and
runs the legacy ring finder on them. A subclass of `SetOfRingsFinder` names the failure mode; it only observes and
then calls the original method. Run time about 20 s.

| Check | checked | mismatches |
|---|---:|---:|
| HOSE one-sphere code, all atoms (legacy vs CDK + `makeBremserCompliant`) | 287,849 | 0 |
| Bremser shift and confidence limit, carbons | 238,003 | 0 |
| (carbon codes without table entry) | 238,003 | 1,954 |
| Morgan legacy vs `(int)` CDK | 24,277 | 0 |
| Morgan legacy vs CDK `long` | 24,277 | 862 |
| Morgan pairwise equality (SymmetryJudge relation) | 24,277 | 0 |
| `PathUtils.getPathLength`, all pairs, cutoff 1-3 | 24,277 | 0 |
| Floyd APSP legacy vs CDK `PathTools` vs `AllPairsShortestPaths` | 24,277 | 0 |
| `isConnected` vs `ConnectivityChecker` (incl. disconnected) | 32,000 | 0 |
| SSSR ring-size multiset vs `Cycles.sssr` | 24,277 | 726 |
| SSSR ring count vs `Cycles.sssr` | 24,277 | 381 |
| SSSR number of 3- and 4-rings vs `Cycles.sssr` | 24,277 | 29 |
| SSSR ring sizes vs CDK `FiguerasSSSRFinder` | 24,277 | 56 |
| legacy `findSSSR` hangs / exceptions on the random-walk isomers | 24,277 | 0 / 0 |
| golden crash candidates (`golden/runs/*.json`, `crash.candidateCt`): legacy `findSSSR` throws | 13 | 13 of 13 throw |
| ... of which via degree-0 `breakBond(Atom)` (failure mode 1 in 6.3) | 13 | 13 |
| `IsomorphismTester` legacy vs CDK `IsomorphismTester` | 720,870 | 4,838 |
| `IsomorphismTester` false positives (vs aromatic canonical SMILES) | 720,870 | 2,580 |
| `IsomorphismTester` false negatives (vs Kekulé canonical SMILES) | 720,870 | 976 |
| MDL round trip H counts, legacy reader / CDK reader | 24,277 | 0 / 0 |
| `MFAnalyser` element counts (16 formulas) | 16 | 0 |
| `MFAnalyser.getMass` vs CDK monoisotopic mass | 16 | 14 |

The same harness is the template for the port's regression tests: run the port's re-implementations against
`compchem.jar` on identical inputs and require 0 mismatches.

## Appendix: `SingleRandomStructureGenerator` (missing from the source tree)

`StochasticGenerator.run()` (src L209-214) instantiates `seneca.structgen.SingleRandomStructureGenerator`, whose source
is not in `legacy/original/src`; it exists only in `legacy/dist-2001/seneca-client.jar` (decompiled in
`legacy/decompiled/seneca-client-2001/seneca/structgen/`). It produces the start structure:

```
generate():
  reset() all atoms
  loop:
    repeat
      changed = false
      for i in 0..n-1:
        if not atoms[i].isSaturated():
          j = getAnotherUnsaturatedNode(i)
          if j != -1:
            o = max(1, (int)(Math.random() * min(atoms[i].getCurrentMaxBondOrder(), atoms[j].getCurrentMaxBondOrder())))
            atoms[i].formBond(atoms[j], o); atoms[j].formBond(atoms[i], o); changed = true
    until not changed
    attic.atomSet = atomSet; bondMatrix = createConnectionTable(atomSet); attic.apsp = Floyd(bondMatrix)
    if no unsaturated atom and structureConnected(): return atomSet
    reset() all atoms

getAnotherUnsaturatedNode(i):
  s = (int)(Math.random() * n)
  first j in s..n-1, then 0..s-1, with not saturated, j != i, not bonded to i; else -1
```

Note `(int)(random * k)` is at most k-1, so with `max(1, ·)` a bond order of k is only chosen when k = 1 (start
structures are biased to low bond orders). Because `StochasticGenerator` sets `dg.attic = attic` before `generate()`,
`attic.atomSet` and the generator's `atomSet` become the same array afterwards.
