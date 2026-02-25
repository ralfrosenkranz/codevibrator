package de.ralfrosenkranz.codevibrator.ui;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

/**
 * Optional FlatLaf integration (no compile-time dependency).
 *
 * If FlatLaf is not on the classpath, this falls back to System LAF without throwing.
 */
public final class LafSupport {

    private LafSupport() {}

    public static final String LAF_SYSTEM = "System";

    // Core FlatLaf
    public static final String LAF_FLAT_LIGHT = "Flat Light";
    public static final String LAF_FLAT_DARK = "Flat Dark";
    public static final String LAF_FLAT_INTELLIJ = "Flat IntelliJ";
    public static final String LAF_FLAT_DARCULA = "Flat Darcula";

    // FlatLaf macOS themes
    public static final String LAF_FLAT_MACOS_LIGHT = "Flat macOS Light";
    public static final String LAF_FLAT_MACOS_DARK = "Flat macOS Dark";

    /**
     * Convenience: list of all choices in one place (useful for a future settings UI).
     *
     * Note: IntelliJ theme availability depends on the flatlaf-intellij-themes dependency.
     */
    public static final String[] ALL_LAF_CHOICES = {
            LAF_SYSTEM,
            LAF_FLAT_LIGHT,
            LAF_FLAT_DARK,
            LAF_FLAT_INTELLIJ,
            LAF_FLAT_DARCULA,
            LAF_FLAT_MACOS_LIGHT,
            LAF_FLAT_MACOS_DARK
    };



    /**
     * Adds subtle color and shape accents on top of FlatLaf.
     *
     * This is intentionally light-touch and only applies when FlatLaf is active.
     */
    private static void applyUiAccents() {

        // macOS Accent Blue
        ColorUIResource accent = new ColorUIResource(new Color(0x0A, 0x84, 0xFF));
        ColorUIResource accentSoft = new ColorUIResource(new Color(0x0A, 0x84, 0xFF, 56));

        // Global accent / focus
        UIManager.put("Component.accentColor", accent);
        UIManager.put("Component.focusColor", accent);

        // Rounded controls
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);

        // Focus ring
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 1);

        // Selections (lists/tables/text)
        UIManager.put("List.selectionBackground", accentSoft);
        UIManager.put("Table.selectionBackground", accentSoft);
        UIManager.put("Tree.selectionBackground", accentSoft);
        UIManager.put("TextComponent.selectionBackground", accentSoft);

        // Links
        UIManager.put("Component.linkColor", accent);
    }

    public static void applyLookAndFeel(String selected) throws Exception {
        switch (selected) {
            case LAF_SYSTEM:
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                break;
            case LAF_FLAT_LIGHT:
                FlatLightLaf.setup();
                break;
            case LAF_FLAT_DARK:
                FlatDarkLaf.setup();
                break;
            case LAF_FLAT_INTELLIJ:
                FlatIntelliJLaf.setup();
                break;
            case LAF_FLAT_DARCULA:
                FlatDarculaLaf.setup();
                break;
            case LAF_FLAT_MACOS_LIGHT:
                FlatMacLightLaf.setup();
                break;
            case LAF_FLAT_MACOS_DARK:
                FlatMacDarkLaf.setup();
                break;

            default:
                // fallback
                //FlatMacDarkLaf.setup();
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                break;
        }

        applyUiAccents();
    }

    private static boolean setupIntelliJThemeByName(String themeName) {
        try {
            // Keep this method resilient against small API changes between FlatLaf versions.
            // Use reflection to avoid compile-time dependency on ThemeInfo details.

            Object infosObj = FlatAllIJThemes.class.getField("INFOS").get(null);
            if (!(infosObj instanceof Object[] infos))
                return false;

            for (Object info : infos) {
                if (info == null)
                    continue;

                String name = null;
                try {
                    // some versions provide a getter
                    Object v = info.getClass().getMethod("getName").invoke(info);
                    if (v instanceof String s)
                        name = s;
                } catch (Throwable ignored) {
                    // ignore and try field access below
                }
                if (name == null) {
                    try {
                        // other versions expose a public field
                        Object v = info.getClass().getField("name").get(info);
                        if (v instanceof String s)
                            name = s;
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }

                if (name != null && name.equalsIgnoreCase(themeName)) {
                    // invoke FlatAllIJThemes.setup(ThemeInfo)
                    FlatAllIJThemes.class.getMethod("setup", info.getClass()).invoke(null, info);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // dependency missing or API changed
        }
        return false;
    }
}
