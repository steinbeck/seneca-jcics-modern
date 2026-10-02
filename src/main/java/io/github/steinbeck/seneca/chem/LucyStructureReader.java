package io.github.steinbeck.seneca.chem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;

/**
 * compchem {@code ChemFileUtils.readLUCYStructure}: LUCY result structure file ("Load LUCY Result",
 * F-92; display only).
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/ChemFileUtils.java}; {@code docs/legacy/
 * compchem-semantics.md} section 10. Ported statement by statement: title line, {@code "nAtoms nBonds"},
 * atom lines (symbol = token 2, H count = token 5), bond lines {@code "a b order"} (1-based, symmetric);
 * adjacency in ascending index; {@code maxBondOrderSum} from {@link StandardElements},
 * {@code bondOrderSum = getBondCount()}; then the greedy bond promotion: for degree 1, 2, 3, for every
 * atom of that degree in index order that is unsaturated ({@code bondOrderSum < maxBondOrderSum - HCount}),
 * raise the order of its first bond to an unsaturated neighbour by one (both directions) and move on.
 * Lines are read as ISO-8859-1 bytes (legacy {@code RandomAccessFile.readLine}). Read errors are caught
 * as in legacy: a file that cannot be read gives {@code [null]} (the legacy initial {@code new Atom[1]}),
 * a partially read file the atoms read so far (unread slots null; the valence pass then throws
 * {@link NullPointerException}, as legacy). Console output of the legacy reader is dropped.
 */
public final class LucyStructureReader {

    private LucyStructureReader() {
    }

    public static HeavyAtom[] read(Path file) throws IOException {
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            text = null;
        }
        return parse(text);
    }

    /** Parses the file content ({@code null}: unreadable file). */
    static HeavyAtom[] parse(String text) {
        int[][] m = new int[0][0];
        HeavyAtom[] atoms = new HeavyAtom[1];
        try {
            BufferedReader in = new BufferedReader(new StringReader(text));
            in.readLine();
            StringTokenizer st = new StringTokenizer(new StringBuilder(in.readLine()).toString());
            int nAtoms = Integer.valueOf(st.nextToken());
            int nBonds = Integer.valueOf(st.nextToken());
            m = new int[nAtoms][nAtoms];
            atoms = new HeavyAtom[m.length];
            for (int i = 0; i < nAtoms; i++) {
                atoms[i] = new HeavyAtom();
                st = new StringTokenizer(new StringBuilder(in.readLine()).toString().trim());
                st.nextToken();
                atoms[i].symbol = st.nextToken();
                st.nextToken();
                st.nextToken();
                atoms[i].hCount = Integer.parseInt(st.nextToken());
                atoms[i].number = i;
                atoms[i].degree = 0;
            }
            for (int i = 0; i < nBonds; i++) {
                st = new StringTokenizer(new StringBuilder(in.readLine()).toString());
                int a = Integer.valueOf(st.nextToken());
                int b = Integer.valueOf(st.nextToken());
                int order = Integer.valueOf(st.nextToken());
                m[a - 1][b - 1] = order;
                m[b - 1][a - 1] = order;
            }
        } catch (Exception e) {
            // legacy: "Error while reading LUCY result file." on stderr
        }
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m.length; j++) {
                if (m[i][j] > 0) {
                    atoms[i].nodeTable[atoms[i].degree] = atoms[j];
                    atoms[i].bondTable[atoms[i].degree] = m[i][j];
                    atoms[i].degree++;
                }
            }
        }
        for (int i = 0; i < m.length; i++) {
            atoms[i].maxBondOrderSum = StandardElements.getMaxBondOrderSum(atoms[i].symbol);
            atoms[i].bondOrderSum = atoms[i].getBondCount();
        }
        for (int degree = 1; degree < 4; degree++) {
            for (int i = 0; i < m.length; i++) {
                HeavyAtom a = atoms[i];
                if (a.degree == degree && a.bondOrderSum < a.maxBondOrderSum - a.hCount) {
                    for (int k = 0; k < a.degree; k++) {
                        HeavyAtom b = a.nodeTable[k];
                        if (b.bondOrderSum < b.maxBondOrderSum - b.hCount) {
                            a.bondTable[k]++;
                            a.bondOrderSum++;
                            for (int l = 0; l < b.degree; l++) {
                                if (b.nodeTable[l] == a) {
                                    b.bondTable[l]++;
                                    b.bondOrderSum++;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        return atoms;
    }
}
