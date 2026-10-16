# legacy/decompiled

Java sources produced by machine from class files with the decompiler Vineflower 1.11.1. They are not the
original source code: names of local variables, comments, and formatting are the decompiler's.

| Directory | Decompiled from |
|---|---|
| `compchem/` | `legacy/original/jar/compchem.jar`, the pre-CDK chemistry library used by SENECA in 2001 |
| `jmdraw/` | `legacy/original/jar/jmdraw.jar`, the structure drawing library used by the 2001 client |
| `seneca-client-2001/` | `legacy/dist-2001/seneca-client.jar`, the SENECA client as built in 2001 |

`compchem.jar` and `jmdraw.jar` exist only as class files; the source of the 2001 client is in
`legacy/original/src`. The decompilation is a reference for the port (`docs/legacy/compchem-semantics.md`)
and is never compiled into it.

Christoph Steinbeck wrote both libraries, as he wrote SENECA itself. In 2001 they were separate projects:
CompChem at `compchem.sourceforge.net`, and JMDraw (automatic 2D layout of structure diagrams) at
`sourceforge.net/projects/jmdraw`, where it was distributed under the GNU General Public License. Neither site
still exists.
