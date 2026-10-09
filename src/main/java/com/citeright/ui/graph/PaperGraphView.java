package com.citeright.ui.graph;

import com.citeright.model.LibraryEntry;
import com.citeright.service.LibraryService;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Root {@link BorderPane} shell for the Macro Paper Graph view (Phase 1).
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │  Header (title · view switcher · search field)          │
 * ├───────────┬─────────────────────────────┬───────────────┤
 * │ Filter    │                             │  InspectorPanel│
 * │ rail      │   Canvas (CENTER, resizable)│  (shown on    │
 * │ (200 px)  │                             │   node select)│
 * └───────────┴─────────────────────────────┴───────────────┘
 * </pre>
 *
 * <h3>Interaction model</h3>
 * <ul>
 *   <li><b>Press</b> — hit-test nodes; select / deselect.</li>
 *   <li><b>Drag on node</b> — temporary move; commits on release.</li>
 *   <li><b>Drag on background</b> — pan camera.</li>
 *   <li><b>Double-click on node</b> — animated centre-on-node.</li>
 *   <li><b>Right-click on node</b> — relationship context menu (stubs).</li>
 *   <li><b>Shift+drag on background</b> — multi-select rubber-band.</li>
 *   <li><b>Scroll wheel</b> — cursor-anchored zoom.</li>
 *   <li><b>Hover</b> — node expansion + halo (no inspector).</li>
 * </ul>
 *
 * <h3>Animation</h3>
 * One {@link AnimationTimer} that is active <em>only</em> while
 * {@code selectedNode != null} or the cluster shimmer needs updating.
 * Otherwise the graph is frozen (no idle physics).
 */
public final class PaperGraphView extends BorderPane {

    // ── Core layer ──────────────────────────────────────────────────────────
    private final LibraryService           libraryService;
    private final GraphModel               model;
    private final HierarchicalClusterLayout layout;
    private final EdgeRouter               router;
    private final GraphCamera              camera;
    private final GraphRenderer            renderer;
    
    // UI Elements for Progressive Loading
    private final VBox                     loadingBox;
    private final Label                    loadingLabel;
    private final ProgressBar              loadingProgress;
    private final InspectorPanel           inspector;
    private final FloatingPreviewCard      previewCard;
    private VBox                           edgeTooltip;

    // ── Canvas ──────────────────────────────────────────────────────────────
    private final Canvas canvas = new Canvas();

    // ── Interaction state ───────────────────────────────────────────────────
    private GraphNode selectedNode   = null;
    private GraphNode hoveredNode    = null;
    private GraphEdge hoveredEdge    = null;
    private GraphNode previewNode    = null;
    private GraphNode dragNode       = null;
    private double    dragOffX, dragOffY;        // world-space offset for node drag
    private double    panStartX, panStartY;      // screen-space anchor for pan
    private double    panOffsetStartX, panOffsetStartY;
    private boolean   isPanDrag      = false;
    private boolean   isNodeDrag     = false;
    private boolean   shiftDrag      = false;
    private double    rubberX0, rubberY0;        // rubber-band world-space start

    // ── Animation ───────────────────────────────────────────────────────────
    private final AnimationTimer animTimer;
    private double nowSec = 0;
    private long   lastNanos = -1;

    // ── Callback (optional companion for MainLayout DetailPanel) ─────────────
    private Consumer<LibraryEntry> onSelectEntry;

    // ── Filter state mirrors (for filter rail ↔ model sync) ─────────────────
    private double simThreshold = 0.08;
    private boolean showSim     = true;
    private boolean showAI      = false;
    private boolean showCurated = true;

    // ── Build flag (prevents double rebuild) ────────────────────────────────
    private volatile boolean building = false;

    public PaperGraphView(LibraryService libraryService) {
        this.libraryService = libraryService;
        this.model    = new GraphModel(libraryService);
        this.layout   = new HierarchicalClusterLayout(model);
        this.router   = new EdgeRouter(model, layout);
        this.camera   = new GraphCamera();
        this.renderer = new GraphRenderer(model, layout, router, camera);
        this.inspector = new InspectorPanel(libraryService, model);

        // Wire clickable paper names in inspector → camera animation + selection.
        this.inspector.setOnNavigateToNode(target -> {
            selectNode(target);
            camera.animateTo(target.x, target.y, Math.max(camera.getZoom(), 1.2), 600, null);
            ensureAnimation();
        });

        // ── Header ──────────────────────────────────────────────────────────────
        setTop(buildHeader());

        // ── Loading UI ──────────────────────────────────────────────────────────
        loadingLabel = new Label("Loading Graph...");
        loadingLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        loadingProgress = new ProgressBar(0);
        loadingProgress.setPrefWidth(200);
        
        loadingBox = new VBox(10, loadingLabel, loadingProgress);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-background-radius: 8;");
        loadingBox.setMaxSize(250, 80);
        loadingBox.setVisible(false);

        // ── Left filter rail ───────────────────────────────────────────────
        setLeft(buildFilterRail());

        // ── Edge Hover Tooltip ─────────────────────────────────────────────
        edgeTooltip = new VBox(4);
        edgeTooltip.setStyle("-fx-background-color: rgba(14,14,34,0.92);"
                + "-fx-border-color: rgba(255,255,255,0.15);"
                + "-fx-border-width: 1; -fx-background-radius: 8;"
                + "-fx-border-radius: 8; -fx-padding: 10;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0.2, 0, 4);");
        edgeTooltip.setMaxSize(240, Region.USE_PREF_SIZE);
        edgeTooltip.setVisible(false);
        edgeTooltip.setMouseTransparent(true);

        // ── Preview Card ───────────────────────────────────────────────────
        previewCard = new FloatingPreviewCard();

        // ── Centre canvas ──────────────────────────────────────────────────
        StackPane canvasHost = new StackPane(canvas, loadingBox, edgeTooltip, previewCard);
        StackPane.setAlignment(previewCard, Pos.TOP_LEFT);
        canvasHost.setStyle("-fx-background-color: " + Theme.BG_HEX + ";");
        setCenter(canvasHost);

        // Bind canvas size to its host.
        canvas.widthProperty() .bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());
        canvas.widthProperty() .addListener(e -> { camera.setViewport(canvas.getWidth(), canvas.getHeight()); redraw(); });
        canvas.heightProperty().addListener(e -> { camera.setViewport(canvas.getWidth(), canvas.getHeight()); redraw(); });

        // ── Interaction ────────────────────────────────────────────────────
        wireInteraction();

        // ── Animation timer ────────────────────────────────────────────────
        animTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNanos < 0) { lastNanos = now; }
                double dt = (now - lastNanos) / 1_000_000_000.0;
                nowSec += dt;
                lastNanos = now;
                redraw();
                // Stop if nothing needs animation.
                if (selectedNode == null && hoveredNode == null && !camera.isAnimating()) {
                    stop();
                    lastNanos = -1;
                }
            }
        };

        // Initial placeholder draw.
        redraw();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Rebuild the graph model, run layout, and route edges progressively.
     */
    public void buildGraph() {
        if (building) return;
        building = true;
        inspector.showEmpty();
        deselect(false);
        
        loadingBox.setVisible(true);
        loadingLabel.setText("Starting...");
        loadingProgress.setProgress(0);

        ProgressiveGraphLoader.buildGraphAsync(
                model, layout, router, camera, 
                canvas.getWidth(), canvas.getHeight(),
                (stageName, progress, canDrawPartial) -> {
                    loadingLabel.setText(stageName);
                    loadingProgress.setProgress(progress);
                    if (canDrawPartial) {
                        redraw();
                    }
                },
                () -> {
                    building = false;
                    loadingBox.setVisible(false);
                    redraw();
                }
        );
    }

    /**
     * Auto-fit the camera so all nodes are visible with 15% margin.
     * Calculates the optimal zoom and offset from the actual node bounds.
     */
    private void autoFitCamera() {
        java.util.List<GraphNode> nodes = model.getNodes();
        if (nodes.isEmpty()) {
            camera.centreOnWorld(0, 0);
            camera.setZoom(0.9);
            return;
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GraphNode n : nodes) {
            double r = NodeRenderer.radius(n) + 40; // account for node radius + title chip
            if (n.x - r < minX) minX = n.x - r;
            if (n.x + r > maxX) maxX = n.x + r;
            if (n.y - r < minY) minY = n.y - r;
            if (n.y + r > maxY) maxY = n.y + r;
        }

        double worldW = maxX - minX;
        double worldH = maxY - minY;
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;

        double viewW = canvas.getWidth();
        double viewH = canvas.getHeight();
        if (viewW < 100 || viewH < 100) {
            camera.centreOnWorld(centerX, centerY);
            camera.setZoom(0.5);
            return;
        }

        // Compute zoom to fit with 15% margin on each side (so 70% of viewport used).
        double zoomX = (viewW * 0.70) / worldW;
        double zoomY = (viewH * 0.70) / worldH;
        double zoom = Math.max(GraphCamera.MIN_ZOOM, Math.min(1.2, Math.min(zoomX, zoomY)));

        camera.setZoom(zoom);
        camera.centreOnWorld(centerX, centerY);
    }

    /** Optional callback fired when the user selects a node (for MainLayout). */
    public void setOnSelectEntry(Consumer<LibraryEntry> cb) {
        this.onSelectEntry = cb;
    }

    // ── Header ──────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 16, 8, 16));
        header.setStyle("-fx-background-color: " + Theme.PANEL_HEX + ";"
                + "-fx-border-color: " + Theme.BORDER_HEX + "; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("CiteRight — Next-Gen Paper Graph");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        Label subtitle = new Label("Beautiful. Organized. Intelligent. Built for Researchers.");
        subtitle.setStyle("-fx-text-fill: " + Theme.TEXT_FAINT_HEX + "; -fx-font-size: 10px;");
        titleBox.getChildren().addAll(title, subtitle);

        // Feature pills
        HBox pills = new HBox(6);
        pills.setAlignment(Pos.CENTER_LEFT);
        pills.getChildren().addAll(
                featurePill("✨ NO OVERLAP"),
                featurePill("🎨 MEANINGFUL COLORS"),
                featurePill("🌌 CLUSTERED INSIGHTS"),
                featurePill("⚡ INTERACTIVE & FAST")
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // View switcher.
        ToggleGroup viewGroup = new ToggleGroup();
        ToggleButton macro  = viewBtn("Macro",    viewGroup, true);
        ToggleButton meso   = viewBtn("Meso",     viewGroup, false);
        ToggleButton micro  = viewBtn("Micro",    viewGroup, false);
        ToggleButton timeline = viewBtn("Timeline", viewGroup, false);
        
        viewGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
            } else if (newVal != macro) {
                // Temporarily show alert for WIP views
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Coming Soon");
                alert.setHeaderText("View Mode Coming in Phase 2/3");
                alert.setContentText("The " + ((ToggleButton)newVal).getText() + " view is scheduled for implementation in Phase 2/3. Stay tuned!");
                alert.showAndWait();
                macro.setSelected(true);
            }
        });
        
        HBox switcher = new HBox(2, macro, meso, micro, timeline);
        switcher.setStyle("-fx-background-color: rgba(255,255,255,0.06);"
                + "-fx-background-radius: 6; -fx-padding: 3;");

        // Active Search field (camera animates to matching paper!).
        TextField search = new TextField();
        search.setPromptText("🔍 Search graph…");
        search.setPrefWidth(180);
        search.setStyle("-fx-background-color: rgba(255,255,255,0.08);"
                + "-fx-text-fill: white; -fx-prompt-text-fill: #5a5a8a;"
                + "-fx-background-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px;");

        search.setOnAction(e -> triggerSearch(search.getText()));
        search.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() >= 3) {
                triggerSearch(newVal);
            }
        });

        header.getChildren().addAll(titleBox, pills, spacer, switcher, search);
        header.setMinWidth(0); // Allow header to shrink so it doesn't push the sidebar off-screen
        return header;
    }

    private static Label featurePill(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: rgba(74,156,247,0.12);"
                + "-fx-text-fill: " + Theme.ACCENT_HEX + ";"
                + "-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 3 7; -fx-background-radius: 10;");
        l.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return l;
    }

    private void triggerSearch(String query) {
        if (query == null || query.isBlank()) return;
        String q = query.toLowerCase().trim();
        for (GraphNode node : model.getNodes()) {
            boolean match = (node.title != null && node.title.toLowerCase().contains(q))
                    || (node.entry != null && node.entry.getPublication() != null
                        && node.entry.getPublication().getAuthorsShort().toLowerCase().contains(q));
            if (match) {
                selectNode(node);
                camera.animateTo(node.x, node.y, 1.5, 600, null);
                ensureAnimation();
                break;
            }
        }
    }

    private static ToggleButton viewBtn(String text, ToggleGroup group, boolean selected) {
        ToggleButton tb = new ToggleButton(text);
        tb.setToggleGroup(group);
        tb.setSelected(selected);
        tb.setMinWidth(Region.USE_PREF_SIZE);
        tb.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaaacc;"
                + "-fx-font-size: 11px; -fx-padding: 4 12; -fx-background-radius: 4;");
        tb.selectedProperty().addListener((obs, was, now) -> {
            if (now) tb.setStyle("-fx-background-color: rgba(74,156,247,0.25);"
                    + "-fx-text-fill: white; -fx-font-size: 11px;"
                    + "-fx-padding: 4 12; -fx-background-radius: 4;");
            else     tb.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaaacc;"
                    + "-fx-font-size: 11px; -fx-padding: 4 12; -fx-background-radius: 4;");
        });
        return tb;
    }

    // ── Filter rail ─────────────────────────────────────────────────────────

    private VBox buildFilterRail() {
        VBox rail = new VBox(12);
        rail.setPadding(new Insets(14));
        rail.setPrefWidth(200);
        rail.setStyle("-fx-background-color: " + Theme.PANEL_2_HEX + ";"
                + "-fx-border-color: " + Theme.BORDER_HEX + "; -fx-border-width: 0 1 0 0;");

        Label filterTitle = new Label("FILTERS");
        filterTitle.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 9px; -fx-font-weight: bold;");

        // Similarity threshold.
        Label simLbl = new Label("Similarity threshold");
        simLbl.setStyle("-fx-text-fill: #aaaacc; -fx-font-size: 10px;");
        Slider simSlider = new Slider(0.05, 0.5, simThreshold);
        simSlider.setShowTickLabels(false);
        simSlider.setStyle("-fx-control-inner-background: transparent;");
        Label simVal = new Label(pct(simThreshold));
        simVal.setStyle("-fx-text-fill: " + Theme.ACCENT_HEX + "; -fx-font-size: 10px;");
        simSlider.valueProperty().addListener((obs, old, val) -> {
            simThreshold = val.doubleValue();
            simVal.setText(pct(simThreshold));
        });

        // Edge type toggles.
        Label typeTitle = new Label("EDGE TYPES");
        typeTitle.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 9px; -fx-font-weight: bold;");

        CheckBox cbSim     = filterCheck("Similarity",  showSim,     v -> showSim = v);
        CheckBox cbAI      = filterCheck("AI Suggested", showAI,     v -> showAI = v);
        CheckBox cbCurated = filterCheck("Curated",     showCurated, v -> showCurated = v);

        // Relationship-type chips.
        Label relTitle = new Label("RELATIONSHIP TYPE");
        relTitle.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 9px; -fx-font-weight: bold;");

        Set<RelationshipType> typeFilter = EnumSet.noneOf(RelationshipType.class);
        FlowPane typeChips = new FlowPane(4, 4);
        for (RelationshipType rt : RelationshipType.values()) {
            if (rt == RelationshipType.RELATED) continue;
            ToggleButton chip = typeChip(rt);
            chip.setOnAction(e -> {
                if (chip.isSelected()) typeFilter.add(rt);
                else typeFilter.remove(rt);
            });
            typeChips.getChildren().add(chip);
        }

        // Rebuild button.
        Button rebuild = new Button("↺  Rebuild Graph");
        rebuild.setStyle("-fx-background-color: rgba(74,156,247,0.18);"
                + "-fx-text-fill: " + Theme.ACCENT_HEX + "; -fx-font-size: 11px;"
                + "-fx-padding: 7 14; -fx-background-radius: 6; -fx-cursor: hand;");
        rebuild.setOnAction(e -> {
            model.setSimilarityThreshold(simThreshold);
            model.setShowSimilarity(showSim);
            model.setShowAI(showAI);
            model.setShowCurated(showCurated);
            model.setTypeFilter(typeFilter);
            buildGraph();
        });

        // Legend.
        VBox legend = buildLegend();

        rail.getChildren().addAll(
                filterTitle, simLbl, simSlider, simVal,
                new Separator(), typeTitle, cbSim, cbAI, cbCurated,
                new Separator(), relTitle, typeChips,
                new Separator(), rebuild,
                new Separator(), legend
        );
        return rail;
    }

    private static CheckBox filterCheck(String label, boolean initial,
                                        java.util.function.Consumer<Boolean> setter) {
        CheckBox cb = new CheckBox(label);
        cb.setSelected(initial);
        cb.setStyle("-fx-text-fill: #aaaacc; -fx-font-size: 10px;");
        cb.selectedProperty().addListener((obs, was, now) -> setter.accept(now));
        return cb;
    }

    private static ToggleButton typeChip(RelationshipType rt) {
        ToggleButton tb = new ToggleButton(rt.getLabel());
        String hex = toHex(rt.getColor());
        tb.setStyle("-fx-background-color: " + hex + "22;"
                + "-fx-text-fill: " + hex + ";"
                + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
        tb.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        
        // Handle selection style manually if needed, or let JavaFX handle it.
        tb.selectedProperty().addListener((obs, was, now) -> {
            if (now) {
                tb.setStyle("-fx-background-color: " + hex + "66;"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
            } else {
                tb.setStyle("-fx-background-color: " + hex + "22;"
                        + "-fx-text-fill: " + hex + ";"
                        + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
            }
        });
        return tb;
    }

    private static VBox buildLegend() {
        VBox box = new VBox(4);
        Label title = new Label("LEGEND");
        title.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 9px; -fx-font-weight: bold;");
        box.getChildren().add(title);
        for (RelationshipType rt : RelationshipType.values()) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            Region dot = new Region();
            dot.setMinSize(8, 8); dot.setMaxSize(8, 8);
            dot.setStyle("-fx-background-color: " + toHex(rt.getColor()) + ";"
                    + "-fx-background-radius: 4;");
            Label lbl = new Label(rt.getLabel());
            lbl.setStyle("-fx-text-fill: #6a6a8a; -fx-font-size: 9px;");
            row.getChildren().addAll(dot, lbl);
            box.getChildren().add(row);
        }
        return box;
    }

    // ── Canvas interaction ──────────────────────────────────────────────────

    private void wireInteraction() {
        canvas.setFocusTraversable(true);

        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);
        canvas.setOnMouseClicked(this::onMouseClicked);
        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMouseExited(e -> clearHover());
        canvas.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.10 : 1.0 / 1.10;
            camera.zoomAt(e.getX(), e.getY(), factor);
            ensureAnimation();
            redraw();
        });
    }

    private void onMousePressed(MouseEvent e) {
        canvas.requestFocus();
        double wx = camera.screenToWorldX(e.getX());
        double wy = camera.screenToWorldY(e.getY());
        GraphNode hit = hitTest(wx, wy);

        if (e.getButton() == MouseButton.SECONDARY && hit != null) {
            showContextMenu(hit, e.getScreenX(), e.getScreenY());
            return;
        }

        if (hit != null) {
            if (!hit.selected || previewNode == null && inspector.getParent() == null) {
                previewNode(hit);
            }
            // Begin node drag.
            dragNode   = hit;
            dragOffX   = wx - hit.x;
            dragOffY   = wy - hit.y;
            isNodeDrag = true;
            isPanDrag  = false;
        } else {
            // Begin pan or rubber-band.
            if (e.isShiftDown()) {
                shiftDrag  = true;
                rubberX0   = wx;
                rubberY0   = wy;
                isPanDrag  = false;
                isNodeDrag = false;
            } else {
                isPanDrag    = true;
                isNodeDrag   = false;
                shiftDrag    = false;
                panStartX    = e.getX();
                panStartY    = e.getY();
                panOffsetStartX = camera.getOffsetX();
                panOffsetStartY = camera.getOffsetY();
                canvas.setCursor(Cursor.CLOSED_HAND);
            }
            // Deselect only if clicking empty space (not shift).
            if (!e.isShiftDown()) deselect(true);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        double wx = camera.screenToWorldX(e.getX());
        double wy = camera.screenToWorldY(e.getY());

        if (isNodeDrag && dragNode != null) {
            dragNode.x = wx - dragOffX;
            dragNode.y = wy - dragOffY;
            router.invalidate();
            ensureAnimation();
            redraw();
        } else if (isPanDrag) {
            double dx = e.getX() - panStartX;
            double dy = e.getY() - panStartY;
            camera.setOffset(panOffsetStartX + dx, panOffsetStartY + dy);
            redraw();
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if (isNodeDrag) {
            // Commit drag — re-route edges.
            router.computeAll();
            redraw();
        }
        canvas.setCursor(Cursor.DEFAULT);
        isNodeDrag = false;
        isPanDrag  = false;
        shiftDrag  = false;
        dragNode   = null;
    }

    private void onMouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            previewCard.hide();
            double wx = camera.screenToWorldX(e.getX());
            double wy = camera.screenToWorldY(e.getY());
            GraphNode hit = hitTest(wx, wy);
            if (hit != null) {
                selectNode(hit);
                camera.animateTo(hit.x, hit.y, Math.max(camera.getZoom(), 1.4), 500, null);
                ensureAnimation();
            }
        } else if (e.getClickCount() == 1) {
            double wx = camera.screenToWorldX(e.getX());
            double wy = camera.screenToWorldY(e.getY());
            GraphNode hit = hitTest(wx, wy);
            if (hit != null) {
                String atAGlance = InspectorPanel.computeAtAGlance(hit, model.edgesFor(hit));
                previewCard.show(hit, atAGlance, n -> {
                    selectNode(n);
                    camera.animateTo(n.x, n.y, Math.max(camera.getZoom(), 1.4), 500, null);
                    ensureAnimation();
                });
                
                double px = e.getX() + 15;
                double py = e.getY() + 15;
                if (px + 300 > canvas.getWidth()) px = e.getX() - 315;
                if (py + 150 > canvas.getHeight()) py = canvas.getHeight() - 150;
                
                previewCard.setTranslateX(px);
                previewCard.setTranslateY(py);
            } else {
                previewCard.hide();
            }
        }
    }

    private void onMouseMoved(MouseEvent e) {
        double wx = camera.screenToWorldX(e.getX());
        double wy = camera.screenToWorldY(e.getY());
        GraphNode hit = hitTest(wx, wy);

        if (hit != hoveredNode) {
            if (hoveredNode != null) hoveredNode.hovered = false;
            hoveredNode = hit;
            if (hit != null) {
                hit.hovered = true;
                if (hoveredEdge != null) {
                    hoveredEdge.hovered = false;
                    hoveredEdge = null;
                }
                edgeTooltip.setVisible(false);
                canvas.setCursor(Cursor.HAND);
            } else {
                canvas.setCursor(Cursor.DEFAULT);
            }
            redraw();
        }

        if (hit == null) {
            GraphEdge hitEdge = hitTestEdge(wx, wy, camera.getZoom());
            if (hitEdge != hoveredEdge) {
                if (hoveredEdge != null) hoveredEdge.hovered = false;
                hoveredEdge = hitEdge;
                if (hoveredEdge != null) {
                    hoveredEdge.hovered = true;
                    canvas.setCursor(Cursor.HAND);
                    showEdgeTooltip(hoveredEdge, e.getX(), e.getY());
                } else {
                    canvas.setCursor(Cursor.DEFAULT);
                    edgeTooltip.setVisible(false);
                }
                redraw();
            } else if (hoveredEdge != null) {
                // Keep tooltip near mouse
                positionTooltip(e.getX(), e.getY());
            }
        }
    }

    private void clearHover() {
        boolean changed = false;
        if (hoveredNode != null) {
            hoveredNode.hovered = false;
            hoveredNode = null;
            changed = true;
        }
        if (hoveredEdge != null) {
            hoveredEdge.hovered = false;
            hoveredEdge = null;
            changed = true;
        }
        if (changed) {
            canvas.setCursor(Cursor.DEFAULT);
            edgeTooltip.setVisible(false);
            redraw();
        }
    }

    // ── Right-click context menu ─────────────────────────────────────────────

    private void showContextMenu(GraphNode node, double screenX, double screenY) {
        ContextMenu menu = new ContextMenu();

        MenuItem addRel  = new MenuItem("➕  Add Relationship");
        MenuItem aiInfer = new MenuItem("🤖  AI-Infer Relationships");
        MenuItem edit    = new MenuItem("✏  Edit Paper");
        MenuItem dismiss = new MenuItem("✕  Dismiss node");

        addRel.setOnAction(e  -> showAddRelStub(node));
        aiInfer.setOnAction(e -> showAiInferStub(node));
        edit.setOnAction(e    -> { /* open DetailPanel — Phase 3 */ });
        dismiss.setOnAction(e -> { /* filter out node — Phase 3 */ });

        menu.getItems().addAll(addRel, aiInfer, new SeparatorMenuItem(), edit, dismiss);
        menu.show(canvas, screenX, screenY);
    }

    private void showAddRelStub(GraphNode node) {
        Alert a = new Alert(Alert.AlertType.INFORMATION,
                "Add Relationship UI coming in Phase 2.\nSelected node: " + truncate(node.title, 40));
        a.setHeaderText("Add Relationship");
        a.showAndWait();
    }

    private void showAiInferStub(GraphNode node) {
        Alert a = new Alert(Alert.AlertType.INFORMATION,
                "AI-inference triggers will be wired in Phase 2.\nNode: " + truncate(node.title, 40));
        a.setHeaderText("AI-Infer Relationships");
        a.showAndWait();
    }

    // ── Selection ───────────────────────────────────────────────────────────

    private void previewNode(GraphNode node) {
        applySelectionHighlight(node);
        selectedNode = node;
        previewNode = node;
        
        // Hide full inspector, show preview
        setRight(null);
        ensureAnimation();
        redraw();
    }

    private void selectNode(GraphNode node) {
        applySelectionHighlight(node);
        selectedNode = node;
        previewNode = null;

        if (node != null) {
            inspector.show(node);
            setRight(inspector);
            ensureAnimation();
            if (onSelectEntry != null && node.entry != null) {
                onSelectEntry.accept(node.entry);
            }
        } else {
            setRight(null);
        }
        redraw();
    }

    private void applySelectionHighlight(GraphNode node) {
        // Deselect all nodes.
        for (GraphNode n : model.getNodes()) {
            n.selected = false;
            n.dimmed   = (node != null);
        }
        // Dim all edges; un-dim incident ones.
        for (GraphEdge e : model.getEdges()) {
            e.selected = false;
            e.dimmed   = (node != null);
        }
        if (node != null) {
            node.selected = true;
            node.dimmed   = false;
            for (GraphEdge e : model.edgesFor(node)) {
                e.selected = true;
                e.dimmed   = false;
                e.other(node).dimmed = false;
            }
        }
    }

    private void deselect(boolean animate) {
        selectNode(null);
        if (animate) setRight(null);
    }

    // ── Hit test ────────────────────────────────────────────────────────────

    private GraphNode hitTest(double wx, double wy) {
        // Iterate in reverse draw order so top nodes are hit first.
        java.util.List<GraphNode> nodes = model.getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            GraphNode n = nodes.get(i);
            double r = NodeRenderer.radius(n);
            double dx = wx - n.x, dy = wy - n.y;
            if (dx * dx + dy * dy <= r * r) return n;
        }
        return null;
    }

    private double distSqToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return (px - x1) * (px - x1) + (py - y1) * (py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        double projX = x1 + t * (x2 - x1);
        double projY = y1 + t * (y2 - y1);
        return (px - projX) * (px - projX) + (py - projY) * (py - projY);
    }

    private GraphEdge hitTestEdge(double wx, double wy, double zoom) {
        double hitRadius = 20.0 / Math.max(0.1, zoom); // Increase hit area slightly
        double rSq = hitRadius * hitRadius;

        for (GraphEdge edge : model.getEdges()) {
            EdgeRouter.Path path = router.path(edge);
            if (path == null) continue;
            // Approximate bezier curve with 20 segments
            double[] prevPt = path.pointAt(0);
            for (int i = 1; i <= 20; i++) {
                double t = i / 20.0;
                double[] pt = path.pointAt(t);
                if (distSqToSegment(wx, wy, prevPt[0], prevPt[1], pt[0], pt[1]) <= rSq) {
                    return edge;
                }
                prevPt = pt;
            }
        }
        return null;
    }

    private void showEdgeTooltip(GraphEdge edge, double screenX, double screenY) {
        edgeTooltip.getChildren().clear();
        
        ConnectionExplainer explainer = new ConnectionExplainer(model);
        ConnectionExplainer.Explanation exp = explainer.explain(edge);

        Label headline = new Label(exp.headline());
        headline.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        edgeTooltip.getChildren().add(headline);

        Label reason = new Label(exp.reason);
        reason.setWrapText(true);
        reason.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10.5px; -fx-line-spacing: 2;");
        edgeTooltip.getChildren().add(reason);

        if (edge.isAISuggestion) {
            Label aiTag = new Label("🤖 AI INFERRED CONNECTION");
            aiTag.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 4 0 0 0;");
            edgeTooltip.getChildren().add(aiTag);
        }

        positionTooltip(screenX, screenY);
        edgeTooltip.setVisible(true);
    }

    private void positionTooltip(double x, double y) {
        double ox = x + 15;
        double oy = y + 15;
        
        if (ox + edgeTooltip.getWidth() > canvas.getWidth()) {
            ox = x - edgeTooltip.getWidth() - 15;
        }
        if (oy + edgeTooltip.getHeight() > canvas.getHeight()) {
            oy = y - edgeTooltip.getHeight() - 15;
        }

        // Translate relative to center of StackPane
        double cx = (ox - canvas.getWidth() / 2) + edgeTooltip.getPrefWidth() / 2;
        double cy = (oy - canvas.getHeight() / 2) + edgeTooltip.getPrefHeight() / 2;
        
        edgeTooltip.setTranslateX(cx);
        edgeTooltip.setTranslateY(cy);
    }

    // ── Redraw / animation ──────────────────────────────────────────────────

    private void redraw() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::redraw);
            return;
        }
        renderer.redraw(canvas, selectedNode, hoveredNode, previewNode, nowSec);
    }

    private void ensureAnimation() {
        if (lastNanos < 0) lastNanos = System.nanoTime();
        animTimer.start();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int)(c.getRed()   * 255),
                (int)(c.getGreen() * 255),
                (int)(c.getBlue()  * 255));
    }
}
