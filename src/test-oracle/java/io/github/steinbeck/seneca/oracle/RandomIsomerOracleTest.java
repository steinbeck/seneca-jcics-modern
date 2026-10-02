package io.github.steinbeck.seneca.oracle;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.openscience.cdk.interfaces.IAtomContainer;

import compchem.Atom;
import compchem.BremserOneSphereHOSECodePredictor;
import compchem.ChemFileUtils;
import compchem.DataStructureTools;
import compchem.HOSECodeGenerator;
import compchem.MFAnalyser;
import compchem.Ring;
import compchem.SetOfRingsFinder;
import compchem.setOfRings;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.BremserPredictor;
import io.github.steinbeck.seneca.chem.CdkBridge;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.HoseCodeGenerator;
import io.github.steinbeck.seneca.chem.IsomorphismTester;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.LegacyMdlWriter;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.chem.PathUtils;
import io.github.steinbeck.seneca.chem.RingFinder;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.oracle.OracleSupport.Struct;
import io.github.steinbeck.seneca.oracle.OracleSupport.Tally;

/**
 * Differential test of every compchem re-implementation against the original {@code compchem.jar}
 * on many structures (the method of {@code docs/legacy/harness/LegacyVsCdk.java}, section 14 of
 * compchem-semantics.md): for every structure the Morgan numbers, connectivity, path lengths, Floyd APSP,
 * {@code cloneAtomSet}, HOSE codes
 * (also against CDK 2.13 + {@code makeBremserCompliant}), Bremser predictions, the ring finder (every
 * ring's atom sequence, or the exception class and message), the MDL writer (byte-exact) and reader, the
 * isomorphism heuristic (against the last 30 structures, as {@code notInStock}) and the molecular-formula
 * string must be identical. 0 mismatches required.
 *
 * <p>Structure sets: (1) the 16 seed molecules of LegacyVsCdk, 2,000 Faulon moves each, every move
 * accepted (~24,000 connected isomers); (2) per golden dataset the 50 score structures, every result
 * structure of the complete golden runs (annealed, ring-rich), the crash candidates, and random walks from
 * the last score structure and from every crash candidate (to exercise the ring finder's failure modes).
 */
@Tag("A")
class RandomIsomerOracleTest {

    static final int STEPS_PER_SEED = 2000;

    @TestFactory
    List<DynamicTest> seedMolecules() {
        List<DynamicTest> t = new ArrayList<>();
        for (int s = 0; s < OracleSupport.SEEDS.length; s++) {
            String smiles = OracleSupport.SEEDS[s];
            long seed = 20011500L + s;
            t.add(DynamicTest.dynamicTest(smiles, () -> {
                Struct start = OracleSupport.fromSmiles(smiles);
                List<Struct> all = OracleSupport.randomWalk(start, STEPS_PER_SEED, new Random(seed), true);
                Tally tally = new Tally();
                compareAll(all, tally, true);
                tally.assertNone();
            }));
        }
        return t;
    }

    @TestFactory
    List<DynamicTest> goldenDatasets() {
        List<DynamicTest> t = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            t.add(DynamicTest.dynamicTest(key, () -> {
                List<Struct> l = new ArrayList<>(OracleSupport.scoreStructures(key));
                l.addAll(OracleSupport.runStructures(key));
                Random rng = new Random(key.hashCode());
                l.addAll(OracleSupport.randomWalk(l.get(49), 1000, rng, true));
                for (Struct c : OracleSupport.crashCandidates()) {
                    if (c.sym().length == l.get(0).sym().length && c.name().startsWith(key + "."))
                        l.addAll(OracleSupport.randomWalk(c, 300, rng, true));
                }
                Tally tally = new Tally();
                compareAll(l, tally, false);
                tally.assertNone();
            }));
        }
        return t;
    }

    /** Ring finder detail: every ring's atom-number sequence in discovery order, or the exception. */
    static String legacyRings(Atom[] atoms) {
        return OracleSupport.outcome(() -> {
            setOfRings s = new SetOfRingsFinder().findSSSR(atoms);
            List<String> rings = new ArrayList<>();
            for (int i = 0; i < s.size(); i++) rings.add(((Ring) s.elementAt(i)).getString());
            return rings;
        });
    }

    static String portRings(HeavyAtom[] atoms) {
        return OracleSupport.outcome(() -> {
            List<String> rings = new ArrayList<>();
            for (io.github.steinbeck.seneca.chem.Ring r : new RingFinder().findSSSR(atoms)) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.size(); i++) {
                    if (i > 0) sb.append('-');
                    sb.append(r.atoms.elementAt(i).number);
                }
                rings.add(sb.toString());
            }
            return rings;
        });
    }

    static void compareAll(List<Struct> structures, Tally tally, boolean withCdkHose) {
        HOSECodeGenerator lh = new HOSECodeGenerator();
        HoseCodeGenerator ph = new HoseCodeGenerator();
        org.openscience.cdk.tools.HOSECodeGenerator ch = new org.openscience.cdk.tools.HOSECodeGenerator();
        BremserOneSphereHOSECodePredictor lp = new BremserOneSphereHOSECodePredictor();
        BremserPredictor pp = new BremserPredictor();
        Deque<Object[]> stock = new ArrayDeque<>();
        int k = 0;
        for (Struct s : structures) {
            k++;
            Atom[] la = s.legacy();
            HeavyAtom[] pa = s.port();
            String w = s.name();
            tally.same("isConnected", compchem.PathUtils.isConnected(la), PathUtils.isConnected(pa), w);
            if (!OracleSupport.connected(s.bm())) continue;
            tally.same("Morgan numbers", java.util.Arrays.toString(DataStructureTools.computeMorganMatrix(la)),
                    java.util.Arrays.toString(AtomSets.computeMorganMatrix(pa)), w);
            if (k % 10 == 0) {
                tally.same("Floyd APSP", java.util.Arrays.deepToString(new compchem.ShortestPathFinder().computeFloydAPSP(DataStructureTools.createConnectionTable(la))),
                        java.util.Arrays.deepToString(io.github.steinbeck.seneca.chem.FloydApsp.compute(AtomSets.createConnectionTable(pa))), w);
                tally.same("cloneAtomSet", MdlOracleTest.describe(DataStructureTools.cloneAtomSet(la)), MdlOracleTest.describe(AtomSets.cloneAtomSet(pa)), w);
                for (int cutoff = 1; cutoff <= 3; cutoff++)
                    for (int i = 0; i < pa.length; i++)
                        for (int j = 0; j < pa.length; j++)
                            tally.same("getPathLength", compchem.PathUtils.getPathLength(la, la[i], la[j], cutoff),
                                    PathUtils.getPathLength(pa, pa[i], pa[j], cutoff), w + " " + i + "-" + j + "/" + cutoff);
            }
            IAtomContainer cdk = withCdkHose ? CdkBridge.toAtomContainer(pa, s.bm()) : null;
            for (int i = 0; i < pa.length; i++) {
                int ii = i;
                String lc = OracleSupport.outcome(() -> lh.getHOSECode(la, la[ii], 1));
                String pc = OracleSupport.outcome(() -> ph.getHOSECode(pa, pa[ii], 1));
                tally.same("HOSE code (1 sphere)", lc, pc, w + " atom " + i);
                if (cdk != null) {
                    String cc = OracleSupport.outcome(() -> {
                        return ch.makeBremserCompliant(ch.getHOSECode(cdk, cdk.getAtom(ii), 1));
                    });
                    tally.same("HOSE code vs CDK + makeBremserCompliant", cc, pc, w + " atom " + i);
                }
                if (pa[i].symbol.equals("C")) {
                    tally.same("Bremser shift", OracleSupport.outcome(() -> lp.predict(lc)), OracleSupport.outcome(() -> pp.predict(pc)).replace(
                            "io.github.steinbeck.seneca.chem.BremserPredictor$UnknownCodeException: No such HOSE code: " + pc,
                            "java.lang.Exception: No such HOSE code"), w + " atom " + i + " " + lc);
                    tally.same("Bremser confidence limit", OracleSupport.outcome(() -> lp.getConfidenceLimit(lc)),
                            OracleSupport.outcome(() -> pp.getConfidenceLimit(pc)).replace(
                                    "io.github.steinbeck.seneca.chem.BremserPredictor$UnknownCodeException: No such HOSE code: " + pc,
                                    "java.lang.Exception: No such HOSE code"), w + " atom " + i);
                }
            }
            tally.same("ring finder (rings or exception)", legacyRings(la), portRings(pa), w);
            StringWriter sw = new StringWriter();
            ChemFileUtils.writeMDLMol(sw, "Score: " + k, la);
            String lmdl = sw.toString();
            String pmdl = LegacyMdlWriter.write("Score: " + k, pa);
            tally.same("MDL writer", lmdl, pmdl, w);
            Atom[] lr = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader(lmdl)));
            HeavyAtom[] pr = LegacyMdlReader.read(lmdl);
            tally.same("MDL reader", MdlOracleTest.describe(lr), MdlOracleTest.describe(pr), w);
            tally.same("MFAnalyser(Atom[]) formula", new MFAnalyser(la).getMolecularFormula(), MolecularFormula.formulaOf(pa), w);
            tally.same("MFAnalyser(Atom[]) H count", new MFAnalyser(la).getAtomCount("H"), MolecularFormula.atomCount(pa, "H"), w);
            for (Object[] old : stock) {
                Atom[] ol = (Atom[]) old[0];
                HeavyAtom[] op = (HeavyAtom[]) old[1];
                if (ol.length != la.length) continue;
                tally.same("IsomorphismTester", new compchem.IsomorphismTester(la).isIsomorph(ol), new IsomorphismTester(pa).isIsomorph(op), w + " vs " + old[2]);
                tally.same("IsomorphismTester (reversed)", new compchem.IsomorphismTester(ol).isIsomorph(la), new IsomorphismTester(op).isIsomorph(pa), w + " vs " + old[2]);
                // as notInStock: base = new structure, compare = stored MDL string re-read
                tally.same("IsomorphismTester vs re-read MDL", new compchem.IsomorphismTester(la).isIsomorph(ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String) old[3])))),
                        new IsomorphismTester(pa).isIsomorph(LegacyMdlReader.read((String) old[3])), w + " vs " + old[2]);
            }
            stock.addFirst(new Object[] {la, pa, w, lmdl});
            if (stock.size() > 30) stock.removeLast();
        }
    }
}
