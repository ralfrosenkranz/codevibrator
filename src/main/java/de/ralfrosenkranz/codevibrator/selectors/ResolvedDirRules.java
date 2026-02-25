package de.ralfrosenkranz.codevibrator.selectors;

import java.util.List;

public record ResolvedDirRules(
        boolean excluded,
        boolean readonlyDir,
        List<EffectiveSelector> selectors,
        List<String> readonlyFilePatterns
) {}
