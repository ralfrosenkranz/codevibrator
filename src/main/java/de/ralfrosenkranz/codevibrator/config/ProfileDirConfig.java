package de.ralfrosenkranz.codevibrator.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileDirConfig {
    public boolean excludeFromZip = false;
    public boolean readonlyDir = false;

    /** Semicolon-separated glob list. Interpreted as FORCE+ACTIVE for this dir level. */
    public String selectorsText = "";

    /** Semicolon-separated glob list for readonly files at this level (inherits). */
    public String readonlyFilePatterns = "";

    /** Per-pattern override state for inherited/multiplied selectors */
    public Map<String, SelectorState> selectorStates = new HashMap<>();

    /** Per-file override at this directory level: filename -> includeInZip */
    public Map<String, Boolean> fileOverrides = new HashMap<>();
}
