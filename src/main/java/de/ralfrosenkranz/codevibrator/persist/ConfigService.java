package de.ralfrosenkranz.codevibrator.persist;

import de.ralfrosenkranz.codevibrator.config.DirectoryConfig;
import de.ralfrosenkranz.codevibrator.config.HomeDefaults;
import de.ralfrosenkranz.codevibrator.config.ProfileDirConfig;
import de.ralfrosenkranz.codevibrator.config.ProjectConfig;
import de.ralfrosenkranz.codevibrator.config.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import de.ralfrosenkranz.codevibrator.persist.InheritanceOverrideReason;
import de.ralfrosenkranz.codevibrator.ui.LafSupport;

public class ConfigService {
    private final Path projectRoot;
    private final Path homeFile;
    private final Path projectFile;

    private HomeDefaults homeDefaults;
    private ProjectConfig projectConfig;

    public ConfigService(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.homeFile = Path.of(System.getProperty("user.home")).resolve(".code.vibrator");
        this.projectFile = projectRoot.resolve(".code.vibrator");
    }

    public Path projectRoot() { return projectRoot; }

    public synchronized void ensureHomeDefaults() {
        try {
            if (Files.exists(homeFile)) {
                homeDefaults = JsonIO.read(homeFile, HomeDefaults.class);
            } else {
                homeDefaults = new HomeDefaults();
            }

            boolean changed = false;
            if (homeDefaults.textFileExtensions == null || homeDefaults.textFileExtensions.trim().isEmpty()) {
                homeDefaults.textFileExtensions = String.join(";", List.of(
                        ".java",".kt",".kts",".groovy",".xml",".yml",".yaml",".json",".md",".txt",".properties",".gradle",
                        ".sql",".sh",".bat",".cmd",".ps1",".ts",".tsx",".js",".jsx",".css",".scss",".html",".htm",
                        ".toml",".ini",".csv",".gitignore",".gitattributes",".editorconfig"
                ));
                changed = true;
            }
            if (homeDefaults.textFileExactNames == null || homeDefaults.textFileExactNames.trim().isEmpty()) {
                homeDefaults.textFileExactNames = String.join(";", List.of(
                        "Makefile","Dockerfile","LICENSE","README","README.md","gradlew","gradlew.bat","mvnw","mvnw.cmd"
                ));
                changed = true;
            }
            if (changed) {
                JsonIO.write(homeFile, homeDefaults);
            }
        } catch (IOException e) {
            // fallback: still set defaults in-memory
            homeDefaults = new HomeDefaults();
            homeDefaults.textFileExtensions = ".java;.xml;.json;.md;.txt;.properties";
            homeDefaults.textFileExactNames = "Makefile;Dockerfile;README.md";
        }
    }

    public synchronized HomeDefaults homeDefaults() {
        if (homeDefaults == null) ensureHomeDefaults();
        return homeDefaults;
    }

    public synchronized ProjectConfig loadProjectConfig() {
        if (projectConfig != null) return projectConfig;
        try {
            if (Files.exists(projectFile)) {
                projectConfig = JsonIO.read(projectFile, ProjectConfig.class);
            } else {
                projectConfig = new ProjectConfig();
                saveProjectConfig(projectConfig);
            }
        } catch (IOException e) {
            projectConfig = new ProjectConfig();
        }
        if (projectConfig.activeProfile == null || projectConfig.activeProfile.isBlank()) {
            projectConfig.activeProfile = LafSupport.LAF_SYSTEM;
        }
        return projectConfig;
    }

    public synchronized void saveProjectConfig(ProjectConfig cfg) {
        try {
            JsonIO.write(projectFile, cfg);
            projectConfig = cfg;
        } catch (IOException ignored) { }
    }

    public synchronized DirectoryConfig loadDirectoryConfig(Path dir) {
        Path file = dir.resolve(".code.vibrator");
        try {
            if (Files.exists(file)) {
                return JsonIO.read(file, DirectoryConfig.class);
            }
        } catch (IOException ignored) { }
        return new DirectoryConfig();
    }

    public synchronized void saveDirectoryConfig(Path dir, DirectoryConfig cfg) {
        Path file = dir.resolve(".code.vibrator");
        try {
            // optional file: if effectively empty, we delete it (conservative cleanup)
            boolean empty = (cfg == null) || (cfg.profiles == null) || cfg.profiles.isEmpty();
            if (empty) {
                Files.deleteIfExists(file);
                return;
            }
            JsonIO.write(file, cfg);
        } catch (IOException ignored) { }
    }

    public synchronized ProfileDirConfig getOrCreateProfileDirConfig(Path dir, String profile) {
        DirectoryConfig dc = loadDirectoryConfig(dir);
        ProfileDirConfig pc = dc.profiles.get(profile);
        if (pc == null) {
            pc = new ProfileDirConfig();
            dc.profiles.put(profile, pc);
            saveDirectoryConfig(dir, dc);
        }
        return pc;
    }

    public synchronized void updateProfileDirConfig(Path dir, String profile, java.util.function.UnaryOperator<ProfileDirConfig> mutator) {
        DirectoryConfig dc = loadDirectoryConfig(dir);
        ProfileDirConfig pc = dc.profiles.getOrDefault(profile, new ProfileDirConfig());
        ProfileDirConfig updated = mutator.apply(pc);
        dc.profiles.put(profile, updated);
        saveDirectoryConfig(dir, dc);
    }



/**
 * Returns true if this directory has a local .code.vibrator file that contains
 * any configuration that can affect inheritance / file selection (exclude/readonly/selectors/overrides).
 *
 * Note: project root .code.vibrator is also used for project-wide settings; this method only considers
 * DirectoryConfig stored in per-directory .code.vibrator files.
 */
public List<InheritanceOverrideReason> hasInheritanceOverrides(Path dir) {
        Path file = dir.resolve(".code.vibrator");
        if (!Files.exists(file)) return null;

        List<InheritanceOverrideReason> reasons = new ArrayList<>();
        try {
            DirectoryConfig dc = JsonIO.read(file, DirectoryConfig.class);
            if (dc == null || dc.profiles == null || dc.profiles.isEmpty()) return null;

            for (Map.Entry<String, ProfileDirConfig> e : dc.profiles.entrySet()) {
                String profile = e.getKey();
                ProfileDirConfig pc = e.getValue();
                if (pc == null) continue;

                if (pc.excludeFromZip) reasons.add(InheritanceOverrideReason.of(InheritanceOverrideReason.Key.EXCLUDE_FROM_ZIP, profile));
                if (pc.readonlyDir) reasons.add(InheritanceOverrideReason.of(InheritanceOverrideReason.Key.READONLY_DIR, profile));

                if (pc.selectorsText != null && !pc.selectorsText.trim().isEmpty())
                    reasons.add(InheritanceOverrideReason.of(InheritanceOverrideReason.Key.SELECTORS_TEXT, profile));

                if (pc.readonlyFilePatterns != null && !pc.readonlyFilePatterns.trim().isEmpty())
                    reasons.add(InheritanceOverrideReason.of(InheritanceOverrideReason.Key.READONLY_FILE_PATTERNS, profile));

                if (pc.selectorStates != null && !pc.selectorStates.isEmpty())
                    reasons.add(InheritanceOverrideReason.ofCount(InheritanceOverrideReason.Key.SELECTOR_STATES_OVERRIDDEN, profile, pc.selectorStates.size()));

                if (pc.fileOverrides != null && !pc.fileOverrides.isEmpty())
                    reasons.add(InheritanceOverrideReason.ofCount(InheritanceOverrideReason.Key.FILE_OVERRIDES, profile, pc.fileOverrides.size()));
            }
        } catch (IOException ex) {
            // If file exists but is unreadable, treat as highlight-worthy (conservative)
            reasons.add(InheritanceOverrideReason.of(InheritanceOverrideReason.Key.UNREADABLE_CONFIG, null, ex.getClass().getSimpleName()));
        }

        return reasons.isEmpty() ? null : Collections.unmodifiableList(reasons);
    }
}
