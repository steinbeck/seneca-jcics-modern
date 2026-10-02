package io.github.steinbeck.seneca.config;

import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Non-Swing core of the "Annealing Schedules" editor (policy point 5; feature-inventory F-43): the
 * schedule list's selection, the four text fields and the buttons "New", "-> Update ->" and "Remove".
 * Port of {@code legacy/original/src/seneca/client/AnnealingScheduleConfigurator.java}; the GUI binds a
 * list and four text fields to this object and calls its methods.
 *
 * <p>Kept from legacy in both modes, because it decides the schedule values and so the search:
 * <ul>
 *   <li>Field texts: {@code Double.toString} of the acceptance probability and the cooling factor,
 *       {@code Long.toString} of the plateau steps, and {@code Double.toString((double) maxUphillSteps)}
 *       for the uphill steps (so 50 shows as {@code "50.0"}).</li>
 *   <li>Parsing ("Update"): every field with {@code Double.parseDouble} (surrounding whitespace allowed,
 *       exponents such as {@code "1e3"} accepted); plateau and uphill steps are then cast to {@code long}
 *       ({@code "500.9"} gives 500, {@code "NaN"} gives 0).</li>
 *   <li>Selection as the legacy {@code JList}: selecting an index outside the list is ignored, and a
 *       selection change refreshes the fields from the selected schedule; "New" appends a default
 *       schedule and selects it.</li>
 * </ul>
 * Switch points (DEVIATIONS.md DV-67):
 * <ul>
 *   <li>"Remove": legacy removes the selected schedule i, then (for i &gt; 0) moves the selection to i-1
 *       and again to i-2 (ignored when that is -1) and writes the fields back into that schedule (they
 *       show its own values, so this changes nothing except values beyond 2^53). For i = 0 the selection
 *       stays at index 0, which is now the next schedule (or nothing), while the fields still show the
 *       removed schedule: a following "Update" copies the removed values into the next schedule, or
 *       throws {@link ArrayIndexOutOfBoundsException} on an empty list. STRICT reproduces all of this;
 *       DEFAULT selects the schedule that took the removed one's place (the new last one if the last
 *       was removed, none if the list is empty), refreshes the fields from it and writes nothing.</li>
 *   <li>"Update": legacy assigns the four fields one after another, so an unparsable field leaves the
 *       fields before it applied ({@link NumberFormatException}); with a selection beyond the list it
 *       throws {@link ArrayIndexOutOfBoundsException}. STRICT reproduces this; DEFAULT parses all four
 *       first and changes nothing if one fails (the exception still reaches the caller), and ignores
 *       "Update" without a valid selection.</li>
 * </ul>
 * The schedule list is the dataset's own {@code sd.annealingSchedules} (edited in place, as legacy).
 */
public final class ScheduleConfiguration {

    private final List<AnnealingSchedule> schedules;
    private final LegacyCompat compat;
    private int selected = -1;
    private String acceptanceProbability = "";
    private String plateauSteps = "";
    private String coolingFactor = "";
    private String maxUphillSteps = "";

    /** Opens the editor on the dataset's schedules; the last schedule is selected, as legacy. */
    public ScheduleConfiguration(SenecaDataset sd, LegacyCompat compat) {
        this.schedules = sd.annealingSchedules;
        this.compat = compat;
        if (!schedules.isEmpty()) select(schedules.size() - 1);
    }

    public List<AnnealingSchedule> schedules() {
        return schedules;
    }

    /** The selected list index, -1 if none; in STRICT it can lie beyond the list after "Remove". */
    public int selectedIndex() {
        return selected;
    }

    /**
     * Selects a list entry ({@code JList.setSelectedIndex}): an index outside the list is ignored; a
     * change of the selection refreshes the four fields from that schedule.
     */
    public void select(int index) {
        if (index < 0 || index >= schedules.size()) return;
        if (index == selected) return;
        selected = index;
        show(schedules.get(index));
    }

    /** "New": appends a default schedule and selects it. */
    public void addNew() {
        schedules.add(new AnnealingSchedule());
        selected = -1;                       // setListData clears the selection
        select(schedules.size() - 1);
    }

    /** "-> Update ->": the four fields into the selected schedule (see the class comment for the modes). */
    public void update() {
        if (selected < 0) return;
        if (compat.strict()) {
            if (selected >= schedules.size())
                throw new ArrayIndexOutOfBoundsException(selected + " >= " + schedules.size());   // Vector.elementAt
            writeFields(schedules.get(selected));
            return;
        }
        if (selected >= schedules.size()) return;
        double p0 = Double.parseDouble(acceptanceProbability);
        long plateau = (long) Double.parseDouble(plateauSteps);
        double cooling = Double.parseDouble(coolingFactor);
        long uphill = (long) Double.parseDouble(maxUphillSteps);
        AnnealingSchedule as = schedules.get(selected);
        as.initialAcceptanceProbability = p0;
        as.plateauSteps = plateau;
        as.asymptoticCoolingFactor = cooling;
        as.maxUphillSteps = uphill;
    }

    /** "Remove <-": removes the selected schedule (see the class comment for the modes). */
    public void remove() {
        if (selected < 0) return;
        if (compat.strict()) {
            int i = selected;
            if (i >= schedules.size())       // Vector.removeElementAt on a selection beyond the list
                throw new ArrayIndexOutOfBoundsException(i + " >= " + schedules.size());
            schedules.remove(i);
            if (i > 0) {
                select(i - 1);
                select(selected - 1);
                writeFields(schedules.get(selected));
            }
            return;
        }
        if (selected >= schedules.size()) return;
        int i = selected;
        schedules.remove(i);
        selected = -1;
        if (schedules.isEmpty()) {
            acceptanceProbability = plateauSteps = coolingFactor = maxUphillSteps = "";
        } else {
            select(Math.min(i, schedules.size() - 1));
        }
    }

    /** Legacy {@code updateAction}: assigns field by field (an unparsable field stops after the earlier ones). */
    private void writeFields(AnnealingSchedule as) {
        as.initialAcceptanceProbability = Double.parseDouble(acceptanceProbability);
        as.plateauSteps = (long) Double.parseDouble(plateauSteps);
        as.asymptoticCoolingFactor = Double.parseDouble(coolingFactor);
        as.maxUphillSteps = (long) Double.parseDouble(maxUphillSteps);
    }

    /** Legacy {@code valueChanged}: the field texts of a schedule. */
    private void show(AnnealingSchedule as) {
        acceptanceProbability = Double.toString(as.initialAcceptanceProbability);
        plateauSteps = Long.toString(as.plateauSteps);
        coolingFactor = Double.toString(as.asymptoticCoolingFactor);
        maxUphillSteps = Double.toString((double) as.maxUphillSteps);
    }

    public String acceptanceProbability() {
        return acceptanceProbability;
    }

    public String plateauSteps() {
        return plateauSteps;
    }

    public String coolingFactor() {
        return coolingFactor;
    }

    public String maxUphillSteps() {
        return maxUphillSteps;
    }

    /** Text field "Initial Accept. Prob.:" edited by the user. */
    public void setAcceptanceProbability(String text) {
        acceptanceProbability = text;
    }

    /** Text field "Plateau Steps:" edited by the user. */
    public void setPlateauSteps(String text) {
        plateauSteps = text;
    }

    /** Text field "Cooling Factor:" edited by the user. */
    public void setCoolingFactor(String text) {
        coolingFactor = text;
    }

    /** Text field "max. Uphill Steps:" edited by the user. */
    public void setMaxUphillSteps(String text) {
        maxUphillSteps = text;
    }
}
