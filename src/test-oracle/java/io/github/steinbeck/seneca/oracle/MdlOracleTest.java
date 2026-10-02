package io.github.steinbeck.seneca.oracle;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import compchem.Atom;
import compchem.ChemFileUtils;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.LegacyMdlWriter;
import io.github.steinbeck.seneca.chem.LucyStructureReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MDL reader/writer and LUCY structure reader against compchem {@code ChemFileUtils} on hand-made inputs
 * that exercise every branch: stereo codes 1/6 and the "reuse the previous codes" bug for other stereo
 * values, aromatic bonds, charges ({@code M  CHG}), explicit hydrogens, coordinates (rounding, 4+ integer
 * digits), long symbols, truncated files (the partial structure is returned), a broken counts line, and
 * write-after-read (stereo codes 5-8 written back with swapped atoms).
 */
@Tag("A")
class MdlOracleTest {

    /** Everything a reader sets, as one comparable string. */
    static String describe(Atom[] a) {
        if (a == null) return "null";
        StringBuilder sb = new StringBuilder("n=" + a.length);
        for (Atom x : a) {
            if (x == null) {
                sb.append(" | null");
                continue;
            }
            sb.append(" | ").append(x.number).append(' ').append(x.symbol).append(" H").append(x.HCount)
                    .append(" mbo").append(x.maxBondOrder).append(" mbos").append(x.maxBondOrderSum)
                    .append(" bos").append(x.bondOrderSum).append(" q").append(x.charge)
                    .append(" xyz").append(x.x).append(',').append(x.y).append(',').append(x.z).append(" nb");
            for (int k = 0; k < x.degree; k++) sb.append(' ').append(index(a, x.nodeTable[k])).append(':').append(x.bondTable[k]);
        }
        return sb.toString();
    }

    static String describe(HeavyAtom[] a) {
        if (a == null) return "null";
        StringBuilder sb = new StringBuilder("n=" + a.length);
        for (HeavyAtom x : a) {
            if (x == null) {
                sb.append(" | null");
                continue;
            }
            sb.append(" | ").append(x.number).append(' ').append(x.symbol).append(" H").append(x.hCount)
                    .append(" mbo").append(x.maxBondOrder).append(" mbos").append(x.maxBondOrderSum)
                    .append(" bos").append(x.bondOrderSum).append(" q").append(x.charge)
                    .append(" xyz").append(x.x).append(',').append(x.y).append(',').append(x.z).append(" nb");
            for (int k = 0; k < x.degree; k++) sb.append(' ').append(index(a, x.nodeTable[k])).append(':').append(x.bondTable[k]);
        }
        return sb.toString();
    }

    static int index(Object[] a, Object x) {
        for (int i = 0; i < a.length; i++) if (a[i] == x) return i;
        return -1;
    }

    static final String HEAD = "title\n  prog\n\n";

    static final String[] MOLFILES = {
        // ethanol with explicit H on O
        HEAD + "  4  3  0  0  0  0  0  0  0  0  1 V2000\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    1.2990    0.7500    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    2.5981    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    3.5000    0.5000    0.0000 H   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "  1  2  1  0  0  0  0\n  2  3  1  0  0  0  0\n  3  4  1  0  0  0  0\nM  END\n",
        // stereo codes 1, 6, and 4 (reuses the previous codes), aromatic bond, charge
        HEAD + "  6  5  0  0  0  0  0  0  0  0  1 V2000\n"
            + "   -1.2345 1234.5678    0.0001 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 N   0  3  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 Cl  0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 Br  0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "  1  2  1  1  0  0  0\n  2  3  1  6  0  0  0\n  3  4  4  0  0  0  0\n  4  5  1  4  0  0  0\n  1  6  1  0  0  0  0\n"
            + "M  CHG  2   3   1   5  -1\nM  END\n",
        // stereo code 4 on the first bond: previous codes are 0 = no bond
        HEAD + "  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "  1  2  2  4  0  0  0\n  2  3  2  0  0  0  0\nM  END\n",
        // truncated in the atom block
        HEAD + "  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n",
        // truncated in the bond block
        HEAD + "  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "  1  2  1  0  0  0  0\n",
        // no M  END
        HEAD + "  2  1  0  0  0  0  0  0  0  0  1 V2000\n"
            + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 S   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "  1  2  2  0  0  0  0\n",
        // broken counts line
        HEAD + "xx\n",
        // empty
        "",
        // unknown element and wildcard
        HEAD + "  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
            + "    0.0000    0.0000    0.0000 B   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 X   0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "    0.0000    0.0000    0.0000 Si  0  0  0  0  0  0  0  0  0  0  0  0\n"
            + "  1  2  1  0  0  0  0\n  2  3  3  0  0  0  0\nM  END\n",
    };

    @TestFactory
    List<DynamicTest> mdlReaderAndRewrite() {
        List<DynamicTest> t = new ArrayList<>();
        for (int i = 0; i < MOLFILES.length; i++) {
            String mol = MOLFILES[i];
            t.add(DynamicTest.dynamicTest("molfile " + i, () -> {
                Atom[] l = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader(mol)));
                HeavyAtom[] p = LegacyMdlReader.read(mol);
                assertEquals(describe(l), describe(p), "reader");
                java.io.StringWriter w = new java.io.StringWriter();
                ChemFileUtils.writeMDLMol(w, "Score: -17", l);
                assertEquals(w.toString(), LegacyMdlWriter.write("Score: -17", p), "writer after reader");
            }));
        }
        return t;
    }

    static final String[] LUCY = {
        // title, counts, atom lines "no symbol x y H", bonds "a b order"
        "LUCY result\n6 6\n1 C 0 0 2\n2 C 0 0 2\n3 C 0 0 1\n4 C 0 0 1\n5 O 0 0 0\n6 C 0 0 3\n1 2 1\n2 3 1\n3 4 1\n4 1 1\n3 5 1\n4 6 1\n",
        "acetone\n4 3\n1 C 1 1 3\n2 C 1 1 0\n3 O 1 1 0\n4 C 1 1 3\n1 2 1\n2 3 1\n2 4 1\n",
        "benzene-like\n6 6\n1 C 0 0 1\n2 C 0 0 1\n3 C 0 0 1\n4 C 0 0 1\n5 C 0 0 1\n6 C 0 0 1\n1 2 1\n2 3 1\n3 4 1\n4 5 1\n5 6 1\n6 1 1\n",
        "nitrile\n3 2\n1 C 0 0 3\n2 C 0 0 0\n3 N 0 0 0\n1 2 1\n2 3 1\n",
        "truncated atoms\n3 2\n1 C 0 0 3\n",
        "truncated bonds\n3 2\n1 C 0 0 3\n2 C 0 0 2\n3 O 0 0 1\n1 2 1\n",
        "",
    };

    @TestFactory
    List<DynamicTest> lucyStructureReader(@TempDir Path dir) {
        List<DynamicTest> t = new ArrayList<>();
        for (int i = 0; i < LUCY.length; i++) {
            String text = LUCY[i];
            int ii = i;
            t.add(DynamicTest.dynamicTest("lucy " + i, () -> {
                Path f = dir.resolve("s" + ii + ".lucy");
                Files.write(f, text.getBytes(StandardCharsets.ISO_8859_1));
                String l = OracleSupport.outcome(() -> describe(ChemFileUtils.readLUCYStructure(f.toString())));
                String p = OracleSupport.outcome(() -> describe(LucyStructureReader.read(f)));
                assertEquals(l, p);
            }));
        }
        t.add(DynamicTest.dynamicTest("lucy missing file", () -> {
            Path f = dir.resolve("does-not-exist");
            assertEquals(OracleSupport.outcome(() -> describe(ChemFileUtils.readLUCYStructure(f.toString()))),
                    OracleSupport.outcome(() -> describe(LucyStructureReader.read(f))));
        }));
        return t;
    }
}
