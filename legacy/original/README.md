# SENECA — JCICS publication version

This repository is an **archival snapshot of the original SENECA source code** as it
existed around the JCICS publication, preserved for reference and citation.

SENECA is a program package for **Computer-Assisted Structure Elucidation (CASE)** of
organic molecules from spectroscopic (primarily NMR) data. It searches the space of
constitutional isomers for a given molecular formula using stochastic optimisation
(simulated annealing / genetic algorithm) and scores candidate structures against the
experimental data with a set of "judges".

> **Note:** This is a proof-of-concept implementation from the late 1990s / early 2000s
> and is **not actively maintained**. It is published here for historical and scientific
> reference. The original `seneca.sourceforge.net` site referenced in `Readme.txt` is
> long defunct.

## Citation

Steinbeck, C. *SENECA: A Platform-Independent, Distributed, and Parallel System for
Computer-Assisted Structure Elucidation in Organic Chemistry.* Journal of Chemical
Information and Computer Sciences **2001**, *41* (6), 1500–1507.
DOI: [10.1021/ci000407n](https://doi.org/10.1021/ci000407n)

## Contents

| Path | What it is |
|------|------------|
| `src/` | Java source (`seneca/…`, plus `jnt/` SciMark and policy files) |
| `jar/` | Third-party dependency JARs needed to build/run (CDK-era libs, JFreeChart, Log4j, JUnit, …) |
| `sml/` | SENECA `.sml` reference-compound datasets (eurabidiol, monochaetin, preconen, …) |
| `bin/` | Launch scripts (`Seneca.bat`, `seneca.sh`) |
| `build.xml`, `Makefile` | Ant / make build definitions |
| `LICENSE`, `License.txt`, `Readme.txt` | Original license and readme |

Compiled output (`build/`, `dist/`, `*.class`) is intentionally **not** tracked — build
from source with the included Ant build file and the JARs in `jar/`.

## License

© 1997–1999 Dr. Christoph Steinbeck. See [`LICENSE`](LICENSE) for the full terms
(an Artistic-License-style grant covering use, distribution, and modification).

## Related

- **`steinbeck/seneca`** — a later, runnable SENECA 2.1.4 rebuild (buildable on modern JDKs).
