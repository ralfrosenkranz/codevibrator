package de.ralfrosenkranz.codevibrator.selectors;

import java.util.ArrayList;
import java.util.List;

public final class FileDecider {
    private FileDecider() {}

    public static FileDecision decide(String fileName, ResolvedDirRules rules) {
        if (rules.excluded()) {
            return new FileDecision(false, false, rules.readonlyDir(), "Excluded directory");
        }

        boolean readonlyByPattern = matchesAny(rules.readonlyFilePatterns(), fileName);
        boolean blocked = rules.readonlyDir() || readonlyByPattern;

        List<Boolean> matchedActives = new ArrayList<>();
        for (EffectiveSelector s : rules.selectors()) {
            if (GlobMatcher.matches(s.pattern(), fileName)) {
                matchedActives.add(s.active());
            }
        }

        if (matchedActives.isEmpty()) {
            return new FileDecision(false, false, blocked, "No matching selectors");
        }

        boolean anyActive = matchedActives.stream().anyMatch(Boolean::booleanValue);
        boolean anyInactive = matchedActives.stream().anyMatch(b -> !b);
        boolean conflict = anyActive && anyInactive;

        // MVP required: OR-logic when conflict is accepted; here we always apply OR
        boolean inZip = anyActive;
        String reason = conflict ? "Conflict: OR applied" : (inZip ? "Included" : "Excluded by selectors");
        return new FileDecision(inZip, conflict, blocked, reason);
    }

    private static boolean matchesAny(List<String> patterns, String fileName) {
        for (String p : patterns) {
            if (GlobMatcher.matches(p, fileName)) return true;
        }
        return false;
    }
}
