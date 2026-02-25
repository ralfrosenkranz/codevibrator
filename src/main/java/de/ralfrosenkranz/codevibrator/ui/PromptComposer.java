package de.ralfrosenkranz.codevibrator.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ralfrosenkranz.codevibrator.config.ProjectConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PromptComposer {
    private static final ObjectMapper M = new ObjectMapper();

    private PromptComposer() {}

    public static String compose(ProjectConfig cfg, String profile, String zipName) {
        Map<String,Object> ctx = new LinkedHashMap<>();
        ctx.put("activeProfile", profile);
        ctx.put("zipFile", zipName);

        StringBuilder sb = new StringBuilder();
        sb.append(cfg.promptBase == null ? "" : cfg.promptBase.trim()).append("\n\n");

        if (cfg.promptAddOns != null && !cfg.promptAddOns.isEmpty()) {
            sb.append("AddOns:\n");
            for (String a : cfg.promptAddOns) {
                if (a != null && !a.isBlank()) sb.append("- ").append(a.trim()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Context(JSON):\n");
        try {
            sb.append(M.writerWithDefaultPrettyPrinter().writeValueAsString(ctx));
        } catch (Exception e) {
            //sb.append("{"activeProfile":"").append(profile).append("","zipFile":"").append(zipName).append(""}");
            sb.append("{\"activeProfile\":\"").append(profile).append("\",\"zipFile\":\"").append(zipName).append("\"}");
        }
        return sb.toString();
    }

    public static String deriveCommitMessage(String promptBase) {
        if (promptBase == null || promptBase.isBlank()) return "CodeVibrator update";
        String s = promptBase.strip();
        s = s.replaceAll("\\s+", " ");
        if (s.length() > 72) s = s.substring(0, 72);
        return s;
    }
}
