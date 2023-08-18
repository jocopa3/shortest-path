package shortestpath;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.SpriteID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.SplitFlagMap;

@PluginDescriptor(
    name = "Shortest Path",
    description = "Draws the shortest path to a chosen destination on the map (right click a spot on the world map to use)",
    tags = {"pathfinder", "map", "waypoint", "navigation"}
)
public class ShortestPathPlugin extends Plugin {
    protected static final String CONFIG_GROUP = "shortestpath";
    private static final String ADD_START = "Add start";
    private static final String ADD_END = "Add end";
    private static final String CLEAR = "Clear";
    private static final String PATH = ColorUtil.wrapWithColorTag("Path", JagexColors.MENU_TARGET);
    private static final String SET = "Set";
    private static final String START = ColorUtil.wrapWithColorTag("Start", JagexColors.MENU_TARGET);
    private static final String TARGET = ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET);
    private static final String TRANSPORT = ColorUtil.wrapWithColorTag("Transport", JagexColors.MENU_TARGET);
    private static final String WALK_HERE = "Walk here";
    private static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(ShortestPathPlugin.class, "/marker.png");

    @Inject
    private Client client;

    @Getter
    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private EventBus eventBus;

    @Inject
    private ShortestPathConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PathTileOverlay pathOverlay;

    @Inject
    private PathMinimapOverlay pathMinimapOverlay;

    @Inject
    private PathMapOverlay pathMapOverlay;

    @Inject
    private PathMapTooltipOverlay pathMapTooltipOverlay;

    @Inject
    private DebugOverlayPanel debugOverlayPanel;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private WorldMapPointManager worldMapPointManager;

    @Inject
    private WorldMapOverlay worldMapOverlay;

    private boolean initialized = false;

    private Point lastMenuOpenedPoint;
    private WorldMapPoint marker;
    private WorldPoint transportStart;
    private WorldPoint lastLocation = new WorldPoint(0, 0, 0);
    private MenuEntry lastClick;
    private Shape minimapClipFixed;
    private Shape minimapClipResizeable;
    private BufferedImage minimapSpriteFixed;
    private BufferedImage minimapSpriteResizeable;
    private Rectangle minimapRectangle = new Rectangle();

    private Timer debugTimerTask;
    private ExecutorService pathfindingExecutor;
    private Future<?> pathfinderFuture;
    private final Object pathfinderMutex = new Object();
    @Getter
    private Pathfinder pathfinder;
    private PathfinderConfig pathfinderConfig;

    @Getter
    private PluginPathManager pathManager;
    private ShortestPathAPI apiHandler;
    private final PluginIdentifier selfIdentifier = new PluginIdentifier(this);

    @Getter
    private ShortestPathPluginPanel pluginPanel;
    private NavigationButton navigationButton;

    @Provides
    public ShortestPathConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ShortestPathConfig.class);
    }

    @Override
    protected void startUp() {
        // First time setup
        if (!initialized) {
            SplitFlagMap map = SplitFlagMap.fromResources();
            Map<WorldPoint, List<Transport>> transports = Transport.loadAllFromResources();

            pathfinderConfig = new PathfinderConfig(map, transports, client, config);

            apiHandler = new ShortestPathAPI(this);

            // Manually subscribe to HashMap events so that shortest path can
            // still handle bookkeeping requests from other plugins while disabled
            eventBus.register(HashMap.class, (data) -> {
                if (apiHandler != null) {
                    apiHandler.parseDataMap(data);
                }
            }, 0);

            pathManager = new PluginPathManager();
            pathManager.addPlugin(selfIdentifier);

            pluginPanel = new ShortestPathPluginPanel(this);

            initialized = true;
        }

        overlayManager.add(pathOverlay);
        overlayManager.add(pathMinimapOverlay);
        overlayManager.add(pathMapOverlay);
        overlayManager.add(pathMapTooltipOverlay);

        if (config.drawDebugPanel()) {
            overlayManager.add(debugOverlayPanel);
        }

        final BufferedImage icon = MARKER_IMAGE;
        navigationButton = NavigationButton.builder()
            .tooltip(getName())
            .icon(icon)
            .priority(5)
            .panel(pluginPanel)
            .build();

        clientToolbar.addNavigation(navigationButton);

        // In-case the user toggled shortest path off then on, try refreshing the path
        PathParameters existingParameters = pathManager.getCurrentParameters();
        if (existingParameters != null) {
            setPath(existingParameters);
        }
    }

    @Override
    protected void shutDown() {
        cancelPathfinding();

        overlayManager.remove(pathOverlay);
        overlayManager.remove(pathMinimapOverlay);
        overlayManager.remove(pathMapOverlay);
        overlayManager.remove(pathMapTooltipOverlay);
        overlayManager.remove(debugOverlayPanel);

        if (pathfindingExecutor != null) {
            pathfindingExecutor.shutdownNow();
            pathfindingExecutor = null;
        }

        if (pathManager != null) {
            pathManager.shutDown();
            // Don't clear the reference in-case the user re-enables shortest path later
        }

        clientToolbar.removeNavigation(navigationButton);
    }

    private void createDebugTimer() {
        synchronized (pathfinderMutex) {
            if (debugTimerTask != null) {
                debugTimerTask.cancel();
            }

            debugTimerTask = new Timer();
            debugTimerTask.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    synchronized (pathfinderMutex) {
                        if (pathfinder == null || pathfinder.isDone()) {
                            debugTimerTask.cancel();
                            return;
                        }

                        if (config.debugPathfindingMode() != PathfinderDebugMode.OFF && pathfinder != null && !pathfinder.isDone() && !pathfinder.isCancelled()) {
                            pathfinder.debugStep();
                            if (!pathfinder.isActive()) {
                                pathfindingExecutor.submit(pathfinder);
                            }
                        }
                    }
                }
            }, config.debugPathfindingDelayMS(), config.debugPathfindingDelayMS());
        }
    }

    public void restartPathfinding(WorldPoint start, WorldPoint end) {
        synchronized (pathfinderMutex) {
            if (pathfinder != null) {
                pathfinder.cancel();
                pathfinderFuture.cancel(true);
                if (debugTimerTask != null) {
                    debugTimerTask.cancel();
                }
            }

            if (pathfindingExecutor == null) {
                pathfindingExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r);
                    thread.setName("shortest-path-thread");
                    return thread;
                });
            }

            if (config.debugPathfindingMode() != PathfinderDebugMode.OFF && debugTimerTask == null) {
                debugTimerTask = new Timer();
            }
        }

        getClientThread().invokeLater(() -> {
            pathfinderConfig.refresh();
            synchronized (pathfinderMutex) {
                pathfinder = new Pathfinder(pathfinderConfig, start, end);
                pathfinderFuture = pathfindingExecutor.submit(pathfinder);
            }
            createDebugTimer();
        });
    }

    public void cancelPathfinding() {
        synchronized (pathfinderMutex) {
            if (pathfinder != null) {
                pathfinder.cancel();
                pathfinder = null;
            }
        }

        if (marker != null) {
            worldMapPointManager.remove(marker);
            marker = null;
        }
    }

    public boolean isNearPath(WorldPoint location) {
        if (pathfinder == null || pathfinder.getPath() == null || pathfinder.getPath().isEmpty() ||
            config.recalculateDistance() < 0 || lastLocation.equals(lastLocation = location)) {
            return true;
        }

        for (WorldPoint point : pathfinder.getPath()) {
            if (location.distanceTo2D(point) < config.recalculateDistance()) {
                return true;
            }
        }

        return false;
    }

    private final Pattern TRANSPORT_OPTIONS_REGEX = Pattern.compile("^(avoidWilderness|use\\w+)$");

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        switch (event.getKey()) {
            case "drawDebugPanel":
                if (config.drawDebugPanel()) {
                    overlayManager.add(debugOverlayPanel);
                } else {
                    overlayManager.remove(debugOverlayPanel);
                }
                return;
            case "debugPathfindingMode":
                if (pathfinder != null) {
                    restartPathfinding(pathfinder.getStart(), pathfinder.getTarget());
                }
                return;
            case "debugPathfindingDelay":
                if (config.debugPathfindingMode() != PathfinderDebugMode.OFF && debugTimerTask != null) {
                    debugTimerTask.cancel();
                    createDebugTimer();
                }
                return;
        }

        // Transport option changed; rerun pathfinding
        if (TRANSPORT_OPTIONS_REGEX.matcher(event.getKey()).find()) {
            if (pathfinder != null) {
                restartPathfinding(pathfinder.getStart(), pathfinder.getTarget());
            }
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        Player localPlayer = client.getLocalPlayer();
        PathParameters currentParameters = pathManager.getCurrentParameters();
        if (localPlayer == null || pathfinder == null || currentParameters == null) {
            return;
        }

        WorldPoint currentLocation = client.isInInstancedRegion() ?
            WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()) : localPlayer.getWorldLocation();
        if (currentLocation.distanceTo(pathfinder.getTarget()) < config.reachedDistance()) {
            clearPath(currentParameters.getRequester());
            return;
        }

        if (!isStartSet() && !isNearPath(currentLocation)) {
            if (config.cancelInstead()) {
                clearPath(currentParameters.getRequester());
                return;
            }
            restartPathfinding(currentLocation, pathfinder.getTarget());
        }
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (client.isKeyPressed(KeyCode.KC_SHIFT) && event.getOption().equals(WALK_HERE) && event.getTarget().isEmpty()) {
            if (config.drawTransports()) {
                addMenuEntry(event, ADD_START, TRANSPORT, 1);
                addMenuEntry(event, ADD_END, TRANSPORT, 1);
                // addMenuEntry(event, "Copy Position");
            }

            addMenuEntry(event, SET, TARGET, 1);
            if (pathfinder != null) {
                if (pathfinder.getTarget() != null) {
                    addMenuEntry(event, SET, START, 1);
                }
                WorldPoint selectedTile = getSelectedWorldPoint();
                if (pathfinder.getPath() != null) {
                    for (WorldPoint tile : pathfinder.getPath()) {
                        if (tile.equals(selectedTile)) {
                            addMenuEntry(event, CLEAR, PATH, 1);
                            break;
                        }
                    }
                }
            }
        }

        final Widget map = client.getWidget(WidgetInfo.WORLD_MAP_VIEW);

        if (map != null && map.getBounds().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())) {
            addMenuEntry(event, SET, TARGET, 0);
            if (pathfinder != null) {
                if (pathfinder.getTarget() != null) {
                    addMenuEntry(event, SET, START, 0);
                    addMenuEntry(event, CLEAR, PATH, 0);
                }
            }
        }

        final Shape minimap = getMinimapClipArea();

        if (minimap != null && pathfinder != null &&
            minimap.contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())) {
            addMenuEntry(event, CLEAR, PATH, 0);
        }

        if (minimap != null && pathfinder != null &&
            ("Floating World Map".equals(Text.removeTags(event.getOption())) ||
             "Close Floating panel".equals(Text.removeTags(event.getOption())))) {
            addMenuEntry(event, CLEAR, PATH, 1);
        }
    }

    public Map<WorldPoint, List<Transport>> getTransports() {
        return pathfinderConfig.getTransports();
    }

    public CollisionMap getMap() {
        return pathfinderConfig.getMap();
    }

    private void onMenuOptionClicked(MenuEntry entry) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        WorldPoint currentLocation = client.isInInstancedRegion() ?
            WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()) : localPlayer.getWorldLocation();
        if (entry.getOption().equals(ADD_START) && entry.getTarget().equals(TRANSPORT)) {
            transportStart = currentLocation;
        }

        if (entry.getOption().equals(ADD_END) && entry.getTarget().equals(TRANSPORT)) {
            WorldPoint transportEnd = client.isInInstancedRegion() ?
                WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()) : localPlayer.getWorldLocation();
            System.out.println(transportStart.getX() + " " + transportStart.getY() + " " + transportStart.getPlane() + " " +
                    currentLocation.getX() + " " + currentLocation.getY() + " " + currentLocation.getPlane() + " " +
                    lastClick.getOption() + " " + Text.removeTags(lastClick.getTarget()) + " " + lastClick.getIdentifier()
            );
            Transport transport = new Transport(transportStart, transportEnd);
            pathfinderConfig.getTransports().computeIfAbsent(transportStart, k -> new ArrayList<>()).add(transport);
        }

        if (entry.getOption().equals("Copy Position")) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection("(" + currentLocation.getX() + ", "
                            + currentLocation.getY() + ", "
                            + currentLocation.getPlane() + ")"), null);
        }

        if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET)) {
            setTarget(selfIdentifier, getSelectedWorldPoint());
        }

        if (entry.getOption().equals(SET) && entry.getTarget().equals(START)) {
            setStart(selfIdentifier, getSelectedWorldPoint());
        }

        if (entry.getOption().equals(CLEAR) && entry.getTarget().equals(PATH)) {
            clearPath(selfIdentifier);
        }

        if (entry.getType() != MenuAction.WALK) {
            lastClick = entry;
        }
    }

    private WorldPoint getSelectedWorldPoint() {
        if (client.getWidget(WidgetInfo.WORLD_MAP_VIEW) == null) {
            if (client.getSelectedSceneTile() != null) {
                return client.isInInstancedRegion() ?
                    WorldPoint.fromLocalInstance(client, client.getSelectedSceneTile().getLocalLocation()) :
                    client.getSelectedSceneTile().getWorldLocation();
            }
        } else {
            return calculateMapPoint(client.isMenuOpen() ? lastMenuOpenedPoint : client.getMouseCanvasPosition());
        }
        return null;
    }

    private boolean isStartSet() {
        PathParameters currentParameters = pathManager.getCurrentParameters();
        return currentParameters != null && currentParameters.isStartSet();
    }

    // Internal convenience method not intended for other plugins
    private void setStart(PluginIdentifier requester, WorldPoint start) {
        PathParameters parameters = pathManager.getParameters(requester);
        if (parameters == null) {
            return;
        }

        parameters = new PathParameters(parameters.getRequester(), start, parameters.getTarget(), parameters.isMarkerHidden(), parameters.isVisible());
        requestPath(parameters);
    }

    // Internal convenience method not intended for other plugins
    private void setTarget(PluginIdentifier requester, WorldPoint target) {
        PathParameters parameters = pathManager.getParameters(requester);
        if (parameters == null) {
            parameters = new PathParameters(requester, null, target, config.debugPathfindingMode() != PathfinderDebugMode.OFF, true);
        } else {
            parameters = new PathParameters(requester, parameters.getStart(), target, parameters.isMarkerHidden(), parameters.isVisible());
        }

        requestPath(parameters);
    }

    public void clearPath(PluginIdentifier requester) {
        boolean currentParametersChanged = pathManager.clearPath(requester);
        pluginPanel.repaintAsync();

        if (currentParametersChanged) {
            PathParameters newCurrentParameters = pathManager.getCurrentParameters();
            if (newCurrentParameters != null) {
                setPath(newCurrentParameters);
            } else {
                cancelPathfinding();
            }
        }

    }

    public void requestPath(PathParameters parameters) {
        if (parameters == null) {
            return;
        }

        if (config.debugPathfindingMode() != PathfinderDebugMode.OFF) {
            parameters = new PathParameters(parameters.getRequester(), parameters.getStart(), parameters.getTarget(), true, parameters.isVisible());
        }

        boolean currentParametersChanged = pathManager.requestPath(parameters);
        pluginPanel.repaintAsync();

        if (!currentParametersChanged) {
            return;
        }

        PathParameters currentParameters = pathManager.getCurrentParameters();
        if (currentParameters == null) {
            cancelPathfinding();
            return;
        }

        setPath(currentParameters);
    }

    private void setPath(PathParameters parameters) {
        if (parameters == null) {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (!parameters.isStartSet() && localPlayer == null) {
            return;
        }

        if (!parameters.isMarkerHidden()) {
            worldMapPointManager.removeIf(x -> x == marker);
            marker = new WorldMapPoint(parameters.getTarget(), MARKER_IMAGE);
            marker.setName("Target");
            marker.setTarget(marker.getWorldPoint());
            marker.setJumpOnClick(true);
            worldMapPointManager.add(marker);
        }

        WorldPoint start = parameters.getStart();
        if (start == null) {
            start = client.isInInstancedRegion() ?
                    WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()) : localPlayer.getWorldLocation();
        }
        lastLocation = start;

        restartPathfinding(start, parameters.getTarget());
    }

    void setNewPriorities(List<PluginIdentifier> newPriorities) {
        if (pathManager.setNewPriorities(newPriorities)) {
            // Priority order changed; no need to re-request the path
            setPath(pathManager.getCurrentParameters());
        }
    }

    public WorldPoint calculateMapPoint(Point point) {
        WorldMap worldMap = client.getWorldMap();
        float zoom = worldMap.getWorldMapZoom();
        final WorldPoint mapPoint = new WorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
        final Point middle = mapWorldPointToGraphicsPoint(mapPoint);

        if (point == null || middle == null) {
            return null;
        }

        final int dx = (int) ((point.getX() - middle.getX()) / zoom);
        final int dy = (int) ((-(point.getY() - middle.getY())) / zoom);

        return mapPoint.dx(dx).dy(dy);
    }

    public Point mapWorldPointToGraphicsPoint(WorldPoint worldPoint) {
        WorldMap worldMap = client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = client.getWidget(WidgetInfo.WORLD_MAP_VIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
            int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
            int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
            int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

            int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
            int yGraphDiff = (int) (yTileOffset * pixelsPerTile);

            yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
            xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);

            yGraphDiff = worldMapRect.height - yGraphDiff;
            yGraphDiff += (int) worldMapRect.getY();
            xGraphDiff += (int) worldMapRect.getX();

            return new Point(xGraphDiff, yGraphDiff);
        }
        return null;
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position) {
        List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenuEntries()));

        if (entries.stream().anyMatch(e -> e.getOption().equals(option) && e.getTarget().equals(target))) {
            return;
        }

        client.createMenuEntry(position)
            .setOption(option)
            .setTarget(target)
            .setParam0(event.getActionParam0())
            .setParam1(event.getActionParam1())
            .setIdentifier(event.getIdentifier())
            .setType(MenuAction.RUNELITE)
            .onClick(this::onMenuOptionClicked);
    }

    private Widget getMinimapDrawWidget() {
        if (client.isResized()) {
            if (client.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                return client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_DRAW_AREA);
            }
            return client.getWidget(WidgetInfo.RESIZABLE_MINIMAP_STONES_DRAW_AREA);
        }
        return client.getWidget(WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
    }

    private Shape getMinimapClipAreaSimple() {
        Widget minimapDrawArea = getMinimapDrawWidget();

        if (minimapDrawArea == null || minimapDrawArea.isHidden()) {
            return null;
        }

        Rectangle bounds = minimapDrawArea.getBounds();

        return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

    public Shape getMinimapClipArea() {
        Widget minimapWidget = getMinimapDrawWidget();

        if (minimapWidget == null || minimapWidget.isHidden() || !minimapRectangle.equals(minimapRectangle = minimapWidget.getBounds())) {
            minimapClipFixed = null;
            minimapClipResizeable = null;
            minimapSpriteFixed = null;
            minimapSpriteResizeable = null;
        }

        if (client.isResized()) {
            if (minimapClipResizeable != null) {
                return minimapClipResizeable;
            }
            if (minimapSpriteResizeable == null) {
                minimapSpriteResizeable = spriteManager.getSprite(SpriteID.RESIZEABLE_MODE_MINIMAP_ALPHA_MASK, 0);
            }
            if (minimapSpriteResizeable != null) {
                minimapClipResizeable = bufferedImageToPolygon(minimapSpriteResizeable);
                return minimapClipResizeable;
            }
            return getMinimapClipAreaSimple();
        }
        if (minimapClipFixed != null) {
            return minimapClipFixed;
        }
        if (minimapSpriteFixed == null) {
            minimapSpriteFixed = spriteManager.getSprite(SpriteID.FIXED_MODE_MINIMAP_ALPHA_MASK, 0);
        }
        if (minimapSpriteFixed != null) {
            minimapClipFixed = bufferedImageToPolygon(minimapSpriteFixed);
            return minimapClipFixed;
        }
        return getMinimapClipAreaSimple();
    }

    private Polygon bufferedImageToPolygon(BufferedImage image) {
        Color outsideColour = null;
        Color previousColour;
        final int width = image.getWidth();
        final int height = image.getHeight();
        List<java.awt.Point> points = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            previousColour = outsideColour;
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb & 0xff000000) >>> 24;
                int r = (rgb & 0x00ff0000) >> 16;
                int g = (rgb & 0x0000ff00) >> 8;
                int b = (rgb & 0x000000ff) >> 0;
                Color colour = new Color(r, g, b, a);
                if (x == 0 && y == 0) {
                    outsideColour = colour;
                    previousColour = colour;
                }
                if (!colour.equals(outsideColour) && previousColour.equals(outsideColour)) {
                    points.add(new java.awt.Point(x, y));
                }
                if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour)) {
                    points.add(0, new java.awt.Point(x, y));
                }
                previousColour = colour;
            }
        }
        int offsetX = minimapRectangle.x;
        int offsetY = minimapRectangle.y;
        Polygon polygon = new Polygon();
        for (java.awt.Point point : points) {
            polygon.addPoint(point.x + offsetX, point.y + offsetY);
        }
        return polygon;
    }
}
