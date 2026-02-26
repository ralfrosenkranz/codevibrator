package de.ralfrosenkranz.codevibrator.ui;

import de.ralfrosenkranz.codevibrator.config.ProjectConfig;
import de.ralfrosenkranz.codevibrator.logging.ResultLog;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort support for reusing an already open ChatGPT browser window.
 *
 * <p>Important: fully reliable cross-process window reuse is not portable.
 * This class attempts OS-specific activation first (optional), and falls back
 * to opening the configured URL via Desktop.browse().</p>
 */
public final class ChatGptBrowserSupport {

    private ChatGptBrowserSupport() {
    }

    public static void openChatGpt(ProjectConfig pc, ResultLog log) {
        String url = (pc == null || pc.chatGptUrl == null || pc.chatGptUrl.isBlank())
                ? "https://chat.openai.com/"
                : pc.chatGptUrl;

        boolean activated = false;
        if (pc != null && pc.reuseExistingChatGptWindow) {
            activated = tryActivateExistingChatGptWindow(log);
            if (activated) {
                log.stats.add("Activated existing ChatGPT browser window (best-effort)." );
            } else {
                log.warnings.add("No existing ChatGPT window detected (best-effort). Opening URL instead." );
            }
        }

        if (!activated) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(url));
                    log.stats.add("Browser opened: " + url);
                } else {
                    log.warnings.add("Desktop API not supported; cannot open browser automatically.");
                }
            } catch (Exception ex) {
                log.warnings.add("Cannot open browser: " + ex.getMessage());
            }
        }
    }

    private static boolean tryActivateExistingChatGptWindow(ResultLog log) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                return tryActivateWindows(log);
            }
            if (os.contains("mac")) {
                return tryActivateMac(log);
            }
            // Linux / Unix
            return tryActivateLinux(log);
        } catch (Exception ex) {
            log.warnings.add("Best-effort activation failed: " + ex.getMessage());
            return false;
        }
    }

    private static boolean tryActivateWindows(ResultLog log) {
        // Uses PowerShell + user32 interop via Add-Type (no extra Java deps).
        // Searches visible top-level windows by title containing "ChatGPT" or "OpenAI".
        String ps = """
                $ErrorActionPreference = 'SilentlyContinue'
                Add-Type @'
                using System;
                using System.Text;
                using System.Runtime.InteropServices;
                public class Win32 {
                  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
                  [DllImport(\"user32.dll\")] public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
                  [DllImport(\"user32.dll\")] public static extern bool IsWindowVisible(IntPtr hWnd);
                  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
                  [DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd);
                }
                '@
                $targets = @('ChatGPT','OpenAI')
                $found = $false
                [Win32]::EnumWindows({
                  param($hWnd, $lParam)
                  if (-not [Win32]::IsWindowVisible($hWnd)) { return $true }
                  $sb = New-Object System.Text.StringBuilder 1024
                  [void][Win32]::GetWindowText($hWnd, $sb, $sb.Capacity)
                  $title = $sb.ToString()
                  if ([string]::IsNullOrWhiteSpace($title)) { return $true }
                  foreach ($t in $targets) {
                    if ($title -like ('*' + $t + '*')) {
                      [void][Win32]::SetForegroundWindow($hWnd)
                      $script:found = $true
                      return $false
                    }
                  }
                  return $true
                }, [IntPtr]::Zero) | Out-Null
                if ($found) { exit 0 } else { exit 1 }
                """;

        ExecResult r = exec(Duration.ofSeconds(2), "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", ps);
        if (r.timedOut) {
            log.warnings.add("Windows activation timed out (best-effort)." );
            return false;
        }
        return r.exitCode == 0;
    }

    private static boolean tryActivateMac(ResultLog log) {
        // AppleScript best-effort:
        // 1) Try to bring a browser window that likely contains ChatGPT to front by activating a running browser.
        // We cannot reliably detect existing tabs without browser-specific scripting/permissions.
        String script = """
                on tryActivate(appName)
                  try
                    tell application \"System Events\"
                      if exists (process appName) then
                        tell process appName to set frontmost to true
                        return true
                      end if
                    end tell
                  end try
                  return false
                end tryActivate

                if tryActivate(\"Google Chrome\") then return 0
                if tryActivate(\"Microsoft Edge\") then return 0
                if tryActivate(\"Safari\") then return 0
                if tryActivate(\"Firefox\") then return 0
                return 1
                """;
        ExecResult r = exec(Duration.ofSeconds(2), "osascript", "-e", script);
        if (r.timedOut) {
            log.warnings.add("macOS activation timed out (best-effort)." );
            return false;
        }
        // osascript returns 0 even if script returns 1 unless we 'return' properly; keep it simple:
        // If it printed '0' -> success.
        if (r.exitCode == 0) {
            String out = (r.stdout == null) ? "" : r.stdout.trim();
            return out.endsWith("0");
        }
        return false;
    }

    private static boolean tryActivateLinux(ResultLog log) {
        // X11 best-effort using wmctrl (often unavailable on Wayland and not installed by default).
        ExecResult has = exec(Duration.ofSeconds(1), "sh", "-lc", "command -v wmctrl >/dev/null 2>&1");
        if (has.exitCode != 0) {
            log.warnings.add("wmctrl not found; cannot activate existing window on Linux (best-effort)." );
            return false;
        }

        // Try focus by window title substring.
        ExecResult r1 = exec(Duration.ofSeconds(1), "sh", "-lc", "wmctrl -a ChatGPT >/dev/null 2>&1");
        if (r1.exitCode == 0) return true;
        ExecResult r2 = exec(Duration.ofSeconds(1), "sh", "-lc", "wmctrl -a OpenAI >/dev/null 2>&1");
        return r2.exitCode == 0;
    }

    private static ExecResult exec(Duration timeout, String... cmd) {
        List<String> out = new ArrayList<>();
        List<String> err = new ArrayList<>();
        boolean timedOut = false;
        int exit = -1;

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process p = pb.start();

            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                timedOut = true;
                p.destroyForcibly();
            } else {
                exit = p.exitValue();
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) out.add(line);
            } catch (Exception ignored) {
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) err.add(line);
            } catch (Exception ignored) {
            }
        } catch (Exception ex) {
            err.add(ex.getMessage());
        }

        return new ExecResult(exit, String.join("\n", out), String.join("\n", err), timedOut);
    }

    private record ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }
}
