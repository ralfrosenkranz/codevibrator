package de.ralfrosenkranz.codevibrator.app;

import de.ralfrosenkranz.codevibrator.persist.ConfigService;
import de.ralfrosenkranz.codevibrator.ui.MainFrame;
import de.ralfrosenkranz.codevibrator.ui.LafSupport;
import de.ralfrosenkranz.codevibrator.config.ProjectConfig;

import javax.swing.*;
import java.nio.file.Path;

public class CodeVibrator {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Path projectRoot = Path.of("").toAbsolutePath().normalize();
            ConfigService configService = new ConfigService(projectRoot);
            configService.ensureHomeDefaults();

            ProjectConfig pc = configService.loadProjectConfig();
            try {
                LafSupport.applyLookAndFeel(pc.uiLookAndFeel);
            } catch (Exception ex) {
               System.out.println(ex.getMessage());
            }

            MainFrame frame = new MainFrame(projectRoot, configService);
            frame.setVisible(true);
        });
    }
}
