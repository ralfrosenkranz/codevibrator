package de.ralfrosenkranz.codevibrator.ui;

import de.ralfrosenkranz.codevibrator.ui.LafSupport;

import de.ralfrosenkranz.codevibrator.config.DirectoryConfig;
import de.ralfrosenkranz.codevibrator.config.ProfileDirConfig;
import de.ralfrosenkranz.codevibrator.config.ProjectConfig;
import de.ralfrosenkranz.codevibrator.config.SelectorState;
import de.ralfrosenkranz.codevibrator.git.GitService;
import de.ralfrosenkranz.codevibrator.importer.ImportPlan;
import de.ralfrosenkranz.codevibrator.importer.ImportService;
import de.ralfrosenkranz.codevibrator.logging.ResultLog;
import de.ralfrosenkranz.codevibrator.logging.ResultLogService;
import de.ralfrosenkranz.codevibrator.persist.ConfigService;
import de.ralfrosenkranz.codevibrator.persist.InheritanceOverrideReason;
import de.ralfrosenkranz.codevibrator.selectors.*;
import de.ralfrosenkranz.codevibrator.ui.model.FileRow;
import de.ralfrosenkranz.codevibrator.ui.model.FileTableModel;
import de.ralfrosenkranz.codevibrator.ui.model.SelectorRow;
import de.ralfrosenkranz.codevibrator.ui.model.SelectorTableModel;
import de.ralfrosenkranz.codevibrator.selectors.*;
import de.ralfrosenkranz.codevibrator.ui.icons.Icons;
import de.ralfrosenkranz.codevibrator.ui.model.*;


import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import de.ralfrosenkranz.codevibrator.zip.ZipExportService;

public class MainFrame extends JFrame {
    private final Path projectRoot;
    private final ConfigService config;
    private final SelectorResolver resolver;
    private final ZipExportService zipExportService;
    private final ImportService importService;
    private final ResultLogService logService = new ResultLogService();
    private final GitService git = new GitService();

    private final DirTreeModel treeModel;
    private final JTree dirTree;
    private final DirTreeCellRenderer treeRenderer;

    private Path selectedDir;
    private boolean uiLoading = false;
    private final JLabel selectedPathLabel = new JLabel();
    private final JLabel selectedPathHeader = new JLabel();

    private final JComboBox<String> profileCombo = new JComboBox<>(new String[]{"default"});
    private final JCheckBox excludeCheck = new JCheckBox("Exclude from Zip");
    private final JCheckBox readonlyDirCheck = new JCheckBox("Readonly directory (import block)");
    private final JTextField selectorsTextField = new JTextField();
    private final JTextField readonlyPatternsField = new JTextField();

    private final SelectorTableModel selectorTableModel = new SelectorTableModel();
    private final JTable selectorTable = new JTable(selectorTableModel);

    private final FileTableModel fileTableModel = new FileTableModel();
    private final JTable fileTable = new JTable(fileTableModel);

    private final JTextArea logArea = new JTextArea();

    private final JCheckBox gitCommitCheck = new JCheckBox("Git add+commit (before send)", true);
    private final JCheckBox autoPasteCheck = new JCheckBox("Auto-Paste (experimental)", false);

    public MainFrame(Path projectRoot, ConfigService config) {
        super("CodeVibrator");
        this.projectRoot = projectRoot;
        this.config = config;
        this.resolver = new SelectorResolver(config);
        this.zipExportService = new ZipExportService(config);
        this.importService = new ImportService(config);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        ProjectConfig pc = config.loadProjectConfig();
        profileCombo.setSelectedItem(pc.activeProfile == null ? "default" : pc.activeProfile);

        // Left: directory tree
        treeModel = new DirTreeModel(projectRoot);
        dirTree = new JTree(treeModel);
        dirTree.setRootVisible(true);
        dirTree.setShowsRootHandles(true);
        treeRenderer = new DirTreeCellRenderer(config);
        treeRenderer.setProfile(activeProfile());
        dirTree.setCellRenderer(treeRenderer);

        dirTree.addTreeSelectionListener(e -> {
            TreePath p = e.getPath();
            if (p == null) return;
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) p.getLastPathComponent();
            if (n.getUserObject() instanceof DirTreeModel.DirNode dn) {
                onDirSelected(dn.path());
            }
        });

        JScrollPane treeScroll = new JScrollPane(dirTree);

        // Top: file table + log tab
        JTabbedPane rightTopTabs = new JTabbedPane();
        rightTopTabs.addTab("Files", buildFileTablePanel());
        rightTopTabs.addTab("Result Log", new JScrollPane(logArea));
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setEditable(false);

        // Bottom: selectors panel
        JPanel selectorsPanel = buildSelectorsPanel();

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightTopTabs, selectorsPanel);
        rightSplit.setResizeWeight(0.65);
JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightSplit);
        mainSplit.setResizeWeight(0.25);

        setJMenuBar(buildMenuBar());
        add(buildGlobalPanel(), BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);

        installAutoSaveListeners();

        // init selection: root
        expandAllTreeRows();
        onDirSelected(projectRoot);
        dirTree.setSelectionRow(0);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu m = new JMenu("File");

        JMenu laf = new JMenu("Look&Feel");
        ButtonGroup bg = new ButtonGroup();
        String selectedLaf = config.loadProjectConfig().uiLookAndFeel;
        for (String name : LafSupport.ALL_LAF_CHOICES) {
            addLafItem(laf, bg, name, selectedLaf);
        }
        if (bg.getSelection() == null && laf.getItemCount() > 0) {
            ((JRadioButtonMenuItem) laf.getItem(0)).setSelected(true);
        }


        JMenuItem send = new JMenuItem("Send to ChatGPT");
        send.addActionListener(e -> onSendToChatGPT());
        m.add(send);

        JMenuItem imp = new JMenuItem("Import Result Zip...");
        imp.addActionListener(e -> onImportZip());
        m.add(imp);

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        m.add(exit);

        mb.add(m);
        mb.add(laf);
        return mb;
    }

    private JPanel buildSelectorsPanel() {
        JPanel p = new JPanel(new BorderLayout());

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshAll());
        top.add(refresh, c);

        c.gridx = 0; c.gridy = 1;
        top.add(excludeCheck, c);
        c.gridx = 1;
        top.add(readonlyDirCheck, c);

        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        top.add(new JLabel("selectorsText (FORCE+ACTIVE):"), c);
        c.gridx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        top.add(selectorsTextField, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        top.add(new JLabel("readonlyFilePatterns:"), c);
        c.gridx = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        top.add(readonlyPatternsField, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 4;
        top.add(gitCommitCheck, c);
        c.gridx = 1;
        top.add(autoPasteCheck, c);
profileCombo.addActionListener(e -> {
            ProjectConfig pc = config.loadProjectConfig();
            pc.activeProfile = activeProfile();
            config.saveProjectConfig(pc);
            treeRenderer.setProfile(activeProfile());
            dirTree.repaint();
            refreshAll();
        });

        // selector table
        selectorTable.setFillsViewportHeight(true);
        selectorTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        JScrollPane tableScroll = new JScrollPane(selectorTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Multiplied selectors (inheritance)"));

        p.add(top, BorderLayout.NORTH);
        p.add(tableScroll, BorderLayout.CENTER);

        excludeCheck.addActionListener(e -> saveDirConfigFromUI());
        readonlyDirCheck.addActionListener(e -> saveDirConfigFromUI());

        return p;
    }

    private JPanel buildFileTablePanel() {
        JPanel p = new JPanel(new BorderLayout());

        // Scrollable full-width header (selected directory)
        selectedPathHeader.setText("");
        selectedPathHeader.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel headerHolder = new JPanel(new BorderLayout());
        headerHolder.add(selectedPathHeader, BorderLayout.CENTER);
        JScrollPane headerScroll = new JScrollPane(headerHolder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        headerScroll.setBorder(BorderFactory.createEmptyBorder());

        fileTable.setFillsViewportHeight(true);
        fileTable.setRowHeight(22);

        TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(fileTableModel);
        fileTable.setRowSorter(sorter);

        fileTable.setDefaultRenderer(String.class, new FileStatusRenderer());
        // keep default checkbox renderer/editor for Boolean column



        adjustFileTableColumns();
        fileTable.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { adjustFileTableColumns(); }
        });

        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) onFileDoubleClick();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel north = new JPanel(new BorderLayout());
        north.add(headerScroll, BorderLayout.NORTH);
        north.add(buttons, BorderLayout.SOUTH);
        p.add(north, BorderLayout.NORTH);
        p.add(new JScrollPane(fileTable), BorderLayout.CENTER);
        return p;
    }

    private String activeProfile() {
        Object o = profileCombo.getSelectedItem();
        return o == null ? "default" : o.toString();
    }

    private void onDirSelected(Path dir) {
        selectedDir = dir;
        loadDirConfigToUI();
        refreshFileTable();
    }

    private void refreshAll() {
        loadDirConfigToUI();
        refreshFileTable();
        dirTree.repaint();
    }

    private void loadDirConfigToUI() {
        if (selectedDir == null) return;
        uiLoading = true;
        selectedPathLabel.setText(selectedDir.toString());
        selectedPathHeader.setText(selectedDir.toString());
        DirectoryConfig dc = config.loadDirectoryConfig(selectedDir);
        ProfileDirConfig pc = dc.profiles.get(activeProfile());
        if (pc == null) pc = new ProfileDirConfig();

        excludeCheck.setSelected(pc.excludeFromZip);
        readonlyDirCheck.setSelected(pc.readonlyDir);
        selectorsTextField.setText(pc.selectorsText == null ? "" : pc.selectorsText);
        readonlyPatternsField.setText(pc.readonlyFilePatterns == null ? "" : pc.readonlyFilePatterns);

        // build multiplied rows: use resolver resolved rules plus local force flags
        ResolvedDirRules rules = resolver.resolveRules(selectedDir, activeProfile());

            DirectoryConfig dcLocal = config.loadDirectoryConfig(selectedDir);
            ProfileDirConfig pcLocal = dcLocal.profiles.get(activeProfile());
            Map<String, Boolean> fileOverrides = (pcLocal == null || pcLocal.fileOverrides == null) ? Map.of() : pcLocal.fileOverrides;
        List<SelectorRow> rows = new ArrayList<>();
        for (EffectiveSelector s : rules.selectors()) {
            boolean force = isForcedLocal(pc, s.pattern());
            boolean active = s.active();
            rows.add(new SelectorRow(s.pattern(), force, active, true));
        }
        selectorTableModel.setRows(rows);
        updateOverrideMarkers();
        uiLoading = false;
    }

    private boolean isForcedLocal(ProfileDirConfig pc, String pattern) {
        if (pc == null) return false;
        if (SelectorResolver.splitSemicolon(pc.selectorsText).contains(pattern)) return true;
        SelectorState st = pc.selectorStates.get(pattern);
        return st != null && st.force;
    }

    private void saveDirConfigFromUI() {
        if (selectedDir == null) return;
        config.updateProfileDirConfig(selectedDir, activeProfile(), pc -> {
            pc.excludeFromZip = excludeCheck.isSelected();
            pc.readonlyDir = readonlyDirCheck.isSelected();
            pc.selectorsText = selectorsTextField.getText() == null ? "" : selectorsTextField.getText().trim();
            pc.readonlyFilePatterns = readonlyPatternsField.getText() == null ? "" : readonlyPatternsField.getText().trim();

            // selector table overrides (only store rows where force or active differs from implied parent)
            // MVP: store all rows as explicit state for simplicity
            pc.selectorStates.clear();
            for (SelectorRow r : selectorTableModel.rows()) {
                pc.selectorStates.put(r.pattern, new SelectorState(r.force, r.active));
            }
            return pc;
        });

        treeRenderer.setProfile(activeProfile());
        dirTree.repaint();
        refreshFileTable();
    }

    private void refreshFileTable() {
        if (selectedDir == null) return;
        try {
            ResolvedDirRules rules = resolver.resolveRules(selectedDir, activeProfile());

            DirectoryConfig dcLocal = config.loadDirectoryConfig(selectedDir);
            ProfileDirConfig pcLocal = dcLocal.profiles.get(activeProfile());
            Map<String, Boolean> fileOverrides = (pcLocal == null || pcLocal.fileOverrides == null) ? Map.of() : pcLocal.fileOverrides;

            List<FileRow> rows = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(selectedDir)) {
                for (Path p : ds) {
                    if (Files.isDirectory(p)) continue;
                    if (p.getFileName().toString().equals(".code.vibrator")) continue;
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    FileDecision d = FileDecider.decide(p.getFileName().toString(), rules);
                    FileRow row = new FileRow(p, attrs, d);
                    Boolean ov = fileOverrides.get(row.name);
                    if (ov != null) row.includeInZip = ov;
                    rows.add(row);
                }
            }
            fileTableModel.setRows(rows);
        } catch (IOException e) {
            logArea.setText("Error reading directory: " + e.getMessage());
        }
    }

    
private void adjustFileTableColumns() {
    if (fileTable.getColumnModel().getColumnCount() < 2) return;
    int w = fileTable.getWidth();
    if (w <= 0) w = 1000;
    // column 0: checkbox small
    fileTable.getColumnModel().getColumn(0).setPreferredWidth(40);
    fileTable.getColumnModel().getColumn(0).setMaxWidth(60);
    // column 1: name half width
    fileTable.getColumnModel().getColumn(1).setPreferredWidth(Math.max(300, w / 2));
}

private void onSendToChatGPT() {
        String profile = activeProfile();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"));

        ResultLog log = new ResultLog();
        Path daily;
        Path zipPath;
        try {
            daily = zipExportService.ensureDailyDir();
            zipPath = zipExportService.exportZip(daily, ts, profile);
            log.stats.add("Zip created: " + zipPath.getFileName());
        } catch (Exception ex) {
            log.warnings.add("Export failed: " + ex.getMessage());
            showLogAndPersist(dailyFallback(), ts, "send", log);
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (gitCommitCheck.isSelected()) {
            GitService.GitResult repo = git.isRepo(projectRoot);
            if (!repo.ok()) {
                log.warnings.add("Git not available: " + repo.message());
                showLogAndPersist(dailyFallback(), ts, "send", log);
                JOptionPane.showMessageDialog(this, "Git cycle aborted:\n" + repo.message(), "Git Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String msg = PromptComposer.deriveCommitMessage(config.loadProjectConfig().promptBase);
            msg = JOptionPane.showInputDialog(this, "Commit message:", msg);
            if (msg == null) {
                log.warnings.add("Git commit canceled by user.");
                showLogAndPersist(dailyFallback(), ts, "send", log);
                return;
            }
            GitService.GitResult add = git.addAll(projectRoot);
            if (!add.ok()) {
                log.warnings.add("git add failed: " + add.message());
                showLogAndPersist(dailyFallback(), ts, "send", log);
                JOptionPane.showMessageDialog(this, "git add failed:\n" + add.message(), "Git Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            GitService.GitResult commit = git.commit(projectRoot, msg);
            if (!commit.ok()) {
                log.warnings.add("git commit failed: " + commit.message());
                showLogAndPersist(dailyFallback(), ts, "send", log);
                JOptionPane.showMessageDialog(this, "git commit failed:\n" + commit.message(), "Git Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log.stats.add("Git commit ok.");
        }

        // Compose prompt + clipboard
        ProjectConfig pc = config.loadProjectConfig();
        String prompt = PromptComposer.compose(pc, profile, zipPath.getFileName().toString());
        pc.promptHistory.add(0, prompt);
        if (pc.promptHistory.size() > 50) pc.promptHistory = pc.promptHistory.subList(0, 50);
        config.saveProjectConfig(pc);

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(prompt), null);
        log.stats.add("Prompt copied to clipboard.");

        // Also copy zip absolute path for convenience
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(prompt + "\n\nZIP_PATH: " + zipPath.toAbsolutePath()), null);
        } catch (Exception ignored) { }


        // Open / activate ChatGPT browser (best-effort reuse of existing window)
        try {
            ChatGptBrowserSupport.openChatGpt(pc, log);
        } catch (Exception ex) {
            log.warnings.add("Cannot open/activate ChatGPT: " + ex.getMessage());
        }

        // Open daily folder for manual zip upload (ChatGPT cannot read local files automatically)
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(zipPath.getParent().toFile());
                log.stats.add("Daily folder opened.");
            }
        } catch (Exception ex) {
            log.warnings.add("Cannot open daily folder: " + ex.getMessage());
        }

        // optional auto-paste
        if (autoPasteCheck.isSelected()) {
            try {
                Robot r = new Robot();
                boolean mac = System.getProperty("os.name","").toLowerCase().contains("mac");
                int mod = mac ? java.awt.event.KeyEvent.VK_META : java.awt.event.KeyEvent.VK_CONTROL;
                r.delay(500);
                r.keyPress(mod);
                r.keyPress(java.awt.event.KeyEvent.VK_V);
                r.keyRelease(java.awt.event.KeyEvent.VK_V);
                r.keyRelease(mod);
                log.stats.add("Auto-paste attempted.");
            } catch (Exception ex) {
                log.warnings.add("Auto-paste failed (non-fatal): " + ex.getMessage());
            }
        }

        showLogAndPersist(dailyFallback(), ts, "send", log);

        JOptionPane.showMessageDialog(this,
                "Zip created: " + zipPath.getFileName() + "\n" +
                "Prompt copied to clipboard.\n" +
                "Daily folder opened for manual zip upload.\n\n" +
                "Next steps (manual):\n" +
                "1) In browser, paste prompt (Ctrl+V / Cmd+V)\n" +
                "2) In the file dialog / upload area, select the zip file (from the opened daily folder).",
                "Send to ChatGPT", JOptionPane.INFORMATION_MESSAGE);

        // requirement: checkbox resets to ON after each cycle
        gitCommitCheck.setSelected(true);
    }

    private Path dailyFallback() {
        try {
            return zipExportService.ensureDailyDir();
        } catch (IOException e) {
            return projectRoot;
        }
    }

    private void onImportZip() {
        // choose zip (MVP: propose latest in daily dir if exists)
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Zip files", "zip"));
        Path suggested = findLatestZipInDailyDir();
        if (suggested != null) fc.setSelectedFile(suggested.toFile());

        int res = fc.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        Path zipPath = fc.getSelectedFile().toPath();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"));
        ResultLog log = new ResultLog();

        ImportService.Validation v = importService.validateZipStructure(zipPath);
        if (!v.ok()) {
            log.warnings.add(v.message());
            showLogAndPersist(dailyFallback(), ts, "import", log);
            JOptionPane.showMessageDialog(this, v.message(), "Fatal Zip", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            ImportPlan plan = importService.buildPlan(zipPath, activeProfile(), log);
            long nNew = plan.items.stream().filter(i -> i.kind == ImportPlan.Kind.NEW).count();
            long nChg = plan.items.stream().filter(i -> i.kind == ImportPlan.Kind.CHANGED).count();
            long nId = plan.items.stream().filter(i -> i.kind == ImportPlan.Kind.IDENTICAL).count();
            long nBlk = plan.items.stream().filter(i -> i.kind == ImportPlan.Kind.BLOCKED).count();
            long nIgn = plan.items.stream().filter(i -> i.kind == ImportPlan.Kind.IGNORED_EXECUTABLE).count();
            log.stats.add("#new=" + nNew + " #changed=" + nChg + " #identical=" + nId + " #blocked=" + nBlk + " #ignored=" + nIgn);

            // Display-only dialog: confirm/cancel (no per-file override)
            ImportPlanDialog dlg = new ImportPlanDialog(this, zipPath, plan);
            dlg.setVisible(true);
            if (!dlg.isConfirmed()) {
                log.warnings.add("Import canceled by user.");
                showLogAndPersist(dailyFallback(), ts, "import", log);
                return;
            }

            // Apply
            importService.apply(zipPath, plan, log);
            log.stats.add("Import applied.");
            showLogAndPersist(dailyFallback(), ts, "import", log);
            refreshAll();
        } catch (Exception ex) {
            log.warnings.add("Import failed: " + ex.getMessage());
            showLogAndPersist(dailyFallback(), ts, "import", log);
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path findLatestZipInDailyDir() {
        try {
            Path daily = zipExportService.ensureDailyDir();
            try (var s = Files.list(daily)) {
                return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private void showLogAndPersist(Path dailyDir, String ts, String prefix, ResultLog log) {
        // requirement: do not write send_result_<timestamp>.txt anymore (UI display remains)
        if (!"send".equals(prefix)) {
            try {
                logService.write(dailyDir, ts, prefix, log);
            } catch (IOException ignored) { }
        }
        logArea.setText(log.toText());
    }

    private void onFileDoubleClick() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        FileRow r = fileTableModel.getRow(modelRow);


        // MVP: show nothing unless last import diff is available; as lightweight fallback show file content
        try {
            byte[] bytes = Files.readAllBytes(r.path);
            JDialog dlg = new JDialog(this, r.name, Dialog.ModalityType.MODELESS);
            dlg.setSize(900, 650);
            dlg.setLocationRelativeTo(this);
            JTextArea ta = new JTextArea(new String(bytes));
            ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            dlg.add(new JScrollPane(ta), BorderLayout.CENTER);
            dlg.setVisible(true);
        } catch (IOException ignored) { }
    }

    private static class FileStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!(table.getModel() instanceof FileTableModel m)) return c;

            int modelRow = table.convertRowIndexToModel(row);
            FileRow fr = m.getRow(modelRow);

            if (!isSelected) {
                if (fr.decision.blocked()) {
                    c.setForeground(new Color(0xB55B5B));
                } else if (fr.decision.inZip()) {
                    c.setForeground(new Color(0x2E7D32));
                } else {
                    c.setForeground(Color.DARK_GRAY);
                }
            }

            if (column == 1 && c instanceof JLabel l) {
                if (fr.decision.conflict()) {
                    l.setIcon(Icons.warning());
                } else if (fr.decision.blocked()) {
                    l.setIcon(Icons.lock());
                } else {
                    l.setIcon(null);
                }
            }
            return c;
        }
    }

private JPanel buildGlobalPanel() {
    JPanel p = new JPanel(new BorderLayout());
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));

    left.add(new JLabel("Profile:"));
    left.add(profileCombo);

    JButton send = new JButton("Send to ChatGPT");
    send.setFont(send.getFont().deriveFont(Font.BOLD, 14f));
    send.addActionListener(e -> onSendToChatGPT());
    left.add(send);

    JButton imp = new JButton("Import Result Zip...");
    imp.addActionListener(e -> onImportZip());
    left.add(imp);

    p.add(left, BorderLayout.WEST);

    JLabel rootLbl = new JLabel("Project: " + projectRoot);
    rootLbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
    p.add(rootLbl, BorderLayout.EAST);

    return p;
}


private void installAutoSaveListeners() {
    javax.swing.Timer t = new javax.swing.Timer(300, e -> {
        if (!uiLoading) saveDirConfigFromUI();
    });
    t.setRepeats(false);

    javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
        private void kick() { if (!uiLoading) t.restart(); }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { kick(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { kick(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { kick(); }
    };

    selectorsTextField.getDocument().addDocumentListener(dl);
    readonlyPatternsField.getDocument().addDocumentListener(dl);

    selectorTableModel.addTableModelListener(e -> { if (!uiLoading) t.restart(); });

        // Per-file include overrides (checkbox in file table)
        fileTableModel.addTableModelListener(e -> {
            if (uiLoading) return;
            if (e.getColumn() == 0) {
                saveFileOverridesFromTable();
                refreshFileTable();
            }
        });

    excludeCheck.addItemListener(e -> { if (!uiLoading) saveDirConfigFromUI(); });
    readonlyDirCheck.addItemListener(e -> { if (!uiLoading) saveDirConfigFromUI(); });
}



private void setOverrideMarker(JComponent c, boolean active) {
    if (active) {
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(255, 140, 0), 2),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
    } else {
        c.setBorder(UIManager.getBorder("TextField.border"));
    }
}


private String formatReason(InheritanceOverrideReason r) {
    String p = (r.profile() == null || r.profile().isBlank()) ? "" : (r.profile() + ": ");
    String c = (r.count() == null) ? "" : (" (" + r.count() + ")");
    String d = (r.detail() == null || r.detail().isBlank()) ? "" : (" - " + r.detail());
    return p + r.key() + c + d;
}

private void updateOverrideMarkers() {
    if (selectedDir == null) return;

    // Clear all markers first
    setOverrideMarker(selectorsTextField, false);
    setOverrideMarker(readonlyPatternsField, false);
    setOverrideMarker(excludeCheck, false);
    setOverrideMarker(readonlyDirCheck, false);
    setOverrideMarker(selectorTable, false);
    setOverrideMarker(fileTable, false);

    // Clear tooltips
    selectorsTextField.setToolTipText(null);
    readonlyPatternsField.setToolTipText(null);
    excludeCheck.setToolTipText(null);
    readonlyDirCheck.setToolTipText(null);
    selectorTable.setToolTipText(null);
    fileTable.setToolTipText(null);

    // Use structured reasons from config (null => no overrides)
    java.util.List<InheritanceOverrideReason> reasons = config.hasInheritanceOverrides(selectedDir);
    if (reasons == null) return;

    java.util.List<String> selTips = new java.util.ArrayList<>();
    java.util.List<String> roTips = new java.util.ArrayList<>();
    java.util.List<String> exTips = new java.util.ArrayList<>();
    java.util.List<String> rdTips = new java.util.ArrayList<>();
    java.util.List<String> stTips = new java.util.ArrayList<>();
    java.util.List<String> foTips = new java.util.ArrayList<>();

    for (InheritanceOverrideReason r : reasons) {
        if (r == null || r.key() == null) continue;

        switch (r.key()) {
            case SELECTORS_TEXT -> {
                setOverrideMarker(selectorsTextField, true);
                selTips.add(formatReason(r));
            }
            case READONLY_FILE_PATTERNS -> {
                setOverrideMarker(readonlyPatternsField, true);
                roTips.add(formatReason(r));
            }
            case EXCLUDE_FROM_ZIP -> {
                setOverrideMarker(excludeCheck, true);
                exTips.add(formatReason(r));
            }
            case READONLY_DIR -> {
                setOverrideMarker(readonlyDirCheck, true);
                rdTips.add(formatReason(r));
            }
            case SELECTOR_STATES_OVERRIDDEN -> {
                setOverrideMarker(selectorTable, true);
                stTips.add(formatReason(r));
            }
            case FILE_OVERRIDES -> {
                setOverrideMarker(fileTable, true);
                foTips.add(formatReason(r));
            }
            case UNREADABLE_CONFIG -> {
                // Conservative: highlight the selector panel essentials because config cannot be evaluated reliably
                setOverrideMarker(selectorsTextField, true);
                setOverrideMarker(readonlyPatternsField, true);
                setOverrideMarker(excludeCheck, true);
                setOverrideMarker(readonlyDirCheck, true);
                selTips.add(formatReason(r));
            }
            default -> {
                // ignore unknown keys (forward compatible)
            }
        }
    }

    // Apply tooltips (do not overwrite existing UI text)
    if (!selTips.isEmpty()) selectorsTextField.setToolTipText(String.join(" | ", selTips));
    if (!roTips.isEmpty()) readonlyPatternsField.setToolTipText(String.join(" | ", roTips));
    if (!exTips.isEmpty()) excludeCheck.setToolTipText(String.join(" | ", exTips));
    if (!rdTips.isEmpty()) readonlyDirCheck.setToolTipText(String.join(" | ", rdTips));
    if (!stTips.isEmpty()) selectorTable.setToolTipText(String.join(" | ", stTips));
    if (!foTips.isEmpty()) fileTable.setToolTipText(String.join(" | ", foTips));
}


private void expandAllTreeRows() {
    for (int i = 0; i < dirTree.getRowCount(); i++) {
        dirTree.expandRow(i);
    }
}


private void addLafItem(JMenu menu, ButtonGroup bg, String name, String selectedLafName) {
    JRadioButtonMenuItem item = new JRadioButtonMenuItem(name);
    bg.add(item);
    menu.add(item);
    if (name != null && name.equalsIgnoreCase(selectedLafName)) item.setSelected(true);

    item.addActionListener(e -> {
        try {
            LafSupport.applyLookAndFeel(name);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        ProjectConfig pc = config.loadProjectConfig();
                pc.uiLookAndFeel = name;
                config.saveProjectConfig(pc);
                SwingUtilities.updateComponentTreeUI(this);
                this.invalidate();
                this.validate();
                this.repaint();
});
}


private void saveFileOverridesFromTable() {
    if (selectedDir == null) return;
    // Store only overrides different from selector-derived default
    config.updateProfileDirConfig(selectedDir, activeProfile(), pc -> {
        pc.fileOverrides.clear();
        for (int i = 0; i < fileTableModel.getRowCount(); i++) {
            FileRow r = fileTableModel.getRow(i);
            boolean def = r.decision.inZip();
            if (r.includeInZip != def) {
                pc.fileOverrides.put(r.name, r.includeInZip);
            }
        }
        return pc;
    });
}

}
