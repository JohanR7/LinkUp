package com.linkup.app.ui.common;

/** A screen that can reload its data from SQLite, e.g. after a sync brought new rows. */
public interface Refreshable {
    void refresh();
}
