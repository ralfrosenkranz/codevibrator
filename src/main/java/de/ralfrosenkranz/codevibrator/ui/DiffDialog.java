package de.ralfrosenkranz.codevibrator.ui;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DiffDialog extends JDialog {

    public DiffDialog(Window owner, String title, byte[] oldBytes, byte[] newBytes) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setSize(1000, 700);
        setLocationRelativeTo(owner);

        String oldText = new String(oldBytes, StandardCharsets.UTF_8);
        String newText = new String(newBytes, StandardCharsets.UTF_8);

        JTextArea left = new JTextArea(oldText);
        JTextArea right = new JTextArea(newText);
        left.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTextArea diffArea = new JTextArea(buildSimpleDiff(oldText, newText));
        diffArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        diffArea.setEditable(false);

        JSplitPane lr = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(left),
                new JScrollPane(right));
        lr.setResizeWeight(0.5);

        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                lr,
                new JScrollPane(diffArea));
        all.setResizeWeight(0.8);

        add(all, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(ev -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);
        add(south, BorderLayout.SOUTH);
    }

    /**
     * MVP: Simple line-by-line diff (deterministic, dependency-free).
     * Not a minimal diff; intended only as a readable preview.
     */
    private String buildSimpleDiff(String oldText, String newText) {
        List<String> oldLines = oldText.lines().toList();
        List<String> newLines = newText.lines().toList();

        StringBuilder sb = new StringBuilder();
        int max = Math.max(oldLines.size(), newLines.size());

        for (int i = 0; i < max; i++) {
            String o = i < oldLines.size() ? oldLines.get(i) : null;
            String n = i < newLines.size() ? newLines.get(i) : null;

            if (o == null) {
                sb.append("+ ").append(n).append("\n");
            } else if (n == null) {
                sb.append("- ").append(o).append("\n");
            } else if (!o.equals(n)) {
                sb.append("- ").append(o).append("\n");
                sb.append("+ ").append(n).append("\n");
            } else {
                sb.append("  ").append(o).append("\n");
            }
        }

        return sb.toString();
    }
}
