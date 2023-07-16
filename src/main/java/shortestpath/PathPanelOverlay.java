package shortestpath;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import shortestpath.pathfinder.Pathfinder;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.stream.Collectors;

public class PathPanelOverlay extends OverlayPanel {
    private final ShortestPathPlugin plugin;

    @Inject
    public PathPanelOverlay(ShortestPathPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        this.plugin = plugin;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Pathfinder pathfinder = plugin.getPathfinder();
        if (pathfinder == null || !pathfinder.isDone()) {
            return null;
        }

        List<String> actions = pathfinder.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        List<LayoutableRenderableEntity> children = panelComponent.getChildren();
        children.add(TitleComponent.builder().text("Actions").build());

        for (int i = 0; i < actions.size(); ++i) {
            children.add(LineComponent.builder().left(i + ": " + actions.get(i)).build());
        }

        return super.render(graphics);
    }
}
