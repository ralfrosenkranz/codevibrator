package de.ralfrosenkranz.codevibrator.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ResultLog {
    public Instant createdAt = Instant.now();
    public List<String> warnings = new ArrayList<>();
    public List<String> blocked = new ArrayList<>();
    public List<String> ignored = new ArrayList<>();
    public List<String> stats = new ArrayList<>();

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("CreatedAt: ").append(createdAt).append("\n\n");

        if (!stats.isEmpty()) {
            sb.append("Stats\n");
            for (String s : stats) sb.append("  - ").append(s).append("\n");
            sb.append("\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("Warnings\n");
            for (String s : warnings) sb.append("  - ").append(s).append("\n");
            sb.append("\n");
        }
        if (!blocked.isEmpty()) {
            sb.append("Blocked (readonly/exclude)\n");
            for (String s : blocked) sb.append("  - ").append(s).append("\n");
            sb.append("\n");
        }
        if (!ignored.isEmpty()) {
            sb.append("Ignored\n");
            for (String s : ignored) sb.append("  - ").append(s).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }
}
