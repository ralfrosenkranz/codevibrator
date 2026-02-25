package de.ralfrosenkranz.codevibrator.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DirectoryConfig {
    /** profileName -> config */
    public Map<String, ProfileDirConfig> profiles = new HashMap<>();
}
