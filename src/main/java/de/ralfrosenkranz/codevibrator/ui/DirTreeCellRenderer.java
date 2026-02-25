package de.ralfrosenkranz.codevibrator.ui;

import de.ralfrosenkranz.codevibrator.persist.ConfigService;
import de.ralfrosenkranz.codevibrator.selectors.ResolvedDirRules;
import de.ralfrosenkranz.codevibrator.selectors.SelectorResolver;
import de.ralfrosenkranz.codevibrator.ui.icons.Icons;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DirTreeCellRenderer extends DefaultTreeCellRenderer {
    private final ConfigService config;
    private final SelectorResolver resolver;
    private String profile = "default";

    private static final Color OVERRIDE_COLOR = new Color(0x2D6CDF);
    private static final Color EXCLUDED_COLOR = new Color(0x777777);
    private static final Color READONLY_COLOR = new Color(0xB55B5B);

    private static final class CacheEntry {
        final long lastModified;
        final boolean hasOverrides;
        CacheEntry(long lastModified, boolean hasOverrides) {
            this.lastModified = lastModified;
            this.hasOverrides = hasOverrides;
        }
    }

    private final Map<Path, CacheEntry> overrideCache = new ConcurrentHashMap<>();

    public DirTreeCellRenderer(ConfigService config) {
        this.config = config;
        this.resolver = new SelectorResolver(config);
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof DirTreeModel.DirNode dn) {
            Path p = dn.path();
            setText(p.getFileName() == null ? p.toString() : p.getFileName().toString());

            ResolvedDirRules rules = resolver.resolveRules(p, profile);
            if (rules.excluded()) {
                setIcon(Icons.excluded());
                setForeground(EXCLUDED_COLOR);
            } else if (rules.readonlyDir()) {
                setIcon(Icons.lock());
                setForeground(READONLY_COLOR);
            }

// Highlight directories that have a local .code.vibrator with inheritance-relevant overrides.
if (!rules.excluded() && !rules.readonlyDir()) {
    boolean hasOverrides = hasOverridesCached(p);
    if (hasOverrides) {
        setForeground(OVERRIDE_COLOR);
        setFont(getFont().deriveFont(Font.BOLD));
        setToolTipText(".code.vibrator overrides in this directory");
    } else {
        setToolTipText(null);
    }
}
        }
        return this;
    }



private boolean hasOverridesCached(Path dir) {
    try {
        Path f = dir.resolve(".code.vibrator");
        if (!Files.exists(f)) {
            overrideCache.remove(dir);
            return false;
        }
        long lm = Files.getLastModifiedTime(f).toMillis();
        CacheEntry ce = overrideCache.get(dir);
        if (ce != null && ce.lastModified == lm) return ce.hasOverrides;

        boolean has = config.hasInheritanceOverrides(dir) != null;
        overrideCache.put(dir, new CacheEntry(lm, has));
        return has;
    } catch (Exception e) {
        // conservative: if we cannot stat/parse, highlight
        return true;
    }
}

}
