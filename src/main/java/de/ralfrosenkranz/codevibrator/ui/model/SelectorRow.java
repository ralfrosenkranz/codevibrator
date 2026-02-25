package de.ralfrosenkranz.codevibrator.ui.model;

public class SelectorRow {
    public final String pattern;
    public boolean force;
    public boolean active;
    public final boolean inherited;

    public SelectorRow(String pattern, boolean force, boolean active, boolean inherited) {
        this.pattern = pattern;
        this.force = force;
        this.active = active;
        this.inherited = inherited;
    }
}
