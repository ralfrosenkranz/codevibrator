package de.ralfrosenkranz.codevibrator.zip;

import de.ralfrosenkranz.codevibrator.persist.ConfigService;
import de.ralfrosenkranz.codevibrator.selectors.FileDecider;
import de.ralfrosenkranz.codevibrator.selectors.FileDecision;
import de.ralfrosenkranz.codevibrator.selectors.ResolvedDirRules;
import de.ralfrosenkranz.codevibrator.selectors.SelectorResolver;
import de.ralfrosenkranz.codevibrator.selectors.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        Path zipPath = dailyDir.resolve("export_" + profile + "_" + timestamp + ".zip");
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
}
