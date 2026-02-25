package de.ralfrosenkranz.codevibrator.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class GitService {

    public record GitResult(boolean ok, String message) {}

    public GitResult isRepo(Path dir) {
        try {
            Process p = new ProcessBuilder(List.of("git","rev-parse","--is-inside-work-tree"))
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code == 0 && out.toLowerCase().contains("true")) return new GitResult(true, "Git repo ok");
            return new GitResult(false, out.trim());
        } catch (Exception e) {
            return new GitResult(false, e.getMessage());
        }
    }

    public GitResult addAll(Path dir) {
        return run(dir, List.of("git","add","-A"));
    }

    public GitResult commit(Path dir, String message) {
        return run(dir, List.of("git","commit","-m", message));
    }

    private GitResult run(Path dir, List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            return new GitResult(code == 0, out.trim());
        } catch (IOException | InterruptedException e) {
            return new GitResult(false, e.getMessage());
        }
    }
}
