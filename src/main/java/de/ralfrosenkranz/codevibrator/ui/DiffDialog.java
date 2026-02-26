package de.ralfrosenkranz.codevibrator.ui;

import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Side-by-side diff preview with synchronized scrolling and colored highlights.
 *
 * Implementation is dependency-free and line-based:
 * - aligns changed blocks so corresponding sections are shown on the same rows
 * - highlights inserts/deletes/changes
 */
public class DiffDialog extends JDialog {

    private static final Color COLOR_INSERT = new Color(230, 255, 230);
    private static final Color COLOR_DELETE = new Color(255, 230, 230);
    private static final Color COLOR_CHANGE = new Color(255, 255, 200);

    public DiffDialog(Window owner, String title, byte[] oldBytes, byte[] newBytes) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setSize(1000, 700);
        setLocationRelativeTo(owner);

        String oldText = new String(oldBytes, StandardCharsets.UTF_8);
        String newText = new String(newBytes, StandardCharsets.UTF_8);

        AlignedDiff aligned = alignByLineDiff(oldText, newText);

        JTextArea left = new JTextArea(aligned.oldText());
        JTextArea right = new JTextArea(aligned.newText());
        left.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        left.setEditable(false);
        right.setEditable(false);
        left.setLineWrap(false);
        right.setLineWrap(false);

        JScrollPane leftScroll = new JScrollPane(left);
        JScrollPane rightScroll = new JScrollPane(right);

        // Link vertical scrolling (shared model).
        rightScroll.getVerticalScrollBar().setModel(leftScroll.getVerticalScrollBar().getModel());

        JSplitPane lr = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        lr.setResizeWeight(0.5);
        add(lr, BorderLayout.CENTER);

        applyLineHighlights(left, aligned.kinds(), true);
        applyLineHighlights(right, aligned.kinds(), false);

        JButton close = new JButton("Close");
        close.addActionListener(ev -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);
        add(south, BorderLayout.SOUTH);
    }

    private void applyLineHighlights(JTextArea area, List<LineKind> kinds, boolean isLeft) {
        area.getHighlighter().removeAllHighlights();
        Highlighter.HighlightPainter insertPainter = new DefaultHighlighter.DefaultHighlightPainter(COLOR_INSERT);
        Highlighter.HighlightPainter deletePainter = new DefaultHighlighter.DefaultHighlightPainter(COLOR_DELETE);
        Highlighter.HighlightPainter changePainter = new DefaultHighlighter.DefaultHighlightPainter(COLOR_CHANGE);

        int lineCount = Math.min(area.getLineCount(), kinds.size());

        for (int i = 0; i < lineCount; i++) {
            LineKind kind = kinds.get(i);
            Highlighter.HighlightPainter painter = null;

            // For INSERT/DELETE we highlight only the side that actually has content.
            if (kind == LineKind.INSERT && !isLeft) painter = insertPainter;
            if (kind == LineKind.DELETE && isLeft) painter = deletePainter;
            if (kind == LineKind.CHANGE) painter = changePainter;

            if (painter == null) continue;

            try {
                int start = area.getLineStartOffset(i);
                int end = area.getLineEndOffset(i);
                area.getHighlighter().addHighlight(start, end, painter);
            } catch (Exception ignored) {
                // If offsets cannot be determined for some reason, skip highlighting.
            }
        }
    }

    private enum Op { EQUAL, INSERT, DELETE }

    private enum LineKind { SAME, INSERT, DELETE, CHANGE }

    private record Edit(Op op, String line) {}

    private record AlignedDiff(List<String> oldLinesAligned,
                               List<String> newLinesAligned,
                               List<LineKind> kinds) {

        String oldText() {
            return String.join("\n", oldLinesAligned);
        }

        String newText() {
            return String.join("\n", newLinesAligned);
        }
    }

    /**
     * Produces aligned old/new line lists from a Myers diff, pairing delete/insert runs into "change" rows.
     */
    private AlignedDiff alignByLineDiff(String oldText, String newText) {
        List<String> a = splitLinesPreserveEmpty(oldText);
        List<String> b = splitLinesPreserveEmpty(newText);

        List<Edit> edits = myersDiff(a, b);

        List<String> oldAligned = new ArrayList<>();
        List<String> newAligned = new ArrayList<>();
        List<LineKind> kinds = new ArrayList<>();

        int i = 0;
        while (i < edits.size()) {
            Edit e = edits.get(i);
            if (e.op == Op.EQUAL) {
                oldAligned.add(e.line);
                newAligned.add(e.line);
                kinds.add(LineKind.SAME);
                i++;
                continue;
            }

            // Collect a run of non-equal edits and pair deletes/inserts as "changes".
            List<String> dels = new ArrayList<>();
            List<String> ins = new ArrayList<>();

            while (i < edits.size() && edits.get(i).op != Op.EQUAL) {
                Edit r = edits.get(i);
                if (r.op == Op.DELETE) dels.add(r.line);
                if (r.op == Op.INSERT) ins.add(r.line);
                i++;
            }

            int max = Math.max(dels.size(), ins.size());
            for (int k = 0; k < max; k++) {
                String ol = k < dels.size() ? dels.get(k) : "";
                String nl = k < ins.size() ? ins.get(k) : "";

                oldAligned.add(ol);
                newAligned.add(nl);

                if (!ol.isEmpty() && !nl.isEmpty()) {
                    kinds.add(LineKind.CHANGE);
                } else if (!ol.isEmpty()) {
                    kinds.add(LineKind.DELETE);
                } else {
                    kinds.add(LineKind.INSERT);
                }
            }
        }

        return new AlignedDiff(oldAligned, newAligned, kinds);
    }

    /**
     * Splits text into lines preserving empty trailing line (if any).
     */
    private List<String> splitLinesPreserveEmpty(String text) {
        // \R is a linebreak matcher (Java 8+). -1 keeps trailing empties.
        String[] parts = text.split("\\R", -1);
        return List.of(parts);
    }

    /**
     * Myers diff (line-based) producing an edit script from a -> b.
     * Deterministic and dependency-free. Complexity O((N+M)D).
     */
    private List<Edit> myersDiff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int max = n + m;
        int offset = max;

        int[] v = new int[2 * max + 1];
        List<int[]> trace = new ArrayList<>(max + 1);

        v[offset + 1] = 0;

        int dFound = -1;

        for (int d = 0; d <= max; d++) {
            int[] vSnapshot = v.clone();
            trace.add(vSnapshot);

            for (int k = -d; k <= d; k += 2) {
                int idx = offset + k;
                int x;
                if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                    // insertion
                    x = v[idx + 1];
                } else {
                    // deletion
                    x = v[idx - 1] + 1;
                }
                int y = x - k;

                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }

                v[idx] = x;

                if (x >= n && y >= m) {
                    dFound = d;
                    break;
                }
            }
            if (dFound >= 0) break;
        }

        return backtrack(a, b, trace, dFound, offset);
    }

    private List<Edit> backtrack(List<String> a, List<String> b, List<int[]> trace, int dFound, int offset) {
        List<Edit> result = new ArrayList<>();

        int x = a.size();
        int y = b.size();

        for (int d = dFound; d >= 0; d--) {
            int[] v = trace.get(d);
            int k = x - y;
            int idx = offset + k;

            int prevK;
            if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }

            int prevIdx = offset + prevK;
            int prevX = v[prevIdx];
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                // equal (snake)
                result.add(new Edit(Op.EQUAL, a.get(x - 1)));
                x--;
                y--;
            }

            if (d == 0) break;

            if (x == prevX) {
                // insertion (came from k+1)
                result.add(new Edit(Op.INSERT, b.get(y - 1)));
                y--;
            } else {
                // deletion (came from k-1)
                result.add(new Edit(Op.DELETE, a.get(x - 1)));
                x--;
            }
        }

        // Reverse into forward order
        List<Edit> forward = new ArrayList<>(result.size());
        for (int i = result.size() - 1; i >= 0; i--) forward.add(result.get(i));
        return forward;
    }
}
