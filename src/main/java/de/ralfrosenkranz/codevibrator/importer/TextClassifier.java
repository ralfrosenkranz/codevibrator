package de.ralfrosenkranz.codevibrator.importer;

import de.ralfrosenkranz.codevibrator.config.HomeDefaults;
import de.ralfrosenkranz.codevibrator.persist.ConfigService;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class TextClassifier {
    private final Set<String> extensions;
    private final Set<String> exactNames;

    public TextClassifier(ConfigService config) {
        HomeDefaults hd = config.homeDefaults();
        this.extensions = split(hd.textFileExtensions).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        this.exactNames = split(hd.textFileExactNames).stream()
                .collect(Collectors.toSet());
    }

    public boolean isTextFile(String fileName) {
        if (exactNames.contains(fileName)) return true;
        int idx = fileName.lastIndexOf('.');
        if (idx < 0) return false;
        String ext = fileName.substring(idx).toLowerCase(Locale.ROOT);
        return extensions.contains(ext);
    }

    private static Set<String> split(String s) {
        if (s == null || s.isBlank()) return Set.of();
        return java.util.Arrays.stream(s.split(";"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }
}
