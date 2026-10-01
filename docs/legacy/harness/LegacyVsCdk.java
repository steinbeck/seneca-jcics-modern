// Reference harness: runs the ORIGINAL compchem.jar bytecode side by side with CDK 2.13
// on random constitutional isomers produced by SENECA's own Faulon mutation, and counts
// every disagreement. Not part of the port; evidence for docs/legacy/compchem-semantics.md.
//
// Build/run: see run.sh next to this file.

import compchem.Atom;
import compchem.ChemFileUtils;
import compchem.DataStructureTools;
import compchem.HOSECodeGenerator;
import compchem.MFAnalyser;
import compchem.PathUtils;
import compchem.Ring;
import compchem.SetOfRingsFinder;
import compchem.ShortestPathFinder;
import compchem.StandardElements;
import compchem.setOfRings;

import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.graph.AllPairsShortestPaths;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.graph.PathTools;
import org.openscience.cdk.graph.invariant.MorganNumbersTools;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.isomorphism.IsomorphismTester;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.BremserOneSphereHOSECodePredictor;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;

public class LegacyVsCdk {

    static final String[] SEEDS = {
        "CC1(C)C2CCC1(C)C(=O)C2",                       // camphor C10H16O
        "CC1=CCC2CC1C2(C)C",                            // alpha-pinene C10H16
        "CC(C)C1CCC(C)CC1O",                            // menthol
        "C12C3C4C1C5C2C3C45",                           // cubane
        "C1C2CC3CC1CC(C2)C3",                           // adamantane
        "CN1CCCC1C1=CC=CN=C1",                          // nicotine (Kekule)
        "CN1C=NC2=C1C(=O)N(C)C(=O)N2C",                 // caffeine (Kekule)
        "CC(CCC=C(C)C)C1CCC2(C)C1(C)CCC1C2CCC2C(C)(C)C(O)CCC12C", // lanostane-type C30H52O
        "CSC1=CC=C(Cl)C=C1Br",
        "C[Si](C)(C)OC1CCCCC1",
        "CCP(CC)CC1CC1",
        "ICC1CC1C#CC",
        "FC(F)C1CC1N=O",
        "O=C1OC2=CC=CC=C2C=C1CC(O)C",                    // coumarin-type, O-rich
        "CC1=CC(=O)OC2=C1C=CC(O)=C2C(=O)CC",            // C13H12O4-type
        "CCCCC1OC1C(O)CC#CC#CC",                        // polyacetylene epoxide
    };

    static final Random RNG = new Random(20011500L);
    static final ExecutorService EXEC = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
    static int sssrHangs = 0;

    // ---- counters ----
    static final Map<String, long[]> C = new TreeMap<>(); // name -> {checked, mismatches}
    static final Map<String, List<String>> EX = new HashMap<>();
    static void count(String k, boolean mismatch, String example) {
        long[] v = C.computeIfAbsent(k, x -> new long[2]);
        v[0]++;
        if (mismatch) {
            v[1]++;
            List<String> l = EX.computeIfAbsent(k, x -> new ArrayList<>());
            if (l.size() < 3) l.add(example);
        }
    }

    public static void main(String[] args) throws Exception {
        int stepsPerSeed = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
        mfTests();
        for (String smi : SEEDS) {
            IAtomContainer m = sp.parseSmiles(smi);
            AtomContainerManipulator.suppressHydrogens(m);
            int n = m.getAtomCount();
            String[] sym = new String[n];
            int[] hc = new int[n];
            for (int i = 0; i < n; i++) { sym[i] = m.getAtom(i).getSymbol(); hc[i] = m.getAtom(i).getImplicitHydrogenCount(); }
            int[][] bm = new int[n][n];
            for (IBond b : m.bonds()) {
                int u = m.indexOf(b.getBegin()), v = m.indexOf(b.getEnd());
                bm[u][v] = bm[v][u] = b.getOrder().numeric();
            }
            Deque<Object[]> stock = new ArrayDeque<>(); // last 30 accepted: {legacyMDL, legacyAtoms, cdkMol}
            for (int step = 0; step < stepsPerSeed; step++) {
                int[][] nb = clone(bm);
                mutate(nb);
                Atom[] la = legacyAtoms(sym, hc, nb);
                IAtomContainer cm = cdkMol(sym, hc, nb);
                boolean lc = PathUtils.isConnected(la);
                boolean cc = ConnectivityChecker.isConnected(cm);
                count("isConnected", lc != cc, smi);
                bm = nb; // random walk accepts every move (like T -> infinity)
                if (!lc) continue;
                compare(la, cm, smi);
                isoCompare(la, cm, stock, smi);
            }
        }
        if (args.length > 1) crashCandidates(args[1]);
        System.out.printf("%-34s %10s %10s%n", "check", "checked", "mismatch");
        for (Map.Entry<String, long[]> e : C.entrySet()) {
            System.out.printf("%-34s %10d %10d%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            if (EX.containsKey(e.getKey())) for (String s : EX.get(e.getKey())) System.out.println("      e.g. " + s);
        }
        System.out.println("legacy findSSSR hangs (>2s, abandoned): " + sssrHangs);
        System.exit(0);
    }

    // ---------------- comparisons ----------------
    static final HOSECodeGenerator LHCG = new HOSECodeGenerator();
    static final compchem.BremserOneSphereHOSECodePredictor LPRED = new compchem.BremserOneSphereHOSECodePredictor();
    static final BremserOneSphereHOSECodePredictor CPRED = new BremserOneSphereHOSECodePredictor();

    static void compare(Atom[] la, IAtomContainer cm, String seed) throws Exception {
        int n = la.length;
        String smi = new SmilesGenerator(SmiFlavor.Canonical).create(cm);
        // HOSE one-sphere
        org.openscience.cdk.tools.HOSECodeGenerator chcg = new org.openscience.cdk.tools.HOSECodeGenerator();
        for (int i = 0; i < n; i++) {
            String l = LHCG.getHOSECode(la, la[i], 1);
            String c = chcg.makeBremserCompliant(chcg.getHOSECode(cm, cm.getAtom(i), 1));
            count("HOSE 1-sphere (all atoms)", !l.equals(c), smi + " atom " + i + ": legacy " + l + " cdk " + c);
            if (la[i].symbol.equals("C")) {
                String lp, cp;
                try { lp = LPRED.predict(l) + "/" + LPRED.getConfidenceLimit(l); } catch (Exception e) { lp = "none"; }
                try { cp = CPRED.predict(c) + "/" + CPRED.getConfidenceLimit(c); } catch (Exception e) { cp = "none"; }
                count("Bremser shift+conf (carbons)", !lp.equals(cp), smi + " atom " + i + " " + l + ": " + lp + " vs " + cp);
                count("Bremser code not in table", lp.equals("none"), l);
            }
        }
        // Morgan
        int[] lm = DataStructureTools.computeMorganMatrix(la);
        long[] cmn = MorganNumbersTools.getMorganNumbers(cm);
        boolean mm = false, mmLong = false;
        for (int i = 0; i < n; i++) { if (lm[i] != (int) cmn[i]) mm = true; if (lm[i] != cmn[i]) mmLong = true; }
        count("Morgan == (int)CDK", mm, smi);
        count("Morgan == CDK long (no cast)", mmLong, smi + " (int overflow in legacy)");
        // symmetry-judge relation: pairwise equality
        boolean rel = false;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if ((lm[i] == lm[j]) != (cmn[i] == cmn[j])) rel = true;
        count("Morgan pair-equality vs CDK long", rel, smi);
        // path lengths (TwoDSpectrumJudge, cutoff 1..3) and Floyd
        AllPairsShortestPaths apsp = new AllPairsShortestPaths(cm);
        int[][] lf = new ShortestPathFinder().computeFloydAPSP(DataStructureTools.createConnectionTable(la));
        int[][] cf = PathTools.computeFloydAPSP(DataStructureTools.createConnectionTable(la));
        boolean pl = false, fl = false;
        for (int cutoff = 1; cutoff <= 3; cutoff++)
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                int l = PathUtils.getPathLength(la, la[i], la[j], cutoff);
                int d = apsp.from(i).distanceTo(j);
                int c = (i != j && d <= cutoff) ? d : -1;
                if (l != c) pl = true;
            }
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (lf[i][j] != cf[i][j] || lf[i][j] != apsp.from(i).distanceTo(j)) fl = true;
        count("PathUtils.getPathLength (cut 1-3)", pl, smi);
        count("Floyd APSP vs CDK", fl, smi);
        // rings
        List<Integer> lr = legacySssrSizes(la);
        List<Integer> cr = new ArrayList<>();
        for (int[] p : Cycles.sssr(cm).paths()) cr.add(p.length - 1);
        Collections.sort(cr);
        if (lr != null) {
            count("SSSR ring-size multiset", !lr.equals(cr), smi + " legacy " + lr + " cdk " + cr);
            count("SSSR #3- and #4-rings", cnt(lr, 3) != cnt(cr, 3) || cnt(lr, 4) != cnt(cr, 4), smi + " legacy " + lr + " cdk " + cr);
            count("SSSR ring count", lr.size() != cr.size(), smi + " legacy " + lr + " cdk " + cr);
            List<Integer> fr = new ArrayList<>();
            org.openscience.cdk.interfaces.IRingSet frs = new org.openscience.cdk.ringsearch.FiguerasSSSRFinder().findSSSR(cm);
            for (org.openscience.cdk.interfaces.IAtomContainer r : frs.atomContainers()) fr.add(r.getAtomCount());
            Collections.sort(fr);
            count("SSSR sizes vs CDK FiguerasSSSRFinder", !lr.equals(fr), smi + " legacy " + lr + " cdk-figueras " + fr);
        }
        // implicit H after MDL round trip: legacy writer -> CDK reader
        String mdl = legacyMdl(la);
        IAtomContainer rr = new MDLV2000Reader(new StringReader(mdl)).read(SilentChemObjectBuilder.getInstance().newAtomContainer());
        Atom[] lrt = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader(mdl)));
        boolean hmis = false, hl = false;
        for (int i = 0; i < n; i++) {
            if (rr.getAtom(i).getImplicitHydrogenCount() != la[i].HCount) hmis = true;
            if (lrt[i].HCount != la[i].HCount) hl = true;
        }
        count("MDL roundtrip H: legacy reader", hl, smi);
        count("MDL roundtrip H: CDK MDLV2000Reader", hmis, smi);
    }

    static int cnt(List<Integer> l, int v) { int c = 0; for (int x : l) if (x == v) c++; return c; }

    // Legacy ring finder with probes that name the failure mode before the original code throws.
    // Behaviour is unchanged: both overrides call the original method.
    static class ProbeFinder extends SetOfRingsFinder {
        String mode = null;
        @Override public void breakBond(Atom a, Atom[] g) {
            if (a.degree == 0 && mode == null) mode = "degree-0 breakBond(Atom) in the degree-2 branch";
            super.breakBond(a, g);
        }
        // checkEdges cannot be overridden (compchem.Bond is package-private); its failure on an
        // empty ring (Vector.elementAt(0) of an empty vector) is recognised by the message.
        String classify(Throwable t) {
            if (mode == null) mode = String.valueOf(t.getMessage()).startsWith("0 >= 0")
                ? "checkEdges on an empty ring (degree-3 branch)" : "other: " + t;
            return mode;
        }
    }

    // Structures on which the original generator died (golden/runs/*.json, crash.candidateCt),
    // prepared by run.sh as: id|el,el,...|h,h,...|u-v-o u-v-o ...
    static void crashCandidates(String file) throws Exception {
        List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(file));
        System.out.println("Golden crash candidates (" + lines.size() + "):");
        for (String line : lines) {
            String[] f = line.split("\\|");
            String[] sym = f[1].split(",");
            int n = sym.length;
            int[] hc = Arrays.stream(f[2].split(",")).mapToInt(Integer::parseInt).toArray();
            int[][] bm = new int[n][n];
            for (String b : f[3].trim().split(" ")) {
                String[] t = b.split("-");
                int u = Integer.parseInt(t[0]), v = Integer.parseInt(t[1]), o = Integer.parseInt(t[2]);
                bm[u][v] = bm[v][u] = o;
            }
            Atom[] la = legacyAtoms(sym, hc, bm);
            IAtomContainer cm = cdkMol(sym, hc, bm);
            ProbeFinder pf = new ProbeFinder();
            String outcome;
            try {
                setOfRings s = pf.findSSSR(la);
                outcome = "no exception, " + s.size() + " rings";
            } catch (Throwable t) {
                outcome = t + " via " + pf.classify(t);
            }
            boolean threw = pf.mode != null;
            count("golden crash cand.: legacy findSSSR does NOT throw", !threw, f[0]);
            count("golden crash cand.: trigger other than degree-0 breakBond", threw && !pf.mode.startsWith("degree-0"), f[0] + " " + pf.mode);
            List<Integer> cr = new ArrayList<>();
            for (int[] p : Cycles.sssr(cm).paths()) cr.add(p.length - 1);
            Collections.sort(cr);
            System.out.println("  " + f[0] + ": " + outcome + "; CDK Cycles.sssr sizes " + cr);
        }
    }

    static List<Integer> legacySssrSizes(Atom[] la) {
        if (sssrHangs >= 3) return null;
        ProbeFinder pf = new ProbeFinder();
        Future<List<Integer>> f = EXEC.submit(() -> {
            setOfRings s = pf.findSSSR(la);
            List<Integer> r = new ArrayList<>();
            for (int i = 0; i < s.size(); i++) r.add(((Ring) s.elementAt(i)).size());
            Collections.sort(r);
            return r;
        });
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            sssrHangs++;
            count("legacy findSSSR hang", true, legacyMdl(la).replace('\n', '|'));
            return null;
        } catch (ExecutionException ee) {
            count("legacy findSSSR exception: " + pf.classify(ee.getCause()), true, ee.getCause().toString());
            return null;
        } catch (InterruptedException ie) {
            return null;
        }
    }

    static void isoCompare(Atom[] la, IAtomContainer cm, Deque<Object[]> stock, String seed) throws Exception {
        String mdl = legacyMdl(la);
        SmilesGenerator kek = new SmilesGenerator(SmiFlavor.Canonical);
        String k1 = kek.create(cm);
        String a1 = aromSmiles(cm);
        compchem.IsomorphismTester lit = new compchem.IsomorphismTester(la);
        IsomorphismTester cit = new IsomorphismTester();
        for (Object[] o : stock) {
            Atom[] other = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String) o[0])));
            boolean l = lit.isIsomorph(other);
            boolean c = cit.isIsomorphic(cm, (IAtomContainer) o[1]);
            boolean tk = k1.equals(o[2]);
            boolean ta = a1.equals(o[3]);
            count("Iso: legacy vs CDK IsomorphismTester", l != c, k1 + " vs " + o[2]);
            count("Iso: legacy vs canonical SMILES (Kekule)", l != tk, k1 + " vs " + o[2] + " legacy=" + l);
            count("Iso: legacy false positive (arom.)", l && !ta, a1 + " vs " + o[3]);
            count("Iso: legacy false negative (Kekule)", !l && tk, k1);
        }
        stock.addFirst(new Object[]{mdl, cm, k1, a1});
        if (stock.size() > 30) stock.removeLast();
    }

    static String aromSmiles(IAtomContainer m) throws Exception {
        IAtomContainer c = m.clone();
        new Aromaticity(ElectronDonation.daylight(), Cycles.all()).apply(c);
        return new SmilesGenerator(SmiFlavor.Canonical | SmiFlavor.UseAromaticSymbols).create(c);
    }

    // ---------------- MF / mass ----------------
    static void mfTests() {
        String[] mfs = {"C10H16O", "C19H30N2O3", "C27H28O6", "C13H14O3", "C15H28O2", "C18H20O5", "C20H20N2O4", "C10H16",
                        "C30H48O2", "C13H16O3", "C32H52O3", "C6H5Cl", "C2H6OS", "C9H20Si", "CH3Br", "C4H10ClP"};
        for (String mf : mfs) {
            MFAnalyser a = new MFAnalyser(mf);
            org.openscience.cdk.interfaces.IMolecularFormula f =
                MolecularFormulaManipulator.getMolecularFormula(mf, SilentChemObjectBuilder.getInstance());
            boolean cntMis = false;
            for (String el : new String[]{"C", "H", "N", "O", "S", "Cl", "Br", "Si", "P"}) {
                int cd = MolecularFormulaManipulator.getElementCount(f, el);
                if (a.getAtomCount(el) != cd) cntMis = true;
            }
            count("MFAnalyser element counts", cntMis, mf);
            double cdkMass = MolecularFormulaManipulator.getMass(f, MolecularFormulaManipulator.MonoIsotopic);
            float lm = a.getMass();
            count("MFAnalyser mass == CDK monoisotopic (float)", lm != (float) cdkMass, mf + " legacy " + lm + " cdk " + cdkMass);
        }
    }

    // ---------------- builders ----------------
    static Atom[] legacyAtoms(String[] sym, int[] hc, int[][] bm) {
        StandardElements se = new StandardElements();
        Atom[] a = new Atom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new Atom(sym[i]);
            a[i].number = i;
            a[i].HCount = hc[i];
            a[i].maxBondOrder = se.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = se.getMaxBondOrderSum(sym[i]);
        }
        DataStructureTools.synchronizeBonds(a, bm);
        return a;
    }

    static IAtomContainer cdkMol(String[] sym, int[] hc, int[][] bm) {
        IAtomContainer m = SilentChemObjectBuilder.getInstance().newAtomContainer();
        for (int i = 0; i < sym.length; i++) {
            IAtom at = m.newAtom(org.openscience.cdk.config.Elements.ofString(sym[i]).number());
            at.setImplicitHydrogenCount(hc[i]);
        }
        for (int i = 0; i < sym.length; i++)
            for (int j = i + 1; j < sym.length; j++)
                if (bm[i][j] > 0) m.newBond(m.getAtom(i), m.getAtom(j), IBond.Order.values()[bm[i][j] - 1]);
        return m;
    }

    static String legacyMdl(Atom[] a) {
        StringWriter sw = new StringWriter();
        ChemFileUtils.writeMDLMol(sw, "x", a);
        return sw.toString();
    }

    static int[][] clone(int[][] m) { int[][] r = new int[m.length][]; for (int i = 0; i < m.length; i++) r[i] = m[i].clone(); return r; }

    // verbatim copy of seneca.structgen.StochasticGenerator.mutate (Faulon 1996), with a seeded RNG
    static void mutate(int[][] bm) {
        int x1, x2, y1, y2, a11, a12, a22, a21, b11, min, max;
        int n = bm.length;
        do {
            do {
                x1 = (int) (RNG.nextDouble() * n);
                x2 = (int) (RNG.nextDouble() * n);
                y1 = (int) (RNG.nextDouble() * n);
                y2 = (int) (RNG.nextDouble() * n);
            } while (!(x1 != x2 && x1 != y1 && x1 != y2 && x2 != y1 && x2 != y2 && y1 != y2));
            a11 = bm[x1][y1]; a12 = bm[x1][y2]; a22 = bm[x2][y2]; a21 = bm[x2][y1];
            int[] cmax = {0, a11 - a22, a11 + a12 - 3, a11 + a21 - 3};
            int[] cmin = {3, a11 + a12, a11 + a21, a11 - a22 + 3};
            min = Arrays.stream(cmax).max().getAsInt();
            max = Arrays.stream(cmin).min().getAsInt();
            b11 = a11;
            if (max - min > 0 || a11 != min) {
                do { b11 = ((int) (RNG.nextDouble() * (max - min + 1))) + min; } while (a11 == b11);
            }
        } while (!(b11 != a11 && (b11 >= min && b11 <= max)));
        bm[x1][y1] = b11; bm[x1][y2] = a11 + a12 - b11; bm[x2][y2] = a22 - a11 + b11; bm[x2][y1] = a11 + a21 - b11;
        bm[y1][x1] = b11; bm[y2][x1] = a11 + a12 - b11; bm[y2][x2] = a22 - a11 + b11; bm[y1][x2] = a11 + a21 - b11;
    }
}
