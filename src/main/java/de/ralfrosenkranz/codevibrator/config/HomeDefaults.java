package de.ralfrosenkranz.codevibrator.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeDefaults {
    public String textFileExtensions;   // semicolon separated, with leading dots
    public String textFileExactNames;   // semicolon separated exact names
}
