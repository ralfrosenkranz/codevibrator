package de.ralfrosenkranz.codevibrator.persist;

import java.util.Map;
import java.util.Objects;

/**
 * Structured reason why a directory has local configuration that affects inheritance / selection.
 * Designed for programmatic use in UI marking.
 *
 * @param key     Unique key identifying the reason type (stable).
 * @param profile Optional profile name (e.g. "default").
 * @param count   Optional numeric value (e.g. count of overrides).
 * @param detail  Optional human readable details.
 * @param meta    Optional extra data for future use. Keep small; may be null.
 */
public record InheritanceOverrideReason(Key key, String profile, Integer count, String detail,
                                        Map<String, Object> meta) {

    public enum Key {READONLY_DIR, SELECTORS_TEXT, READONLY_FILE_PATTERNS, SELECTOR_STATES_OVERRIDDEN, FILE_OVERRIDES, UNREADABLE_CONFIG, EXCLUDE_FROM_ZIP}

    public InheritanceOverrideReason(Key key, String profile, Integer count, String detail, Map<String, Object> meta) {
        this.key = Objects.requireNonNull(key, "key");
        this.profile = profile;
        this.count = count;
        this.detail = detail;
        this.meta = meta;
    }

    public static InheritanceOverrideReason of(Key key, String profile) {
        return new InheritanceOverrideReason(key, profile, null, null, null);
    }

    public static InheritanceOverrideReason of(Key key, String profile, String detail) {
        return new InheritanceOverrideReason(key, profile, null, detail, null);
    }

    public static InheritanceOverrideReason ofCount(Key key, String profile, int count) {
        return new InheritanceOverrideReason(key, profile, count, null, null);
    }

    @Override
    public String toString() {
        return "InheritanceOverrideReason{" +
                "key='" + key + '\'' +
                ", profile='" + profile + '\'' +
                ", count=" + count +
                ", detail='" + detail + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InheritanceOverrideReason that)) return false;
        return key.equals(that.key) &&
                Objects.equals(profile, that.profile) &&
                Objects.equals(count, that.count) &&
                Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, profile, count, detail);
    }
}
