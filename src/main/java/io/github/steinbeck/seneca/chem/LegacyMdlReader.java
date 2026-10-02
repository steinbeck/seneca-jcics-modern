package io.github.steinbeck.seneca.chem;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * compchem {@code ChemFileUtils.readMDLMolFile}: reads a V2000 molfile into a {@code HeavyAtom[]}
 * ({@code number = index}, neighbours in ascending index, valences from {@link StandardElements},
 * {@code saturate()} sets the H counts).
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/ChemFileUtils.java};
 * {@code docs/legacy/compchem-semantics.md} section 10. Ported statement by statement: 3 header lines
 * skipped; counts line parsed after inserting a space at position 3; atom lines tokenised
 * (x, y, z, symbol); bond lines split by inserting spaces at 3/7/11/15; stereo 0 -&gt; order as given,
 * 1 -&gt; 5 (a-&gt;b) / 6 (b-&gt;a), 6 -&gt; 7 / 8, any other stereo value reuses the previous bond's codes
 * (legacy bug; before the first bond these are 0 = no bond); {@code M  CHG} sets {@code charge}; reading
 * stops at {@code M  END}. Any exception while reading is caught and the structure read so far is
 * returned (legacy prints the stack trace; the port drops the console output, DEVIATIONS.md DV-51).
 * The adjacency of atom i lists every j with {@code m[i][j] > 0} in ascending j with order
 * {@code m[i][j]} (so stereo bonds have direction-dependent codes, as legacy).
 *
 * <p>Used by the duplicate check {@code StochasticGenerator.notInStock} and
 * {@code StructureGeneratorResult.removeIsomorphism} (they re-read stored result strings), and by
 * "Load MDL" (F-91). Explicit hydrogens in user files are kept as atoms, as in compchem.
 */
public final class LegacyMdlReader {

    private LegacyMdlReader() {
    }

    public static HeavyAtom[] read(String molfile) {
        return read(new BufferedReader(new StringReader(molfile)));
    }

    public static HeavyAtom[] read(BufferedReader in) {
        int nAtoms;
        int nBonds;
        int codeAB = 0;
        int codeBA = 0;
        int[][] m = new int[0][0];
        List<HeavyAtom> atoms = new ArrayList<>();
        try {
            in.readLine();
            in.readLine();
            in.readLine();
            StringBuilder line = new StringBuilder(in.readLine());
            line.insert(3, " ");
            StringTokenizer st = new StringTokenizer(line.toString());
            nAtoms = Integer.valueOf(st.nextToken());
            nBonds = Integer.valueOf(st.nextToken());
            m = new int[nAtoms][nAtoms];
            for (int i = 0; i < nAtoms; i++) {
                line = new StringBuilder(in.readLine());
                st = new StringTokenizer(line.toString().trim());
                double x = Double.valueOf(st.nextToken());
                double y = Double.valueOf(st.nextToken());
                double z = Double.valueOf(st.nextToken());
                HeavyAtom a = new HeavyAtom(st.nextToken());
                a.x = x;
                a.y = y;
                a.z = z;
                atoms.add(a);
            }
            for (int i = 0; i < nBonds; i++) {
                line = new StringBuilder(in.readLine());
                line.insert(3, " ");
                line.insert(7, " ");
                line.insert(11, " ");
                line.insert(15, " ");
                st = new StringTokenizer(line.toString());
                int a = Integer.valueOf(st.nextToken());
                int b = Integer.valueOf(st.nextToken());
                int order = Integer.valueOf(st.nextToken());
                int stereo = Integer.valueOf(st.nextToken());
                if (stereo == 0) {
                    codeAB = order;
                    codeBA = order;
                } else if (stereo == 1) {
                    codeAB = 5;
                    codeBA = 6;
                } else if (stereo == 6) {
                    codeAB = 7;
                    codeBA = 8;
                }
                m[a - 1][b - 1] = codeAB;
                m[b - 1][a - 1] = codeBA;
            }
            for (String s = in.readLine(); !s.startsWith("M  END"); s = in.readLine()) {
                if (s.startsWith("M  CHG")) {
                    s = s.substring(6);
                    st = new StringTokenizer(s.trim());
                    int count = Integer.valueOf(st.nextToken());
                    for (int k = 1; k <= count; k++) {
                        if (st.countTokens() >= 2) {
                            int atom = Integer.valueOf(st.nextToken());
                            int charge = Integer.valueOf(st.nextToken());
                            atoms.get(atom - 1).charge = charge;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // legacy: "Error while reading MDL Molfile." + stack trace; the partial structure is returned
        }
        for (int i = 0; i < atoms.size(); i++) {
            HeavyAtom a = atoms.get(i);
            a.number = i;
            a.degree = 0;
            for (int j = 0; j < atoms.size(); j++) {
                if (m[i][j] > 0) {
                    a.nodeTable[a.degree] = atoms.get(j);
                    a.bondTable[a.degree] = m[i][j];
                    a.degree++;
                }
            }
        }
        HeavyAtom[] result = new HeavyAtom[atoms.size()];
        for (int i = 0; i < atoms.size(); i++) {
            HeavyAtom a = atoms.get(i);
            a.maxBondOrderSum = StandardElements.getMaxBondOrderSum(a.symbol);
            a.saturate();
            result[i] = a;
        }
        return result;
    }
}
