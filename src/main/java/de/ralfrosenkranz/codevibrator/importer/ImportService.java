package de.ralfrosenkranz.codevibrator.importer;

import de.ralfrosenkranz.codevibrator.logging.ResultLog;
import de.ralfrosenkranz.codevibrator.persist.ConfigService;
import de.ralfrosenkranz.codevibrator.selectors.FileDecider;
import de.ralfrosenkranz.codevibrator.selectors.FileDecision;
import de.ralfrosenkranz.codevibrator.selectors.ResolvedDirRules;
import de.ralfrosenkranz.codevibrator.selectors.SelectorResolver;
import de.ralfrosenkranz.codevibrator.selectors.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ImportService {
    private final ConfigService config;
    private final SelectorResolver resolver;
    private final TextClassifier textClassifier;

    public ImportService(ConfigService config) {
        this.config = config;
        this.resolver = new SelectorResolver(config);
        this.textClassifier = new TextClassifier(config);
    }

    public record Validation(boolean ok, String message) {}

    public Validation validateZipStructure(Path zipPath) {
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            List<String> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*")) {
                    return new Validation(false, "Fatal: absolute path entry: " + name);
                }
                if (name.contains("..")) {
                    return new Validation(false, "Fatal: traversal entry: " + name);
                }
                entries.add(name);
            }
            if (entries.isEmpty()) return new Validation(false, "Zip has no file entries.");

            // Additional root folder check (conservative):
            // if all entries share one top segment AND it equals project root dir name => fatal
            String rootName = config.projectRoot().getFileName().toString();
            Set<String> top = new HashSet<>();
            boolean anyHasSlash = false;
            for (String n : entries) {
                int slash = n.indexOf('/');
                if (slash > 0) {
                    anyHasSlash = true;
                    top.add(n.substring(0, slash));
                } else {
                    top.add(n);
                }
            }
            if (anyHasSlash && top.size() == 1 && top.iterator().next().equals(rootName)) {
                return new Validation(false, "Fatal: zip entries are under an extra root folder '" + rootName + "/...'");
            }
            return new Validation(true, "ok");
        } catch (IOException e) {
            return new Validation(false, "Cannot read zip: " + e.getMessage());
        }
    }

    public ImportPlan buildPlan(Path zipPath, String profile, ResultLog log) throws IOException {
        ImportPlan plan = new ImportPlan();

        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;

                String name = e.getName();
                Path rel = Path.of(name).normalize();
                Path target = config.projectRoot().resolve(rel).normalize();
                if (!target.startsWith(config.projectRoot())) {
                    throw new IOException("Fatal traversal detected: " + name);
                }

                // ignore executables (conservative heuristic)
                if (isExecutableName(target.getFileName().toString())) {
                    plan.items.add(new ImportPlan.Item(target, ImportPlan.Kind.IGNORED_EXECUTABLE, false, "Executable ignored"));
                    log.ignored.add("Executable ignored: " + rel);
                    continue;
                }

                // excluded/readonly rules are evaluated by target parent directory rules
                Path parent = target.getParent();
                if (parent == null) parent = config.projectRoot();
                ResolvedDirRules rules = resolver.resolveRules(parent, profile);
                FileDecision decision = FileDecider.decide(target.getFileName().toString(), rules);
                if (rules.excluded() || decision.blocked()) {
                    plan.items.add(new ImportPlan.Item(target, ImportPlan.Kind.BLOCKED, false, decision.reason()));
                    log.blocked.add("Blocked: " + rel + " (" + decision.reason() + ")");
                    continue;
                }

                boolean isText = textClassifier.isTextFile(target.getFileName().toString());
                if (!Files.exists(target)) {
                    plan.items.add(new ImportPlan.Item(target, ImportPlan.Kind.NEW, isText, "New"));
                    continue;
                }

                byte[] zipBytes = readZipBytes(zf, e);
                byte[] oldBytes = Files.readAllBytes(target);
                boolean same;
                if (isText) {
                    same = textEqualNormalized(zipBytes, oldBytes);
                } else {
                    same = Arrays.equals(sha256(zipBytes), sha256(oldBytes));
                }
                if (!same && isText) {
                    plan.items.add(new ImportPlan.Item(target, ImportPlan.Kind.CHANGED, true, "Changed", oldBytes, zipBytes));
                } else {
                    plan.items.add(new ImportPlan.Item(target, same ? ImportPlan.Kind.IDENTICAL : ImportPlan.Kind.CHANGED, isText, same ? "Identical" : "Changed"));
                }
            }
        }
        return plan;
    }

    public void apply(Path zipPath, ImportPlan plan, ResultLog log) throws IOException {
        // No deletions, only create/overwrite for NEW/CHANGED
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            for (ImportPlan.Item it : plan.items) {
                if (it.kind == ImportPlan.Kind.NEW || it.kind == ImportPlan.Kind.CHANGED) {
                    Path rel = config.projectRoot().relativize(it.target);
                    ZipEntry e = zf.getEntry(rel.toString().replace("\\","/"));
                    if (e == null) {
                        // fallback: try direct lookup by zip name (case)
                        e = zf.getEntry(rel.toString().replace("\\","/"));
                    }
                    if (e == null) {
                        log.warnings.add("Entry missing in zip during apply: " + rel);
                        continue;
                    }
                    Files.createDirectories(it.target.getParent());
                    try (InputStream in = zf.getInputStream(e)) {
                        Files.copy(in, it.target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static byte[] readZipBytes(ZipFile zf, ZipEntry e) throws IOException {
        try (InputStream in = zf.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    private static boolean textEqualNormalized(byte[] a, byte[] b) {
        String sa = decodeLenient(a);
        String sb = decodeLenient(b);
        if (sa == null || sb == null) {
            // cannot confirm text decoding => compare bytes
            return Arrays.equals(a, b);
        }
        return WhitespaceNormalizer.normalize(sa).equals(WhitespaceNormalizer.normalize(sb));
    }

    private static String decodeLenient(byte[] bytes) {
        // Conservative: try UTF-8 strictly; if fails => cannot confirm
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (Exception e) {
            return bytes;
        }
    }

    private static boolean isExecutableName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".exe") || n.endsWith(".bat") || n.endsWith(".cmd") || n.endsWith(".sh");
    }
}
