package io.github.steinbeck.seneca.app.dataset;

import io.github.steinbeck.seneca.app.AppContext;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunController;

/**
 * What every tab of a dataset window gets (docs/port/gui-design.md, "State"): the application, the
 * dataset (the one mutable model the tabs edit in place, as in 2001), the window, and the window's
 * {@link RunController} (legacy: each dataset window's "Structure Generation" tab had its own server list).
 */
public record DatasetContext(AppContext app, SenecaDataset dataset, DatasetFrame frame, RunController runs) {
}
