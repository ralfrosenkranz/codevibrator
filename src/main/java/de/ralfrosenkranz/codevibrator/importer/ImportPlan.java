package de.ralfrosenkranz.codevibrator.importer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ImportPlan {
    public enum Kind { NEW, CHANGED, IDENTICAL, BLOCKED, IGNORED_EXECUTABLE }

    public static class Item {
        public final byte[] oldBytes; // only for CHANGED text (MVP)
        public final byte[] newBytes;
        public final Path target;
        public final Kind kind;
        public final boolean text;
        public final String note;

        public Item(Path target, Kind kind, boolean text, String note) {
            this(target, kind, text, note, null, null);
        }

        public Item(Path target, Kind kind, boolean text, String note, byte[] oldBytes, byte[] newBytes) {
            this.target = target;
            this.kind = kind;
            this.text = text;
            this.note = note;
            this.oldBytes = oldBytes;
            this.newBytes = newBytes;
        }
    }

    public final List<Item> items = new ArrayList<>();
}
