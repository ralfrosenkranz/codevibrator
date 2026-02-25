package de.ralfrosenkranz.codevibrator.ui;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DirTreeModel extends DefaultTreeModel {
    public record DirNode(Path path) {}

    public DirTreeModel(Path root) {
        super(new DefaultMutableTreeNode(new DirNode(root)));
        buildRecursively((DefaultMutableTreeNode) getRoot());
    }

    public void buildRecursively(DefaultMutableTreeNode node) {
        node.removeAllChildren();
        DirNode dn = (DirNode) node.getUserObject();
        try {
            List<Path> dirs = Files.list(dn.path)
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".git"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
            for (Path d : dirs) {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new DirNode(d));
                node.add(child);
                buildRecursively(child);
            }
        } catch (IOException ignored) {}
        nodeStructureChanged(node);
    }
}
