package de.ralfrosenkranz.codevibrator.ui;

import de.ralfrosenkranz.codevibrator.importer.ImportPlan;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class ImportPlanDialog extends JDialog {
    private boolean confirmed = false;

    public ImportPlanDialog(Window owner, Path zip, ImportPlan plan) {
        super(owner, "Import Preview (display only)", ModalityType.APPLICATION_MODAL);
        setSize(900, 600);
        setLocationRelativeTo(owner);

        JLabel top = new JLabel("Zip: " + zip.getFileName() + " (No overrides; Confirm/Cancel only)");
        top.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        JTable table = new JTable(new PlanModel(plan.items));
        table.setFillsViewportHeight(true);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int r = table.getSelectedRow();
                    if (r < 0) return;
                    int modelRow = table.convertRowIndexToModel(r);
                    ImportPlan.Item it = plan.items.get(modelRow);
                    if (it.kind == ImportPlan.Kind.CHANGED && it.text && it.oldBytes != null && it.newBytes != null) {
                        DiffDialog dd = new DiffDialog(owner, "Diff: " + it.target.getFileName(), it.oldBytes, it.newBytes);
                        dd.setVisible(true);
                    }
                }
            }
        });


        JButton confirm = new JButton("Confirm Import");
        JButton cancel = new JButton("Cancel");
        confirm.addActionListener(e -> { confirmed = true; dispose(); });
        cancel.addActionListener(e -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);
        south.add(confirm);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() { return confirmed; }

    private static class PlanModel extends AbstractTableModel {
        private final List<ImportPlan.Item> items;
        private final String[] cols = {"Target", "Kind", "Text", "Note"};

        PlanModel(List<ImportPlan.Item> items) {
            this.items = items;
        }

        @Override public int getRowCount() { return items.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ImportPlan.Item it = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> it.target.toString();
                case 1 -> it.kind.name();
                case 2 -> it.text ? "yes" : "no";
                case 3 -> it.note;
                default -> "";
            };
        }
    }
}
