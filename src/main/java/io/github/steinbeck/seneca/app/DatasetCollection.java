package io.github.steinbeck.seneca.app;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * The open datasets and the "current" one (port of {@code SenecaDatasetCollection}, F-33): the current
 * dataset is shown in the status bar and is the target of "File &gt; Save". Adding a dataset makes it
 * current. Listeners are notified after every change of the collection, of the current dataset and of
 * any dataset (name, formula, ...). EDT only.
 *
 * <p>Differences to legacy (docs/port/gui-design.md, GUI deviations): {@link #remove} notifies after the
 * removal (legacy before it, so listeners saw the removed dataset) and keeps a valid current index; the
 * current dataset can be set by index (legacy only by name: of two datasets with the same name the first
 * was always chosen).
 */
public final class DatasetCollection {

    private final List<SenecaDataset> datasets = new ArrayList<>();
    private final Map<SenecaDataset, Runnable> datasetListeners = new IdentityHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int current = 0;

    /** Adds {@code sd} and makes it current. */
    public void add(SenecaDataset sd) {
        Runnable l = this::fireChange;
        datasetListeners.put(sd, l);
        sd.addChangeListener(l);
        datasets.add(sd);
        current = datasets.size() - 1;
        fireChange();
    }

    /** Removes {@code sd} (identity); the current dataset stays current if it remains. */
    public void remove(SenecaDataset sd) {
        int i = indexOf(sd);
        if (i < 0) return;
        SenecaDataset cur = current();
        datasets.remove(i);
        Runnable l = datasetListeners.remove(sd);
        if (l != null) sd.removeChangeListener(l);
        int c = cur == sd ? Math.min(i, datasets.size() - 1) : indexOf(cur);
        current = Math.max(0, c);
        fireChange();
    }

    /** Index of {@code sd} by identity, -1 if absent. */
    public int indexOf(SenecaDataset sd) {
        for (int f = 0; f < datasets.size(); f++) if (datasets.get(f) == sd) return f;
        return -1;
    }

    /** Makes dataset {@code index} current (no-op if out of range or unchanged). */
    public void setCurrent(int index) {
        if (index < 0 || index >= datasets.size() || index == current) return;
        current = index;
        fireChange();
    }

    /** Makes {@code sd} current. */
    public void setCurrent(SenecaDataset sd) {
        setCurrent(indexOf(sd));
    }

    /** The current dataset, null if none is open. */
    public SenecaDataset current() {
        return datasets.isEmpty() ? null : datasets.get(current);
    }

    /** The current index, -1 if none is open. */
    public int currentIndex() {
        return datasets.isEmpty() ? -1 : current;
    }

    public int size() {
        return datasets.size();
    }

    public SenecaDataset get(int index) {
        return datasets.get(index);
    }

    /** Copy of the list, in opening order. */
    public List<SenecaDataset> datasets() {
        return List.copyOf(datasets);
    }

    /** Names in opening order (Datasets &gt; switch menu). */
    public List<String> names() {
        List<String> l = new ArrayList<>();
        for (SenecaDataset sd : datasets) l.add(sd.getName());
        return l;
    }

    /** Adds a listener and calls it once at once (legacy "bring it up to date with current state"). */
    public void addChangeListener(Runnable l) {
        listeners.add(l);
        l.run();
    }

    public void removeChangeListener(Runnable l) {
        listeners.remove(l);
    }

    private void fireChange() {
        for (Runnable l : listeners) l.run();
    }
}
