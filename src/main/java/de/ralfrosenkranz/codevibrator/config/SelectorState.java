package de.ralfrosenkranz.codevibrator.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectorState {
    public boolean force;
    public boolean active;

    public SelectorState() { }
    public SelectorState(boolean force, boolean active) {
        this.force = force;
        this.active = active;
    }
}
