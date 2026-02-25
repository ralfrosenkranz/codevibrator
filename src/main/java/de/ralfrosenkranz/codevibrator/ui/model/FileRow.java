package de.ralfrosenkranz.codevibrator.ui.model;

import de.ralfrosenkranz.codevibrator.selectors.FileDecision;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

public class FileRow {
    public final Path path;
    public final String name;
    public final long size;
    public final Instant modified;

    public final FileDecision decision;

    /** current include state shown in checkbox (can be overridden per directory level) */
    public boolean includeInZip;

    /** Tooltip text (e.g. decision reason / conflict / blocked). */
    public final String tooltip;

    public FileRow(Path path, BasicFileAttributes attrs, FileDecision decision) {
        this.path = path;
        this.name = path.getFileName().toString();
        this.size = attrs.size();
        this.modified = attrs.lastModifiedTime().toInstant();
        this.decision = decision;
        this.includeInZip = decision.inZip();
        this.tooltip = decision.reason();
    }
}
