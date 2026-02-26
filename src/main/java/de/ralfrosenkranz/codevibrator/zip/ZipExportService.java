package de.ralfrosenkranz.codevibrator.zip;

import de.ralfrosenkranz.codevibrator.persist.ConfigService;
import de.ralfrosenkranz.codevibrator.selectors.FileDecider;
import de.ralfrosenkranz.codevibrator.selectors.FileDecision;
import de.ralfrosenkranz.codevibrator.selectors.ResolvedDirRules;
import de.ralfrosenkranz.codevibrator.selectors.SelectorResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipExportService {
    private final ConfigService config;
    private final SelectorResolver resolver;

    public ZipExportService(ConfigService config) {
        this.config = config;
        this.resolver = new SelectorResolver(config);
    }

    public Path ensureDailyDir() throws IOException {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
        Path daily = config.projectRoot().resolve(".code.vibrator." + day);
        Files.createDirectories(daily);
        return daily;
    }

    public Path exportZip(Path dailyDir, String timestamp, String profile) throws IOException {
        int snapshotNo = nextSnapshotNumber(dailyDir);
        String no2 = String.format("%02d", snapshotNo);
        Path zipPath = dailyDir.resolve("snapshot_" + timestamp + "_" + profile + "_" + no2 + ".zip");
        try (OutputStream os = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            Path root = config.projectRoot();
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals(".code.vibrator"))
                    .filter(p -> !p.toString().contains(FileSystems.getDefault().getSeparator() + ".git" + FileSystems.getDefault().getSeparator()))
                    .forEach(p -> {
                        try {
                            Path parent = p.getParent();
                            if (parent == null) return;
                            ResolvedDirRules rules = resolver.resolveRules(parent, profile);
                            FileDecision d = FileDecider.decide(p.getFileName().toString(), rules);

                            // per-file override at this directory level
                            try {
                                var dcLocal = config.loadDirectoryConfig(parent);
                                var pcLocal = dcLocal.profiles.get(profile);
                                if (pcLocal != null && pcLocal.fileOverrides != null) {
                                    Boolean ov = pcLocal.fileOverrides.get(p.getFileName().toString());
                                    if (ov != null) {
                                        d = new FileDecision(ov, d.conflict(), d.blocked(), d.reason() + " (file override)");
                                    }
                                }
                            } catch (Exception ignored) { }

                            if (!d.inZip()) return;

                            Path rel = root.relativize(p);
                            String entryName = rel.toString().replace("\\", "/");
                            ZipEntry e = new ZipEntry(entryName);
                            zos.putNextEntry(e);
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        return zipPath;
    }

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile(
                    "^snapshot_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_[^_]+_(\\d{2})(?:[^/]*)?\\.zip$",
                    Pattern.CASE_INSENSITIVE
            );
    /**
     * Snapshot numbering is per day (per dailyDir) and shared across profiles.
     * Starts at 01 and increments by scanning existing snapshot zip files.
     */
    private int nextSnapshotNumber(Path dailyDir) {
        int max = 0;

        // The next number is derived from the files already present in the daily directory.
        // This is intentionally robust, so that if another process (e.g. ChatGPT result zips)
        // already created snapshot zip files with higher numbers, numbering continues correctly.
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dailyDir, "*.zip")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (!name.toLowerCase().startsWith("snapshot_")) continue;

                Matcher m = NUMBER_PATTERN.matcher(name);
                if (!m.matches()) continue;

                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) {
            // If scanning fails, fall back to 1 (still produces a valid name).
            return 1;
        }

        return max + 1;
    }
}
