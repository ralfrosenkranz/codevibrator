package de.ralfrosenkranz.codevibrator.selectors;

import de.ralfrosenkranz.codevibrator.config.DirectoryConfig;
import de.ralfrosenkranz.codevibrator.config.ProfileDirConfig;
import de.ralfrosenkranz.codevibrator.config.SelectorState;
import de.ralfrosenkranz.codevibrator.persist.ConfigService;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SelectorResolver {
    private final ConfigService config;

    public SelectorResolver(ConfigService config) {
        this.config = config;
    }

    public ResolvedDirRules resolveRules(Path dir, String profile) {
        // Walk from project root -> dir, accumulating inheritance
        Path root = config.projectRoot();
        Path rel = root.relativize(dir);
        Path cur = root;

        boolean excluded = false;
        boolean readonlyDir = false;

        // pattern -> inheritedActive
        Map<String, Boolean> inheritedActive = new LinkedHashMap<>();
        // readonly file patterns inherited
        List<String> readonlyPatterns = new ArrayList<>();

        for (int i = 0; i <= rel.getNameCount(); i++) {
            Path step = (i == 0) ? root : root.resolve(rel.subpath(0, i));
            DirectoryConfig dc = config.loadDirectoryConfig(step);
            ProfileDirConfig pc = dc.profiles.get(profile);
            if (pc == null) pc = new ProfileDirConfig();

            excluded = excluded || pc.excludeFromZip;
            readonlyDir = readonlyDir || pc.readonlyDir;

            readonlyPatterns.addAll(splitSemicolon(pc.readonlyFilePatterns));

            // Apply "selectorsText" as FORCE+ACTIVE at this level
            for (String p : splitSemicolon(pc.selectorsText)) {
                inheritedActive.put(p, true);
            }

            // Apply selectorStates overrides
            for (Map.Entry<String, SelectorState> e : pc.selectorStates.entrySet()) {
                String pat = e.getKey();
                SelectorState st = e.getValue();
                if (!inheritedActive.containsKey(pat)) {
                    // allow local-only selectors too
                    inheritedActive.put(pat, st.active);
                } else {
                    if (st.force) {
                        inheritedActive.put(pat, st.active);
                    }
                }
            }
        }

        List<EffectiveSelector> eff = inheritedActive.entrySet().stream()
                .map(e -> new EffectiveSelector(e.getKey(), isForcedAt(dir, profile, e.getKey()), e.getValue(), true))
                .collect(Collectors.toList());

        return new ResolvedDirRules(excluded, readonlyDir, eff, readonlyPatterns);
    }

    private boolean isForcedAt(Path dir, String profile, String pattern) {
        DirectoryConfig dc = config.loadDirectoryConfig(dir);
        ProfileDirConfig pc = dc.profiles.get(profile);
        if (pc == null) return false;
        // selectorsText forces on this level
        if (splitSemicolon(pc.selectorsText).contains(pattern)) return true;
        SelectorState st = pc.selectorStates.get(pattern);
        return st != null && st.force;
    }

    public static List<String> splitSemicolon(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(";"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
    }
}
