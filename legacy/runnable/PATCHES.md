# legacy/runnable — changes versus legacy/original

`legacy/runnable/src` is a verbatim copy of `legacy/original/src` with exactly the
four changes listed below. Third-party jars are not copied; `build.sh` and `run.sh`
use `../original/jar/*.jar` in place. Nothing under `legacy/original` was modified.

## Diff summary

```
$ git diff --no-index --stat legacy/original/src legacy/runnable/src
 .../null => runnable/src/seneca/structgen/Rng.java |  37 ++++
 .../structgen/SingleRandomStructureGenerator.java  | 217 +++++++++++++++++++++
 .../src/seneca/structgen/StochasticGenerator.java  |  12 +-
 .../seneca/structgen/StructureGeneratorStatus.java |   2 +-
 4 files changed, 261 insertions(+), 7 deletions(-)
```

| # | File | Kind | Why |
|---|------|------|-----|
| 1 | `seneca/structgen/SingleRandomStructureGenerator.java` | added (reconstructed) | Source missing from archive; required to compile |
| 2 | `seneca/structgen/StructureGeneratorStatus.java` | 1 line changed | Archived source lacks field `al` that the rest of the code uses |
| 3 | `seneca/structgen/Rng.java` | added (new class) | Determinism hook |
| 4 | `seneca/structgen/StochasticGenerator.java` | 6 lines changed | `Math.random()` -> `Rng.random()` |

New build files (not source changes): `build.sh`, `run.sh`, `.gitignore` (`classes/`),
`tools/bytecode_fingerprint.py`, `tools/compare-with-2001.sh`.

## Finding: the archived source does not compile as-is

Compiling `legacy/original/src` with any javac fails with exactly 5 errors, all caused by
source-tree drift, none by the JDK version:

```
StochasticGenerator.java:209/210  cannot find symbol: class SingleRandomStructureGenerator
StochasticGenerator.java:416/421  cannot find symbol: variable al (in StructureGeneratorStatus)
SGServerPanel.java:541            cannot find symbol: variable al (in StructureGeneratorStatus)
```

The 2001 jar (`legacy/dist-2001/seneca-client.jar`, built 2001-09-09 with JDK 1.3.0)
contains both: `seneca/structgen/SingleRandomStructureGenerator.class` and a
`StructureGeneratorStatus` with `public AnnealingLog al`. Patches 1 and 2 restore that state.

## Patch 1 — `SingleRandomStructureGenerator.java` (reconstructed)

- **What:** New source file, reconstructed from
  `seneca-client.jar!/seneca/structgen/SingleRandomStructureGenerator.class`
  (decompiled with CFR 0.152, transcribed statement by statement into the 2001 code style).
  A port note in the file header records the provenance.
- **Why:** `StochasticGenerator.run()` uses it to build the random starting structure
  (`dg.setAtomSet(atomSet); dg.attic = attic; atomSet = dg.generate();`).
- **Deviation from the 2001 bytecode:** the two `Math.random()` calls (bond order choice in
  `generate()`, start index in `getAnotherUnsaturatedNode()`) call `Rng.random()` instead
  (see patch 3). This class draws random numbers too — the generator's randomness is
  **not** confined to `StochasticGenerator`.
- **Why behaviour is unchanged:** same fields, methods, signatures, control flow and
  evaluation order as the 2001 class. Verified with `tools/compare-with-2001.sh`
  (per-method multiset of invoked methods, field accesses, constants, arithmetic): the only
  residual difference is that javac 1.3 emitted two `ireturn`s for three boolean helper
  methods (`nodeStatusOk`, `nodeStatusPerfect`, `endOfMatrix`; all three unused), which is
  code generation, not semantics.

## Patch 2 — `StructureGeneratorStatus.java`

```diff
-	public Vector statusObjects = new Vector();
+	public AnnealingLog al = null; // PORT: field as in the 2001 jar, see PATCHES.md
```

- **Why:** `StochasticGenerator.getStatus()` fills `sgs.al`; `SGServerPanel` reads it for
  the annealing chart.
- **Why behaviour is unchanged:** the 2001 class has exactly this field set (`javap` of
  the 2001 class: `datasetName, molecularFormula, iteration, bestStructure, status,
  bestEvaluation, al`; no `statusObjects`). `statusObjects` is referenced nowhere.
  After the patch the decompiled 2001 class and the rebuilt class are identical.

## Patch 3 — `Rng.java` (new)

- **What:** `public final class seneca.structgen.Rng` with
  `static synchronized void seed(long)` and `static synchronized double random()`, backed
  by one `java.util.Random`. The initial instance is `new Random()` (unseeded).
  `seed(s)` replaces it with `new Random(s)`.
- **Why:** reproducible runs for testing the port against the original.
- **Why behaviour is unchanged when never seeded:** `Math.random()` is implemented as
  `nextDouble()` on a lazily created, unseeded, JVM-wide `java.util.Random`. `Rng.random()`
  is `nextDouble()` on an unseeded, JVM-wide `java.util.Random` that is only shared by the
  generator: same distribution, same range [0,1), one draw per call.
- **Caveat:** determinism holds only if one generator runs at a time in a JVM (several
  concurrent generators — several "local servers" in the GUI — interleave their draws,
  exactly as they interleaved on `Math.random()` before).

## Patch 4 — `StochasticGenerator.java`

All six `Math.random()` calls replaced 1:1 by `Rng.random()`; no other change
(line endings CRLF preserved).

```
255:  rnd = Rng.random();                                   (Metropolis test, main loop)
450:  x1 = (int)(Rng.random() * nrOfAtoms);                 (mutate: pick 4 atoms)
451:  x2 = (int)(Rng.random() * nrOfAtoms);
452:  y1 = (int)(Rng.random() * nrOfAtoms);
453:  y2 = (int)(Rng.random() * nrOfAtoms);
475:  b11 = ((int)(Rng.random() * (max - min + 1))) + min;  (mutate: new bond order)
```

- **Why behaviour is unchanged:** same expression, same position, therefore the same number
  and order of draws; only the source of the double changes (see patch 3).

## Build level and RMI (no source patches needed)

- `build.sh` compiles with `javac --release 8` from JDK 25. Reason: `StochasticGenerator`
  calls the inherited static `Thread.yield()` unqualified as `yield()` (4 sites); javac
  rejects that for `--release` >= 14 ("invalid use of a restricted identifier 'yield'").
  Release 8 avoids touching the algorithm source. The class files (version 52) run on
  JDK 25 (and on the Oracle JDK 22 on PATH). With `yield()` -> `Thread.yield()` the code
  also compiles at `--release 25`; the only `[removal]` warnings then are
  `System.setSecurityManager`/`RMISecurityManager` in the `main()` methods of
  `StructureGeneratorServer` and `GateKeeper` (server entry points, dropped by the port).
- No class is excluded. The GUI's "Start local server" button instantiates
  `seneca.engine.StructureGeneratorServer` (a `UnicastRemoteObject`) in-process, and the
  GUI references `seneca.compute.Compute` and `seneca.gatekeeper.*`. These compile and work
  on JDK 25: exporting the object yields a dynamic proxy stub (`jdk.proxy1.$Proxy0`), so
  the rmic-generated `_Stub`/`_Skel` classes in the 2001 jar are not needed and `rmic`
  (removed from the JDK) is not run.
- Source encoding: `-encoding ISO-8859-1` (e.g. `SenecaDataset.java` contains Latin-1).
- Resources: `build.sh` copies all non-`.java` files from `src/` to `classes/`
  (like the 2001 ant target) except CVS backup files `.#*`.

## Equivalence check against the 2001 jar

`tools/compare-with-2001.sh` compares each class in the 2001 jar with the rebuilt class
by a compiler-independent bytecode fingerprint. Result: all classes in `seneca.structgen`,
`seneca.judges`, `seneca.engine`, `seneca.compute`, `seneca.gatekeeper` and almost all of
`seneca.core`/`seneca.client` are equal. Every remaining difference is code generation, not
semantics:

- constant folding of adjacent string literals (`CMLGenerator`, `SpecMLGenerator`,
  `SenecaParameters`, `SenecaDatasetFrame`, `jnt.scimark2.commandline`);
- `try/finally` inlined instead of `jsr/ret` (`Parameters`);
- tableswitch offsets and a static field read via an instance expression
  (`LUCYFileHandler`, `HybridizationJudgeConfigurator$HybridizationTableModel`);
- public bridge `autoconfigure()` that modern javac adds to `HHCOSYJudgeConfigurator` /
  `HMBCJudgeConfigurator` (public method inherited from a package-private superclass);
- `StructureGeneratorServer_Stub/_Skel` only in the 2001 jar (rmic output, see above);
- `Rng` only in the rebuild.

Limitation: the fingerprint strips owner classes, so `Math.random()` and `Rng.random()`
(same name and descriptor) look identical; patches 1 and 4 are verified by source diff.

Conclusion: apart from patches 1-4, the archived source is the source of the 2001 binary.

## Runtime checks (JDK 25.0.2, macOS, 2026-10-01)

| Check | Result |
|-------|--------|
| 2001 jar + original jars, `seneca.client.Seneca`, no args | Main frame "Seneca" 800x653, menus File/Edit/View/Calculate/Datasets/Help; no exception |
| 2001 jar, `pinene.sml` as argument | Dataset frame with all tabs and 7 judge configurators; no exception |
| Rebuilt `classes/`, same two cases | Identical to the 2001 jar; no exception |
| `./run.sh` | Starts, no output on stdout/stderr, killed after 8 s |
| `new StructureGeneratorServer("local0")` in-process | Works (export via dynamic proxy) |
| Generator run from the unmodified 2001 jar (pinene, in-process) | Runs; best scores 2400/2400, 2210/2400, 2305/2400 in 3 runs |
| Rebuilt, pinene, `Rng.seed(42)` twice | Byte-identical result lists (13 structures, best 2305/2400) |

GUI startup was verified with an in-process probe (starts `Seneca.main`, lists frames,
renders the frame to PNG), because screen capture was not available in the build environment. Only
expected console chatter appears (e.g. configurators printing themselves); no stack traces.
