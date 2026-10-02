package io.github.steinbeck.seneca.chem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import javax.vecmath.Point2d;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.layout.StructureDiagramGenerator;

/**
 * 2D layout for structure drawings (GUI) and "Save MDL" ({@link CdkBridge#layout2D}): CDK's
 * {@code StructureDiagramGenerator}, judged by a layout {@link #penalty} (bond crossings, crowded atoms,
 * and above all <em>overlaps</em>: atoms drawn on top of each other or on a bond they do not belong to,
 * which make the drawing read as a different constitution) and improved where it is not clean:
 *
 * <ol>
 *   <li>the layout in dataset atom order; if its penalty is 0, it is kept unchanged;</li>
 *   <li>otherwise {@link #ORDERS} more layouts of the same molecule with its atoms in other, fixed
 *       pseudo-random orders (CDK's result depends on the atom order: e.g. which ring of a bridged system
 *       is drawn first);</li>
 *   <li>each candidate is refined by turning substituents about their acyclic bond
 *       ({@link #refine}: every acyclic bond, the smaller side rotated in 15 degree steps or mirrored
 *       across the bond, kept if the penalty falls), which moves e.g. the gem-dimethyl group of a
 *       norbornane bridge off the ring's double bond;</li>
 *   <li>a candidate that still has overlaps is relaxed ({@link #separate}: the overlapping atoms pushed
 *       apart, bond lengths kept, every atom held near its place) and refined again, up to
 *       {@value #SEPARATE_ROUNDS} times, as long as the penalty falls;</li>
 *   <li>the candidate with the smallest penalty wins (ties: the earlier one).</li>
 * </ol>
 * Deterministic: the same atoms always give the same coordinates. Only coordinates change.
 */
public final class Layout2D {

    /** Additional atom orders tried when the first layout is not clean. */
    public static final int ORDERS = 8;

    /** Molecules above this many atoms get only the dataset-order layout and its refinement (time). */
    public static final int MAX_ATOMS_FOR_ORDERS = 60;

    private static final double STEP = Math.toRadians(15);
    private static final int REFINE_PASSES = 3;

    /** Penalty of one overlap (atoms on top of each other or on a foreign bond). */
    static final double HARD = 1000;
    /** Non-bonded atoms closer than this (bond lengths) overlap; the visual review's limit is 0.2. */
    static final double COINCIDENT_LIMIT = 0.3;
    /** An atom closer than this (bond lengths) to a foreign bond overlaps it; the review's limit is 0.12. */
    static final double ON_BOND_LIMIT = 0.2;
    /** ... where the foot of the perpendicular is not in the outer 5 % of the bond (review: 8 %). */
    static final double ON_BOND_MARGIN = 0.05;
    /** The visual review's limits ({@link #overlaps(IAtomContainer)}); an overlap at these costs {@link #HARD} twice. */
    static final double REVIEW_COINCIDENT = 0.2, REVIEW_ON_BOND = 0.12, REVIEW_MARGIN = 0.08;

    private Layout2D() {
    }

    /**
     * Lays out {@code m} in place (2D coordinates of every atom).
     *
     * @throws CDKException if CDK cannot lay it out
     */
    public static void layout(IAtomContainer m) throws CDKException {
        int n = m.getAtomCount();
        Scorer scorer = new Scorer(m);
        Point2d[] best = improve(m, scorer, sdg(m, 0));
        double bestPenalty = scorer.penalty(best);
        if (bestPenalty > 0 && n > 2 && n <= MAX_ATOMS_FOR_ORDERS) {
            for (int k = 1; k <= ORDERS && bestPenalty > 0; k++) {
                Point2d[] c;
                try {
                    c = sdg(m, k);
                } catch (CDKException | RuntimeException e) {
                    continue;                      // a failing order is just not a candidate
                }
                c = improve(m, scorer, c);
                double p = scorer.penalty(c);
                if (p < bestPenalty - 1e-9) {
                    best = c;
                    bestPenalty = p;
                }
            }
        }
        for (int i = 0; i < n; i++) m.getAtom(i).setPoint2d(new Point2d(best[i]));
    }

    /**
     * One candidate: {@link #refine} (substituents turned), then, while atoms still lie on top of each
     * other or on a foreign bond, {@link #separate} (up to {@value #SEPARATE_ROUNDS} rounds, each pushing
     * harder) and another {@link #refine}; each round is kept only if it lowers the {@link #penalty}.
     */
    private static Point2d[] improve(IAtomContainer m, Scorer scorer, Point2d[] p) {
        Point2d[] cur = refine(m, p);
        double pen = scorer.penalty(cur);
        for (int round = 1; round <= SEPARATE_ROUNDS && scorer.overlaps(cur) > 0; round++) {
            Point2d[] sep = refine(m, separate(scorer, cur, round));
            double sp = scorer.penalty(sep);
            if (sp < pen - 1e-9) {
                cur = sep;
                pen = sp;
            }
        }
        return cur;
    }

    /** CDK layout of a copy of {@code m} with its atoms in permutation {@code order} (0: as they are). */
    private static Point2d[] sdg(IAtomContainer m, long order) throws CDKException {
        IAtomContainer c;
        try {
            c = m.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        List<IAtom> original = new ArrayList<>();
        for (IAtom a : c.atoms()) original.add(a);
        if (order != 0) {
            List<IAtom> shuffled = new ArrayList<>(original);
            shuffle(shuffled, order);
            c.setAtoms(shuffled.toArray(new IAtom[0]));
        }
        new StructureDiagramGenerator().generateCoordinates(c);
        Point2d[] p = new Point2d[original.size()];
        for (int i = 0; i < p.length; i++) p[i] = new Point2d(original.get(i).getPoint2d());
        return p;
    }

    /**
     * Fisher-Yates shuffle driven by SplitMix64 from {@code seed}: a fixed permutation per seed (display
     * only; the runs' random source {@code RunRandom} is not touched).
     */
    static <T> void shuffle(List<T> list, long seed) {
        long state = seed;
        for (int i = list.size() - 1; i > 0; i--) {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            int j = (int) Math.floorMod(z, (long) (i + 1));
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }

    // ------------------------------------------------------------------ quality

    /**
     * Layout penalty of {@code m} with coordinates {@code p} (index = atom index): 10 per pair of
     * crossing bonds (20 if one of them is a multiple bond); 15 per non-ring atom drawn inside a ring
     * (a substituent pointing into a ring reads like a ring bond); for every pair of non-bonded atoms
     * closer than 0.8 bond lengths {@code 25 * (0.8 - d/L)} (L = median bond length); and for every atom
     * closer than 0.4 bond lengths to a bond it does not belong to {@code 25 * (0.4 - d/L)}. 0 = no
     * crossing and no crowding.
     *
     * <p>On top of that, {@value #HARD} for every <em>overlap</em>, a drawing that reads as a different
     * constitution: two non-bonded atoms closer than {@value #COINCIDENT_LIMIT} bond lengths (one label,
     * or one carbon with too many bonds), or an atom within {@value #ON_BOND_LIMIT} bond lengths of the
     * inner part of a bond it does not belong to (the bond reads as two bonds through that atom); twice
     * that for an overlap at the closer limits of {@link #overlaps(IAtomContainer)}. An overlap thus
     * always costs more than the crossings it might save.
     */
    public static double penalty(IAtomContainer m, Point2d[] p) {
        return new Scorer(m).penalty(p);
    }

    /** Topology needed by {@link #penalty}, computed once per molecule. */
    private static final class Scorer {
        final int n;
        final int[][] ends;
        final boolean[] multiple;
        final boolean[][] bonded;
        final int[][] rings;
        final boolean[] ringAtom;

        Scorer(IAtomContainer m) {
            n = m.getAtomCount();
            ends = Layout2D.ends(m);
            multiple = new boolean[ends.length];
            for (int x = 0; x < ends.length; x++) multiple[x] = m.getBond(x).getOrder() != IBond.Order.SINGLE;
            bonded = Layout2D.bonded(m);
            int[][] paths = Cycles.sssr(m).paths();
            rings = new int[paths.length][];
            ringAtom = new boolean[n];
            for (int r = 0; r < paths.length; r++) {
                rings[r] = Arrays.copyOf(paths[r], paths[r].length - 1);      // paths are closed
                for (int a : rings[r]) ringAtom[a] = true;
            }
        }

        double medianBond(Point2d[] p) {
            int nb = ends.length;
            double[] len = new double[nb];
            for (int x = 0; x < nb; x++) len[x] = p[ends[x][0]].distance(p[ends[x][1]]);
            Arrays.sort(len);
            return len[nb / 2];
        }

        /**
         * Number of overlaps ({@link #penalty}) at the limits {@code coincident} and {@code onBond}
         * (fractions of the median bond length; on-bond only where the foot of the perpendicular lies in
         * the inner {@code 1 - 2 * margin} of the bond).
         */
        int overlaps(Point2d[] p, double coincident, double onBond, double margin) {
            int nb = ends.length;
            if (nb == 0) return 0;
            double L = medianBond(p);
            if (L <= 0) return 0;
            int count = 0;
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    if (!bonded[i][j] && p[i].distance(p[j]) < coincident * L) count++;
            for (int i = 0; i < n; i++) {
                for (int x = 0; x < nb; x++) {
                    int a = ends[x][0], b = ends[x][1];
                    if (a == i || b == i) continue;
                    if (onBondDistance(p[i], p[a], p[b], margin) < onBond * L) count++;
                }
            }
            return count;
        }

        int overlaps(Point2d[] p) {
            return overlaps(p, COINCIDENT_LIMIT, ON_BOND_LIMIT, ON_BOND_MARGIN);
        }

        double penalty(Point2d[] p) {
            int nb = ends.length;
            if (nb == 0) return 0;
            double L = medianBond(p);
            if (L <= 0) return 0;
            double pen = 0;
            // one pass per term, with cheap rejects (refine evaluates this thousands of times)
            double soft2 = 0.64 * L * L, hard2 = COINCIDENT_LIMIT * COINCIDENT_LIMIT * L * L,
                    review2 = REVIEW_COINCIDENT * REVIEW_COINCIDENT * L * L;
            for (int i = 0; i < n; i++) {
                double xi = p[i].x, yi = p[i].y;
                for (int j = i + 1; j < n; j++) {
                    if (bonded[i][j]) continue;
                    double dx = p[j].x - xi, dy = p[j].y - yi, d2 = dx * dx + dy * dy;
                    if (d2 >= soft2) continue;
                    pen += 25 * (0.8 - Math.sqrt(d2) / L);
                    if (d2 < hard2) pen += HARD;
                    if (d2 < review2) pen += HARD;
                }
            }
            double[] minX = new double[nb], maxX = new double[nb], minY = new double[nb], maxY = new double[nb];
            for (int x = 0; x < nb; x++) {
                Point2d a = p[ends[x][0]], b = p[ends[x][1]];
                minX[x] = Math.min(a.x, b.x);
                maxX[x] = Math.max(a.x, b.x);
                minY[x] = Math.min(a.y, b.y);
                maxY[x] = Math.max(a.y, b.y);
            }
            for (int x = 0; x < nb; x++) {
                for (int y = x + 1; y < nb; y++) {
                    if (maxX[x] < minX[y] || maxX[y] < minX[x] || maxY[x] < minY[y] || maxY[y] < minY[x]) continue;
                    int a = ends[x][0], b = ends[x][1], c = ends[y][0], d = ends[y][1];
                    if (a == c || a == d || b == c || b == d) continue;
                    if (intersects(p[a], p[b], p[c], p[d])) pen += multiple[x] || multiple[y] ? 20 : 10;
                }
            }
            double reach = 0.4 * L;
            for (int i = 0; i < n; i++) {
                Point2d q = p[i];
                for (int x = 0; x < nb; x++) {
                    if (q.x < minX[x] - reach || q.x > maxX[x] + reach || q.y < minY[x] - reach || q.y > maxY[x] + reach) continue;
                    int a = ends[x][0], b = ends[x][1];
                    if (a == i || b == i) continue;
                    Point2d pa = p[a], pb = p[b];
                    double dx = pb.x - pa.x, dy = pb.y - pa.y, len2 = dx * dx + dy * dy;
                    double t = len2 == 0 ? 0 : ((q.x - pa.x) * dx + (q.y - pa.y) * dy) / len2;
                    double tc = Math.max(0, Math.min(1, t));
                    double fx = pa.x + tc * dx - q.x, fy = pa.y + tc * dy - q.y;
                    double d = Math.sqrt(fx * fx + fy * fy) / L;
                    if (d < 0.4) pen += 25 * (0.4 - d);
                    if (len2 == 0) continue;
                    // t inside the bond: d is the perpendicular distance
                    if (t > ON_BOND_MARGIN && t < 1 - ON_BOND_MARGIN && d < ON_BOND_LIMIT) pen += HARD;
                    if (t > REVIEW_MARGIN && t < 1 - REVIEW_MARGIN && d < REVIEW_ON_BOND) pen += HARD;
                }
            }
            for (int i = 0; i < n; i++) {
                if (ringAtom[i]) continue;
                for (int[] ring : rings) {
                    if (inside(p[i], ring, p)) {
                        pen += 15;
                        break;
                    }
                }
            }
            return pen;
        }
    }

    /**
     * Distance of {@code q} from the line through {@code a-b} where the foot of the perpendicular lies
     * in the inner part of the segment (between {@code margin} and {@code 1 - margin}); infinity
     * otherwise (near an end, it is the atom-atom distance that matters).
     */
    private static double onBondDistance(Point2d q, Point2d a, Point2d b, double margin) {
        double dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
        if (len2 == 0) return Double.POSITIVE_INFINITY;
        double t = ((q.x - a.x) * dx + (q.y - a.y) * dy) / len2;
        if (t <= margin || t >= 1 - margin) return Double.POSITIVE_INFINITY;
        double fx = a.x + t * dx - q.x, fy = a.y + t * dy - q.y;
        return Math.sqrt(fx * fx + fy * fy);
    }

    /** Distance of {@code q} from the segment {@code a-b}. */
    private static double segmentDistance(Point2d q, Point2d a, Point2d b) {
        double dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((q.x - a.x) * dx + (q.y - a.y) * dy) / len2));
        double fx = a.x + t * dx - q.x, fy = a.y + t * dy - q.y;
        return Math.sqrt(fx * fx + fy * fy);
    }

    /** Point in polygon (even-odd rule). */
    private static boolean inside(Point2d q, int[] ring, Point2d[] p) {
        boolean in = false;
        for (int k = 0, j = ring.length - 1; k < ring.length; j = k++) {
            Point2d a = p[ring[k]], b = p[ring[j]];
            if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x) in = !in;
        }
        return in;
    }

    /** {@link #penalty} of the coordinates stored in {@code m}'s atoms. */
    public static double penalty(IAtomContainer m) {
        Point2d[] p = new Point2d[m.getAtomCount()];
        for (int i = 0; i < p.length; i++) p[i] = m.getAtom(i).getPoint2d();
        return penalty(m, p);
    }

    /**
     * Number of overlaps in the stored coordinates, at the limits of the visual review: non-bonded atoms
     * closer than 0.2 bond lengths, and atoms closer than 0.12 bond lengths to the inner 84 % of a bond
     * they do not belong to. A layout from {@link #layout} has none, unless {@link #separate} could not
     * remove them.
     */
    public static int overlaps(IAtomContainer m) {
        Point2d[] p = new Point2d[m.getAtomCount()];
        for (int i = 0; i < p.length; i++) p[i] = m.getAtom(i).getPoint2d();
        return new Scorer(m).overlaps(p, REVIEW_COINCIDENT, REVIEW_ON_BOND, REVIEW_MARGIN);
    }

    /** Number of pairs of bonds without a common atom that cross (stored coordinates). */
    public static int crossings(IAtomContainer m) {
        int[][] ends = ends(m);
        int count = 0;
        for (int x = 0; x < ends.length; x++) {
            for (int y = x + 1; y < ends.length; y++) {
                int a = ends[x][0], b = ends[x][1], c = ends[y][0], d = ends[y][1];
                if (a == c || a == d || b == c || b == d) continue;
                if (intersects(m.getAtom(a).getPoint2d(), m.getAtom(b).getPoint2d(), m.getAtom(c).getPoint2d(),
                        m.getAtom(d).getPoint2d())) count++;
            }
        }
        return count;
    }

    /** Number of crossings that involve a double or triple bond (stored coordinates; tests). */
    static int multipleBondCrossings(IAtomContainer m) {
        Scorer sc = new Scorer(m);
        int count = 0;
        for (int x = 0; x < sc.ends.length; x++) {
            for (int y = x + 1; y < sc.ends.length; y++) {
                int a = sc.ends[x][0], b = sc.ends[x][1], c = sc.ends[y][0], d = sc.ends[y][1];
                if (a == c || a == d || b == c || b == d || !(sc.multiple[x] || sc.multiple[y])) continue;
                if (intersects(m.getAtom(a).getPoint2d(), m.getAtom(b).getPoint2d(), m.getAtom(c).getPoint2d(),
                        m.getAtom(d).getPoint2d())) count++;
            }
        }
        return count;
    }

    /** Number of non-ring atoms drawn inside a ring (stored coordinates; tests). */
    static int substituentsInsideRings(IAtomContainer m) {
        Scorer sc = new Scorer(m);
        Point2d[] p = new Point2d[m.getAtomCount()];
        for (int i = 0; i < p.length; i++) p[i] = m.getAtom(i).getPoint2d();
        int count = 0;
        for (int i = 0; i < p.length; i++) {
            if (sc.ringAtom[i]) continue;
            for (int[] ring : sc.rings) {
                if (inside(p[i], ring, p)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** Number of pairs of non-bonded atoms closer than {@code fraction} of the median bond length (stored coordinates). */
    public static int closeContacts(IAtomContainer m, double fraction) {
        Point2d[] p = new Point2d[m.getAtomCount()];
        for (int i = 0; i < p.length; i++) p[i] = m.getAtom(i).getPoint2d();
        double L = medianBondLength(m, p);
        boolean[][] bonded = bonded(m);
        int count = 0;
        for (int i = 0; i < p.length; i++)
            for (int j = i + 1; j < p.length; j++)
                if (!bonded[i][j] && p[i].distance(p[j]) < fraction * L) count++;
        return count;
    }

    private static double medianBondLength(IAtomContainer m, Point2d[] p) {
        int nb = m.getBondCount();
        if (nb == 0) return 1.5;
        double[] len = new double[nb];
        int[][] ends = ends(m);
        for (int x = 0; x < nb; x++) len[x] = p[ends[x][0]].distance(p[ends[x][1]]);
        Arrays.sort(len);
        return len[nb / 2];
    }

    private static int[][] ends(IAtomContainer m) {
        int[][] e = new int[m.getBondCount()][2];
        for (int x = 0; x < e.length; x++) {
            IBond b = m.getBond(x);
            e[x][0] = m.indexOf(b.getBegin());
            e[x][1] = m.indexOf(b.getEnd());
        }
        return e;
    }

    private static boolean[][] bonded(IAtomContainer m) {
        int n = m.getAtomCount();
        boolean[][] b = new boolean[n][n];
        for (int[] e : ends(m)) {
            b[e[0]][e[1]] = true;
            b[e[1]][e[0]] = true;
        }
        return b;
    }

    static boolean intersects(Point2d p1, Point2d p2, Point2d p3, Point2d p4) {
        double d1 = cross(p3, p4, p1), d2 = cross(p3, p4, p2), d3 = cross(p1, p2, p3), d4 = cross(p1, p2, p4);
        return ((d1 > 1e-9 && d2 < -1e-9) || (d1 < -1e-9 && d2 > 1e-9)) && ((d3 > 1e-9 && d4 < -1e-9) || (d3 < -1e-9 && d4 > 1e-9));
    }

    private static double cross(Point2d a, Point2d b, Point2d c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    // ------------------------------------------------------------------ refinement

    /**
     * Turns substituents about acyclic bonds while that lowers the {@link #penalty}: for every acyclic
     * bond, the side with fewer atoms is rotated about the other end in 15 degree steps, or mirrored
     * across the bond line; the best placement is kept. At most {@value #REFINE_PASSES} passes over all
     * bonds. Returns new coordinates ({@code p} is not changed).
     */
    static Point2d[] refine(IAtomContainer m, Point2d[] p) {
        Scorer scorer = new Scorer(m);
        Point2d[] cur = copy(p);
        double curPen = scorer.penalty(cur);
        if (curPen == 0) return cur;
        IAtomContainer rings;
        try {
            rings = m.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        Cycles.markRingAtomsAndBonds(rings);
        int n = m.getAtomCount();
        List<int[]> rotors = new ArrayList<>();          // {pivot, moving atom}
        List<boolean[]> sides = new ArrayList<>();
        for (int x = 0; x < rings.getBondCount(); x++) {
            IBond b = rings.getBond(x);
            if (b.isInRing()) continue;
            int u = rings.indexOf(b.getBegin()), v = rings.indexOf(b.getEnd());
            boolean[] sideV = side(rings, v, u);
            int count = 0;
            for (boolean s : sideV) if (s) count++;
            if (count > n - count) {
                sideV = side(rings, u, v);
                int t = u;
                u = v;
                v = t;
            }
            rotors.add(new int[] {u, v});
            sides.add(sideV);
        }
        for (int pass = 0; pass < REFINE_PASSES && curPen > 0; pass++) {
            boolean improved = false;
            for (int r = 0; r < rotors.size() && curPen > 0; r++) {
                int pivot = rotors.get(r)[0], moving = rotors.get(r)[1];
                boolean[] side = sides.get(r);
                Point2d[] bestP = null;
                double bestPen = curPen;
                for (int k = 1; k < 24; k++) {
                    Point2d[] q = rotate(cur, side, cur[pivot], k * STEP);
                    double pen = scorer.penalty(q);
                    if (pen < bestPen - 1e-9) {
                        bestPen = pen;
                        bestP = q;
                    }
                }
                Point2d[] q = mirror(cur, side, cur[pivot], cur[moving]);
                double pen = scorer.penalty(q);
                if (pen < bestPen - 1e-9) {
                    bestPen = pen;
                    bestP = q;
                }
                if (bestP != null) {
                    cur = bestP;
                    curPen = bestPen;
                    improved = true;
                }
            }
            if (!improved) break;
        }
        return cur;
    }

    /** Atoms reachable from {@code start} without passing {@code block}. */
    private static boolean[] side(IAtomContainer m, int start, int block) {
        boolean[] seen = new boolean[m.getAtomCount()];
        Deque<Integer> todo = new ArrayDeque<>();
        seen[start] = true;
        todo.add(start);
        while (!todo.isEmpty()) {
            IAtom a = m.getAtom(todo.poll());
            for (IAtom nb : m.getConnectedAtomsList(a)) {
                int j = m.indexOf(nb);
                if (j == block || seen[j]) continue;
                seen[j] = true;
                todo.add(j);
            }
        }
        return seen;
    }

    private static Point2d[] copy(Point2d[] p) {
        Point2d[] c = new Point2d[p.length];
        for (int i = 0; i < p.length; i++) c[i] = new Point2d(p[i]);
        return c;
    }

    private static Point2d[] rotate(Point2d[] p, boolean[] side, Point2d o, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        Point2d[] q = copy(p);
        for (int i = 0; i < p.length; i++) {
            if (!side[i]) continue;
            double dx = p[i].x - o.x, dy = p[i].y - o.y;
            q[i].x = o.x + cos * dx - sin * dy;
            q[i].y = o.y + sin * dx + cos * dy;
        }
        return q;
    }

    private static Point2d[] mirror(Point2d[] p, boolean[] side, Point2d a, Point2d b) {
        double dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
        Point2d[] q = copy(p);
        if (len2 == 0) return q;
        for (int i = 0; i < p.length; i++) {
            if (!side[i]) continue;
            double t = ((p[i].x - a.x) * dx + (p[i].y - a.y) * dy) / len2;
            double fx = a.x + t * dx, fy = a.y + t * dy;
            q[i].x = 2 * fx - p[i].x;
            q[i].y = 2 * fy - p[i].y;
        }
        return q;
    }

    // ------------------------------------------------------------------ separation

    /** Clearance {@link #separate} aims for: between non-bonded atoms, and of an atom from a foreign bond (bond lengths). */
    private static final double ATOM_CLEARANCE = 0.55, BOND_CLEARANCE = 0.35;
    private static final int SEPARATE_ITERATIONS = 600;
    static final int SEPARATE_ROUNDS = 3;

    /**
     * Pushes overlapping atoms apart (layout {@link #penalty}'s overlaps; e.g. two ring atoms of a
     * wrapped-around fused system drawn on the same spot, or a ring atom on a bridge bond): a short
     * deterministic relaxation in units of the median bond length L, minimising
     * <ul>
     *   <li>every bond's deviation from its length in {@code p} (bonds keep their lengths, rings their size),</li>
     *   <li>{@code 4 s (0.55 - d)^2} for non-bonded atoms closer than 0.55 L (s = {@code strength}),</li>
     *   <li>{@code 4 s (0.35 - d)^2} for an atom closer than 0.35 L to the inner part of a foreign bond
     *       (atom pushed off the bond, the bond's ends the other way),</li>
     *   <li>{@code 0.02 |q - p|^2}: every atom stays near its place, so the rest of the drawing does
     *       not change.</li>
     * </ul>
     * Atoms exactly on top of each other or on a bond are separated in a fixed direction (towards their
     * own neighbours). Returns new coordinates; {@code p} is not changed.
     */
    static Point2d[] separate(Scorer sc, Point2d[] p, int strength) {
        int n = p.length, nb = sc.ends.length;
        Point2d[] q = copy(p);
        if (nb == 0) return q;
        double L = sc.medianBond(p);
        if (L <= 0) return q;
        double[] x = new double[n], y = new double[n], x0 = new double[n], y0 = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = x0[i] = p[i].x / L;
            y[i] = y0[i] = p[i].y / L;
        }
        double[] rest = new double[nb];
        for (int b = 0; b < nb; b++) rest[b] = len(x[sc.ends[b][0]] - x[sc.ends[b][1]], y[sc.ends[b][0]] - y[sc.ends[b][1]]);
        // neighbour centroids: the fixed direction for exactly coincident atoms
        double[] cx = new double[n], cy = new double[n];
        int[] deg = new int[n];
        for (int[] e : sc.ends) {
            for (int k = 0; k < 2; k++) {
                int a = e[k], o = e[1 - k];
                cx[a] += x0[o];
                cy[a] += y0[o];
                deg[a]++;
            }
        }
        for (int i = 0; i < n; i++) {
            if (deg[i] > 0) {
                cx[i] /= deg[i];
                cy[i] /= deg[i];
            } else {
                cx[i] = x0[i];
                cy[i] = y0[i];
            }
        }
        final double kb = 1, kr = 4 * strength, ka = 4 * strength, kanchor = 0.02, eta = 0.1 / strength, maxMove = 0.05;
        double[] gx = new double[n], gy = new double[n];
        for (int it = 0; it < SEPARATE_ITERATIONS; it++) {
            Arrays.fill(gx, 0);
            Arrays.fill(gy, 0);
            boolean active = false;
            for (int b = 0; b < nb; b++) {
                int i = sc.ends[b][0], j = sc.ends[b][1];
                double dx = x[i] - x[j], dy = y[i] - y[j], d = len(dx, dy);
                if (d < 1e-9) continue;
                double f = 2 * kb * (d - rest[b]) / d;
                gx[i] += f * dx;
                gy[i] += f * dy;
                gx[j] -= f * dx;
                gy[j] -= f * dy;
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (sc.bonded[i][j]) continue;
                    double dx = x[i] - x[j], dy = y[i] - y[j], d2 = dx * dx + dy * dy;
                    if (d2 >= ATOM_CLEARANCE * ATOM_CLEARANCE) continue;
                    double d = Math.sqrt(d2);
                    active = true;
                    if (d < 1e-6) {                          // same spot: apart along their neighbours' directions
                        dx = cx[i] - cx[j];
                        dy = cy[i] - cy[j];
                        double l = len(dx, dy);
                        if (l < 1e-9) {
                            dx = 1;
                            dy = 0;
                            l = 1;
                        }
                        dx /= l;
                        dy /= l;
                        d = 0;
                    } else {
                        dx /= d;
                        dy /= d;
                    }
                    double f = 2 * kr * (ATOM_CLEARANCE - d);   // -dE/dd
                    gx[i] -= f * dx;
                    gy[i] -= f * dy;
                    gx[j] += f * dx;
                    gy[j] += f * dy;
                }
            }
            for (int i = 0; i < n; i++) {
                for (int b = 0; b < nb; b++) {
                    int a = sc.ends[b][0], c = sc.ends[b][1];
                    if (a == i || c == i) continue;
                    if (x[i] < Math.min(x[a], x[c]) - BOND_CLEARANCE || x[i] > Math.max(x[a], x[c]) + BOND_CLEARANCE
                            || y[i] < Math.min(y[a], y[c]) - BOND_CLEARANCE || y[i] > Math.max(y[a], y[c]) + BOND_CLEARANCE) continue;
                    double ux = x[c] - x[a], uy = y[c] - y[a], len2 = ux * ux + uy * uy;
                    if (len2 < 1e-12) continue;
                    double t = ((x[i] - x[a]) * ux + (y[i] - y[a]) * uy) / len2;
                    if (t <= 0.02 || t >= 0.98) continue;
                    double fx = x[i] - (x[a] + t * ux), fy = y[i] - (y[a] + t * uy), d = len(fx, fy);
                    if (d >= BOND_CLEARANCE) continue;
                    active = true;
                    double len = Math.sqrt(len2), nx = -uy / len, ny = ux / len;
                    if (d < 1e-6) {                          // on the line: to the side of its own neighbours
                        double side = (cx[i] - x[a]) * nx + (cy[i] - y[a]) * ny;
                        if (side < 0) {
                            nx = -nx;
                            ny = -ny;
                        }
                    } else {
                        nx = fx / d;
                        ny = fy / d;
                    }
                    double f = 2 * ka * (BOND_CLEARANCE - d);
                    gx[i] -= f * nx;
                    gy[i] -= f * ny;
                    gx[a] += f * nx * (1 - t);
                    gy[a] += f * ny * (1 - t);
                    gx[c] += f * nx * t;
                    gy[c] += f * ny * t;
                }
            }
            if (!active) break;
            double largest = 0;
            for (int i = 0; i < n; i++) {
                gx[i] += 2 * kanchor * (x[i] - x0[i]);
                gy[i] += 2 * kanchor * (y[i] - y0[i]);
                double mx = -eta * gx[i], my = -eta * gy[i], mv = len(mx, my);
                if (mv > maxMove) {
                    mx *= maxMove / mv;
                    my *= maxMove / mv;
                }
                largest = Math.max(largest, Math.min(mv, maxMove));
                x[i] += mx;
                y[i] += my;
            }
            if (largest < 1e-4) break;                      // settled (forces in balance)
        }
        for (int i = 0; i < n; i++) q[i] = new Point2d(x[i] * L, y[i] * L);
        return q;
    }

    private static double len(double dx, double dy) {
        return Math.sqrt(dx * dx + dy * dy);          // Math.hypot is exact but many times slower
    }
}
