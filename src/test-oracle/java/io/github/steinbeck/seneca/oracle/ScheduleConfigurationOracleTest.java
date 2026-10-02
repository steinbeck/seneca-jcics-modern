package io.github.steinbeck.seneca.oracle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.JList;
import javax.swing.JTextField;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.ScheduleConfiguration;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schedule editor ({@link ScheduleConfiguration}, STRICT) against the 2001 Swing panel
 * {@code seneca.client.AnnealingScheduleConfigurator} driven headless: random sequences of selections,
 * field edits, "New", "Update" and "Remove". After every operation the outcome (exception class or
 * "ok"), the selected index, the four field texts and every schedule's values must be equal.
 */
@Tag("B")
class ScheduleConfigurationOracleTest {

    private static final String[] TEXTS = {"0.8", "0.5", " 0.9 ", "500", "500.9", "1e3", "-3", "NaN", "Infinity",
            "abc", "", "50.0", "0.95", "12", "1.0E10", "9007199254740993", "2.5e-1", "7"};

    /** The legacy panel and its package-private parts. */
    private static final class Legacy {
        final seneca.client.AnnealingScheduleConfigurator panel;
        final Vector<?> schedules;
        final JList<?> list;
        final JTextField acc, plat, cool, uphill;
        final AbstractAction update, remove, add;

        Legacy(int initial) throws Exception {
            seneca.core.SenecaDataset sd = new seneca.core.SenecaDataset("schedules");
            Field f = seneca.core.SenecaDataset.class.getField("annealingSchedules");
            @SuppressWarnings("unchecked")
            Vector<Object> v = (Vector<Object>) f.get(sd);
            for (int i = 0; i < initial; i++) {
                seneca.structgen.AnnealingSchedule a = new seneca.structgen.AnnealingSchedule();
                a.plateauSteps = 100 + i;
                a.maxUphillSteps = 10 + i;
                a.initialAcceptanceProbability = 0.5 + i / 10.0;
                a.asymptoticCoolingFactor = 0.9 - i / 100.0;
                v.addElement(a);
            }
            panel = new seneca.client.AnnealingScheduleConfigurator(sd);
            schedules = v;
            list = (JList<?>) field("schedules");
            acc = (JTextField) field("a_accProb");
            plat = (JTextField) field("a_platSteps");
            cool = (JTextField) field("a_coolFact");
            uphill = (JTextField) field("a_maxUphillSteps");
            update = action("updateAction");
            remove = action("removeAction");
            add = action("newAction");
        }

        Object field(String name) throws Exception {
            Field f = seneca.client.AnnealingScheduleConfigurator.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(panel);
        }

        AbstractAction action(String inner) throws Exception {
            Class<?> c = Class.forName("seneca.client.AnnealingScheduleConfigurator$" + inner);
            Constructor<?> k = c.getDeclaredConstructor(seneca.client.AnnealingScheduleConfigurator.class);
            k.setAccessible(true);
            return (AbstractAction) k.newInstance(panel);
        }

        String state() {
            StringBuilder sb = new StringBuilder();
            sb.append("sel=").append(list.getSelectedIndex()).append(" fields=[").append(acc.getText()).append('|')
                    .append(plat.getText()).append('|').append(cool.getText()).append('|').append(uphill.getText()).append("]");
            for (Object o : schedules) {
                seneca.structgen.AnnealingSchedule a = (seneca.structgen.AnnealingSchedule) o;
                sb.append(" (").append(a.initialAcceptanceProbability).append(',').append(a.plateauSteps).append(',')
                        .append(a.asymptoticCoolingFactor).append(',').append(a.maxUphillSteps).append(')');
            }
            return sb.toString();
        }
    }

    private static Port port(int initial) {
        SenecaDataset sd = new SenecaDataset("schedules");
        for (int i = 0; i < initial; i++) {
            sd.annealingSchedules.add(new AnnealingSchedule(0.5 + i / 10.0, 10 + i, 100 + i, 0.9 - i / 100.0));
        }
        return new Port(new ScheduleConfiguration(sd, LegacyCompat.STRICT));
    }

    private record Port(ScheduleConfiguration c) {
        String state() {
            StringBuilder sb = new StringBuilder();
            sb.append("sel=").append(c.selectedIndex()).append(" fields=[").append(c.acceptanceProbability()).append('|')
                    .append(c.plateauSteps()).append('|').append(c.coolingFactor()).append('|').append(c.maxUphillSteps()).append("]");
            for (AnnealingSchedule a : c.schedules()) {
                sb.append(" (").append(a.initialAcceptanceProbability).append(',').append(a.plateauSteps).append(',')
                        .append(a.asymptoticCoolingFactor).append(',').append(a.maxUphillSteps).append(')');
            }
            return sb.toString();
        }
    }

    private static String outcome(DatasetIoOracleTest.ThrowingRunnable r) {
        try {
            r.run();
            return "ok";
        } catch (Throwable t) {
            return t.getClass().getName();
        }
    }

    @Test
    void randomEditSequencesMatchTheLegacyPanel() throws Exception {
        Random rnd = new Random(20261002);
        int ops = 0;
        List<String> kinds = new ArrayList<>();
        for (int seq = 0; seq < 400; seq++) {
            int initial = rnd.nextInt(5);
            Legacy l = new Legacy(initial);
            Port p = port(initial);
            assertEquals(l.state(), p.state(), "seq " + seq + " opened");
            StringBuilder history = new StringBuilder();
            for (int step = 0; step < 25; step++) {
                int op = rnd.nextInt(8);
                String lo, po;
                switch (op) {
                    case 0, 1 -> {
                        int i = rnd.nextInt(6) - 1;
                        history.append(" select ").append(i);
                        lo = outcome(() -> l.list.setSelectedIndex(i));
                        po = outcome(() -> p.c().select(i));
                    }
                    case 2 -> {
                        int fld = rnd.nextInt(4);
                        String t = TEXTS[rnd.nextInt(TEXTS.length)];
                        history.append(" field").append(fld).append("='").append(t).append('\'');
                        JTextField[] lf = {l.acc, l.plat, l.cool, l.uphill};
                        lo = outcome(() -> lf[fld].setText(t));
                        po = outcome(() -> {
                            switch (fld) {
                                case 0 -> p.c().setAcceptanceProbability(t);
                                case 1 -> p.c().setPlateauSteps(t);
                                case 2 -> p.c().setCoolingFactor(t);
                                default -> p.c().setMaxUphillSteps(t);
                            }
                        });
                    }
                    case 3, 4 -> {
                        history.append(" update");
                        lo = outcome(() -> l.update.actionPerformed(null));
                        po = outcome(() -> p.c().update());
                    }
                    case 5, 6 -> {
                        history.append(" remove");
                        lo = outcome(() -> l.remove.actionPerformed(null));
                        po = outcome(() -> p.c().remove());
                    }
                    default -> {
                        history.append(" new");
                        lo = outcome(() -> l.add.actionPerformed(null));
                        po = outcome(() -> p.c().addNew());
                    }
                }
                ops++;
                if (!lo.equals("ok")) kinds.add(lo);
                assertEquals(lo, po, "seq " + seq + " outcome after" + history);
                assertEquals(l.state(), p.state(), "seq " + seq + " state after" + history);
            }
        }
        System.out.println("[oracle] schedule editor: " + ops + " operations, legacy exceptions " + kinds.stream().distinct().toList()
                + " (" + kinds.size() + ")");
        assertTrue(kinds.contains("java.lang.NumberFormatException"), "no unparsable field exercised");
        assertTrue(kinds.contains("java.lang.ArrayIndexOutOfBoundsException"), "no selection beyond the list exercised");
    }
}
