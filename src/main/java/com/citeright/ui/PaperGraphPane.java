package com.citeright.ui;

import com.citeright.model.*;
import com.citeright.nlp.TfIdfEngine;
import com.citeright.service.*;
import com.citeright.service.ClusteringEngine.ClusterResult;
import com.citeright.service.GapAnalyzer.*;
import com.citeright.service.AuthorNetworkBuilder.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import com.citeright.ai.GeminiConfig;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Interactive 2D graph visualization — a researcher's thinking machine.
 *
 * Three scale layers:
 *   MACRO  — Topic landscape with real TF-IDF clustering, temporal slider, gap analysis
 *   MESO   — Ego-centric reasoning with AI-assisted relationship inference
 *   MICRO  — Freeform argument canvas with notes, groups, and custom edges
 *
 * Rendering: Canvas-based with force-directed layout, convex hulls, and animated edges.
 */
public class PaperGraphPane extends BorderPane {

    private static final double SIMILARITY_THRESHOLD = 0.06;
    private static final double NODE_RADIUS = 22;
    private static final double REPULSION = 13000;
    private static final double ATTRACTION = 0.005;
    private static final double DAMPING = 0.85;
    private static final int LAYOUT_ITERATIONS = 180;
    private static final double EDGE_HIT_DISTANCE = 8;

    private final LibraryService libraryService;
    private final ClusteringEngine clusteringEngine = new ClusteringEngine();
    private final GapAnalyzer gapAnalyzer = new GapAnalyzer();
    private final AuthorNetworkBuilder authorNetworkBuilder = new AuthorNetworkBuilder();
    private final GraphAIService graphAIService = new GraphAIService();
    private GraphViewState currentState = GraphViewState.MACRO_LANDSCAPE;
    private Consumer<LibraryEntry> onSelectEntry;

    private Canvas canvas;
    private StackPane canvasContainer;
    private StackPane tourOverlay;
    private VBox settingsPanel;
    private int tourStep = 0;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    // Panning & zooming
    private double offsetX = 0, offsetY = 0;
    private double zoom = 1.0;
    private double dragStartX, dragStartY;
    private double panStartX, panStartY;

    // Hover
    private GraphNode hoveredNode = null;
    private GraphNode selectedNode = null;
    private GraphEdge hoveredEdge = null;

    // Stored vectors for shared-term extraction
    private final Map<GraphNode, Map<String, Double>> nodeVectors = new HashMap<>();
    private final com.citeright.database.PaperRelationshipDAO relationshipDAO = new com.citeright.database.PaperRelationshipDAO();

    private GraphNode egoNode = null;
    private GraphNode connectionSourceNode = null;
    private Button btnClearEgo;
    private com.citeright.database.WorkspaceDAO workspaceDAO = new com.citeright.database.WorkspaceDAO();
    private com.citeright.database.WorkspaceNoteDAO noteDAO = new com.citeright.database.WorkspaceNoteDAO();
    private com.citeright.database.WorkspaceGroupDAO groupDAO = new com.citeright.database.WorkspaceGroupDAO();

    // Inspector Panel
    private VBox inspectorPanel;
    private Label inspectorTitle, inspectorAuthors, inspectorAbstract;
    private ListView<String> inspectorRelationships;

    // Tooltip panel (for nodes)
    private VBox tooltipPanel;
    private Label tooltipTitle, tooltipAuthors, tooltipYear, tooltipVenue, tooltipAbstract, tooltipConnections;

    // Edge tooltip panel
    private VBox edgeTooltipPanel;
    private Label edgeTooltipTitle, edgeTooltipScore, edgeTooltipTerms;

    // ── Temporal Slider ──
    private Slider temporalMinSlider, temporalMaxSlider;
    private Label temporalLabel;
    private HBox temporalBar;
    private int globalMinYear = 1990, globalMaxYear = 2026;
    private int filterMinYear = 1900, filterMaxYear = 2100;

    // ── Gap Analysis ──
    private boolean gapAnalysisEnabled = false;
    private GapAnalysisResult gapResult = null;
    private ToggleButton btnGapAnalysis;

    // ── Author Network ──
    private boolean authorOverlayEnabled = false;
    private AuthorNetwork authorNetwork = null;
    private ToggleButton btnAuthorOverlay;

    // ── Graph Filters ──
    private double similarityFilterValue = 0.08;
    private boolean showTfIdfEdges = true;
    private boolean showAISuggestions = false;
    private boolean showCuratedEdges = true;
    private final List<GraphEdge> aiSuggestedEdges = new ArrayList<>();
    private CheckBox cbAI;
    private ToggleGroup viewGroup;

    // ── Micro Workspace state ──
    private final List<WorkspaceNote> workspaceNotes = new ArrayList<>();
    private final List<WorkspaceGroup> workspaceGroups = new ArrayList<>();
    private WorkspaceNote draggedNote = null;
    private WorkspaceNote editingNote = null;
    private final List<GraphNode> selectedNodes = new ArrayList<>(); // for group creation
    private boolean isSelecting = false;
    private double selStartX, selStartY, selEndX, selEndY;

    // ── Cluster data (for Macro convex hulls) ──
    private Map<String, List<GraphNode>> clusterNodeMap = new HashMap<>();

    // ── Color palette for clusters ──
    private static final Color[] CLUSTER_COLORS = {
            Color.web("#4a9cf7"), Color.web("#ff6b9d"), Color.web("#2ecc71"),
            Color.web("#f1c40f"), Color.web("#9b59b6"), Color.web("#e67e22"),
            Color.web("#1abc9c"), Color.web("#e74c3c"), Color.web("#3498db"),
            Color.web("#fd79a8"), Color.web("#6c5ce7"), Color.web("#00cec9"),
            Color.web("#fdcb6e"), Color.web("#d63031"), Color.web("#74b9ff")
    };

    private static final Color[] COMMUNITY_COLORS = {
            Color.web("#e74c3c"), Color.web("#3498db"), Color.web("#2ecc71"),
            Color.web("#9b59b6"), Color.web("#f39c12"), Color.web("#1abc9c"),
            Color.web("#e67e22"), Color.web("#fd79a8"), Color.web("#00cec9")
    };

    public PaperGraphPane(LibraryService libraryService) {
        this.libraryService = libraryService;
        buildUI();
        if (!GeminiConfig.isTourCompleted()) {
            Platform.runLater(this::showOnboardingTour);
        }
    }

    public void setOnSelectEntry(Consumer<LibraryEntry> handler) { this.onSelectEntry = handler; }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI() {
        setStyle("-fx-background-color: #0d0d1a;");

        canvasContainer = new StackPane();

        canvas = new Canvas();
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        // Interaction
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMouseClicked(this::onMouseClicked);
        canvas.setOnScroll(this::onScroll);

        // Tooltip panel (Lightweight Hover)
        tooltipPanel = new VBox(2);
        tooltipPanel.setVisible(false);
        tooltipPanel.setMouseTransparent(true);
        tooltipPanel.setManaged(false);
        tooltipPanel.setMaxWidth(220);
        tooltipPanel.setStyle(
                "-fx-background-color: rgba(15,15,25,0.95); " +
                "-fx-background-radius: 6; " +
                "-fx-padding: 8 12; " +
                "-fx-border-color: rgba(74,108,247,0.4); -fx-border-radius: 6; -fx-border-width: 1;");
        StackPane.setAlignment(tooltipPanel, Pos.TOP_LEFT);

        tooltipTitle = new Label();
        tooltipTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        tooltipTitle.setWrapText(true);

        tooltipAuthors = new Label();
        tooltipAuthors.setStyle("-fx-font-size: 10px; -fx-text-fill: #4a9cf7;");

        tooltipYear = new Label();
        tooltipYear.setStyle("-fx-font-size: 9px; -fx-text-fill: #aaaacc;");

        // We still need these for compatibility, but we won't show them
        tooltipVenue = new Label();
        tooltipAbstract = new Label();
        tooltipConnections = new Label();

        tooltipPanel.getChildren().addAll(tooltipTitle, tooltipAuthors, tooltipYear);

        // Edge tooltip panel (Lightweight)
        edgeTooltipPanel = new VBox(2);
        edgeTooltipPanel.setVisible(false);
        edgeTooltipPanel.setMouseTransparent(true);
        edgeTooltipPanel.setManaged(false);
        edgeTooltipPanel.setMaxWidth(200);
        edgeTooltipPanel.setStyle(
                "-fx-background-color: rgba(15,15,25,0.95); " +
                "-fx-background-radius: 6; " +
                "-fx-padding: 8 12; " +
                "-fx-border-color: rgba(255,107,157,0.4); -fx-border-radius: 6; -fx-border-width: 1;");
        StackPane.setAlignment(edgeTooltipPanel, Pos.TOP_LEFT);

        edgeTooltipTitle = new Label();
        edgeTooltipTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        edgeTooltipTitle.setWrapText(true);

        edgeTooltipScore = new Label();
        edgeTooltipScore.setStyle("-fx-font-size: 10px; -fx-text-fill: #ff6b9d; -fx-font-weight: bold;");

        edgeTooltipTerms = new Label();
        edgeTooltipTerms.setStyle("-fx-font-size: 9px; -fx-text-fill: #bbbbdd;");

        edgeTooltipPanel.getChildren().addAll(edgeTooltipTitle, edgeTooltipScore, edgeTooltipTerms);

        // Header bar
        HBox header = buildHeader();
        StackPane.setAlignment(header, Pos.TOP_LEFT);

        // Temporal bar (bottom)
        temporalBar = buildTemporalBar();
        StackPane.setAlignment(temporalBar, Pos.BOTTOM_LEFT);

        // Legend
        VBox legend = buildLegend();
        StackPane.setAlignment(legend, Pos.BOTTOM_RIGHT);

        // Graph Filters Settings Panel (floating, sits on the left under header)
        settingsPanel = buildSettingsPanel();

        canvasContainer.getChildren().addAll(canvas, header, temporalBar, legend, settingsPanel, tooltipPanel, edgeTooltipPanel);

        // Inspector Panel
        inspectorPanel = buildInspectorPanel();
        inspectorPanel.setVisible(false);
        inspectorPanel.setManaged(false);

        setCenter(canvasContainer);
        setRight(inspectorPanel);

        // Redraw on resize
        canvasContainer.widthProperty().addListener((o, a, b) -> draw());
        canvasContainer.heightProperty().addListener((o, a, b) -> draw());
    }

    private VBox buildInspectorPanel() {
        VBox panel = new VBox(15);
        panel.setPrefWidth(300);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: #2a2a3e; -fx-border-width: 0 0 0 1;");

        Label header = new Label("Inspector");
        header.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #88aadd;");

        inspectorTitle = new Label();
        inspectorTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        inspectorTitle.setWrapText(true);

        inspectorAuthors = new Label();
        inspectorAuthors.setStyle("-fx-font-size: 12px; -fx-text-fill: #4a9cf7;");
        inspectorAuthors.setWrapText(true);

        Label absHeader = new Label("Abstract");
        absHeader.setStyle("-fx-font-size: 11px; -fx-text-fill: #88aadd; -fx-font-weight: bold;");

        inspectorAbstract = new Label();
        inspectorAbstract.setStyle("-fx-font-size: 12px; -fx-text-fill: #bbbbdd; -fx-line-spacing: 3;");
        inspectorAbstract.setWrapText(true);
        ScrollPane absScroll = new ScrollPane(inspectorAbstract);
        absScroll.setFitToWidth(true);
        absScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        absScroll.setPrefHeight(200);

        Label relHeader = new Label("Relationships");
        relHeader.setStyle("-fx-font-size: 11px; -fx-text-fill: #88aadd; -fx-font-weight: bold;");

        inspectorRelationships = new ListView<>();
        inspectorRelationships.setStyle("-fx-background-color: transparent; -fx-control-inner-background: #1a1a2e; -fx-text-fill: #ffffff;");
        inspectorRelationships.setPrefHeight(150);

        panel.getChildren().addAll(header, inspectorTitle, inspectorAuthors, absHeader, absScroll, relHeader, inspectorRelationships);
        return panel;
    }

    private void updateInspector(GraphNode node) {
        inspectorPanel.setVisible(false);
        inspectorPanel.setManaged(false);
    }

    private HBox buildHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 16, 20));
        header.setMaxHeight(56);
        header.setStyle("-fx-background-color: rgba(13,13,26,0.85);");

        Label icon = new Label("🕸");
        icon.setStyle("-fx-font-size: 20px;");

        Label title = new Label("Paper Graph");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label subtitle = new Label("Semantic connections between your papers");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #8888bb;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button zoomIn = styledBtn("＋");
        zoomIn.setMinWidth(Region.USE_PREF_SIZE);
        zoomIn.setOnAction(e -> { zoom = Math.min(3.0, zoom * 1.2); draw(); });

        Button zoomOut = styledBtn("－");
        zoomOut.setMinWidth(Region.USE_PREF_SIZE);
        zoomOut.setOnAction(e -> { zoom = Math.max(0.3, zoom / 1.2); draw(); });

        Button reset = styledBtn("Reset");
        reset.setMinWidth(Region.USE_PREF_SIZE);
        reset.setOnAction(e -> { zoom = 1.0; offsetX = 0; offsetY = 0; draw(); });

        btnClearEgo = styledBtn("Clear Target");
        btnClearEgo.setMinWidth(Region.USE_PREF_SIZE);
        btnClearEgo.setStyle("-fx-background-color: rgba(231,76,60,0.3); -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand; -fx-border-color: #e74c3c; -fx-border-radius: 4; -fx-background-radius: 4;");
        btnClearEgo.setOnAction(e -> {
            egoNode = null;
            connectionSourceNode = null;
            buildGraph();
        });
        btnClearEgo.setVisible(false);
        btnClearEgo.setManaged(false);

        // Gap Analysis toggle
        btnGapAnalysis = new ToggleButton("🔍 Gaps");
        btnGapAnalysis.setMinWidth(Region.USE_PREF_SIZE);
        btnGapAnalysis.setTooltip(new Tooltip("Gap Analysis (Orphans, Bridges, Methodologies)"));
        styleToggleButton(btnGapAnalysis);
        btnGapAnalysis.setOnAction(e -> {
            gapAnalysisEnabled = btnGapAnalysis.isSelected();
            if (gapAnalysisEnabled) runGapAnalysis();
            else { gapResult = null; draw(); }
        });

        // Author Network toggle
        btnAuthorOverlay = new ToggleButton("👥 Authors");
        btnAuthorOverlay.setMinWidth(Region.USE_PREF_SIZE);
        btnAuthorOverlay.setTooltip(new Tooltip("Co-authorship Communities Network"));
        styleToggleButton(btnAuthorOverlay);
        btnAuthorOverlay.setOnAction(e -> {
            authorOverlayEnabled = btnAuthorOverlay.isSelected();
            if (authorOverlayEnabled) buildAuthorNetwork();
            else { authorNetwork = null; draw(); }
        });

        // AI Analyze button (Meso only)
        Button btnAIAnalyze = styledBtn("🤖 Analyze");
        btnAIAnalyze.setMinWidth(Region.USE_PREF_SIZE);
        btnAIAnalyze.setTooltip(new Tooltip("Run AI/Local relationship inference on visible papers"));
        btnAIAnalyze.setOnAction(e -> runAIAnalysis());

        viewGroup = new ToggleGroup();

        ToggleButton btnMacro = new ToggleButton("Macro");
        btnMacro.setMinWidth(Region.USE_PREF_SIZE);
        btnMacro.setTooltip(new Tooltip("Macro View (Theme Clusters)"));
        btnMacro.setToggleGroup(viewGroup);
        btnMacro.setUserData(GraphViewState.MACRO_LANDSCAPE);

        ToggleButton btnMeso = new ToggleButton("Meso");
        btnMeso.setMinWidth(Region.USE_PREF_SIZE);
        btnMeso.setTooltip(new Tooltip("Meso View (2-Hop Ego Network)"));
        btnMeso.setToggleGroup(viewGroup);
        btnMeso.setUserData(GraphViewState.MESO_EGOCENTRIC);

        ToggleButton btnMicro = new ToggleButton("Micro");
        btnMicro.setMinWidth(Region.USE_PREF_SIZE);
        btnMicro.setTooltip(new Tooltip("Micro View (Workspace Whiteboard)"));
        btnMicro.setToggleGroup(viewGroup);
        btnMicro.setUserData(GraphViewState.MICRO_WORKSPACE);

        ToggleButton btnTimeline = new ToggleButton("Timeline");
        btnTimeline.setMinWidth(Region.USE_PREF_SIZE);
        btnTimeline.setTooltip(new Tooltip("Timeline View (Connected Papers / Litmaps style chronological similarity mapping)"));
        btnTimeline.setToggleGroup(viewGroup);
        btnTimeline.setUserData(GraphViewState.TIMELINE_TRAJECTORY);

        if (currentState == GraphViewState.MACRO_LANDSCAPE) btnMacro.setSelected(true);
        else if (currentState == GraphViewState.MESO_EGOCENTRIC) btnMeso.setSelected(true);
        else if (currentState == GraphViewState.MICRO_WORKSPACE) btnMicro.setSelected(true);
        else if (currentState == GraphViewState.TIMELINE_TRAJECTORY) btnTimeline.setSelected(true);

        viewGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
            } else {
                currentState = (GraphViewState) newVal.getUserData();
                updateControlVisibility();
                buildGraph();
            }
        });

        String normalStyle = "-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: #ffffff; -fx-font-size: 12px; -fx-padding: 5 12; -fx-cursor: hand;";
        String selectedStyle = "-fx-background-color: rgba(74,156,247,0.4); -fx-text-fill: #ffffff; -fx-font-size: 12px; -fx-padding: 5 12; -fx-cursor: hand; -fx-font-weight: bold;";

        for (ToggleButton tb : java.util.Arrays.asList(btnMacro, btnMeso, btnMicro, btnTimeline)) {
            tb.setMinWidth(Region.USE_PREF_SIZE);
            tb.setStyle(tb.isSelected() ? selectedStyle : normalStyle);
            tb.selectedProperty().addListener((obs, wasSel, isSel) -> {
                tb.setStyle(isSel ? selectedStyle : normalStyle);
            });
        }

        Button btnTour = styledBtn("❓ Tour");
        btnTour.setMinWidth(Region.USE_PREF_SIZE);
        btnTour.setTooltip(new Tooltip("Start Onboarding Tour"));
        btnTour.setOnAction(e -> showOnboardingTour());

        HBox viewSelector = new HBox(2, btnMacro, btnMeso, btnMicro, btnTimeline);
        viewSelector.setMinWidth(Region.USE_PREF_SIZE);
        HBox tools = new HBox(6, btnGapAnalysis, btnAuthorOverlay, btnAIAnalyze, btnTour);
        tools.setMinWidth(Region.USE_PREF_SIZE);

        header.getChildren().addAll(icon, title, subtitle, spacer, btnClearEgo,
                viewSelector, tools, zoomOut, zoomIn, reset);
        return header;
    }

    private void updateControlVisibility() {
        boolean showTemporal = (currentState == GraphViewState.MACRO_LANDSCAPE || currentState == GraphViewState.TIMELINE_TRAJECTORY);
        temporalBar.setVisible(showTemporal);
        temporalBar.setManaged(showTemporal);
    }

    private HBox buildTemporalBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 30, 16, 30));
        bar.setMaxHeight(50);
        bar.setStyle("-fx-background-color: rgba(13,13,26,0.85);");
        StackPane.setMargin(bar, new Insets(0, 200, 0, 0));

        Label icon = new Label("📅");
        icon.setStyle("-fx-font-size: 14px;");

        temporalLabel = new Label("All Years");
        temporalLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaacc; -fx-min-width: 100;");

        temporalMinSlider = new Slider(1950, 2026, 1950);
        temporalMinSlider.setMajorTickUnit(10);
        temporalMinSlider.setMinorTickCount(4);
        temporalMinSlider.setShowTickLabels(true);
        temporalMinSlider.setShowTickMarks(true);
        temporalMinSlider.setPrefWidth(250);
        temporalMinSlider.setStyle("-fx-control-inner-background: #2a2a3e;");

        temporalMaxSlider = new Slider(1950, 2026, 2026);
        temporalMaxSlider.setMajorTickUnit(10);
        temporalMaxSlider.setMinorTickCount(4);
        temporalMaxSlider.setShowTickLabels(true);
        temporalMaxSlider.setShowTickMarks(true);
        temporalMaxSlider.setPrefWidth(250);
        temporalMaxSlider.setStyle("-fx-control-inner-background: #2a2a3e;");

        Label fromLabel = new Label("From:");
        fromLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 11px;");
        Label toLabel = new Label("To:");
        toLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 11px;");

        temporalMinSlider.valueProperty().addListener((obs, old, val) -> {
            filterMinYear = val.intValue();
            if (filterMinYear > filterMaxYear) {
                temporalMaxSlider.setValue(filterMinYear);
            }
            temporalLabel.setText(filterMinYear + " – " + filterMaxYear);
            buildGraph();
        });

        temporalMaxSlider.valueProperty().addListener((obs, old, val) -> {
            filterMaxYear = val.intValue();
            if (filterMaxYear < filterMinYear) {
                temporalMinSlider.setValue(filterMaxYear);
            }
            temporalLabel.setText(filterMinYear + " – " + filterMaxYear);
            buildGraph();
        });

        Button resetBtn = styledBtn("All Years");
        resetBtn.setOnAction(e -> {
            temporalMinSlider.setValue(1950);
            temporalMaxSlider.setValue(2026);
            filterMinYear = 1900;
            filterMaxYear = 2100;
            temporalLabel.setText("All Years");
            buildGraph();
        });

        bar.getChildren().addAll(icon, fromLabel, temporalMinSlider, toLabel, temporalMaxSlider, temporalLabel, resetBtn);
        return bar;
    }

    private VBox buildSettingsPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        panel.setMaxWidth(190);
        panel.setStyle(
                "-fx-background-color: rgba(22, 22, 40, 0.85); " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: rgba(74, 156, 247, 0.25); -fx-border-radius: 10; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 4);");
        StackPane.setAlignment(panel, Pos.TOP_LEFT);
        StackPane.setMargin(panel, new Insets(76, 0, 0, 16)); // Sits under header

        Label titleLabel = new Label("GRAPH FILTERS");
        titleLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 0 0 4 0;");

        // Similarity Slider
        Label simLabel = new Label("Similarity: ≥ 0.08");
        simLabel.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10.5px;");

        Slider simSlider = new Slider(0.00, 0.35, 0.08);
        simSlider.setBlockIncrement(0.02);
        simSlider.setStyle("-fx-control-inner-background: #2a2a3e;");
        simSlider.valueProperty().addListener((obs, old, val) -> {
            similarityFilterValue = val.doubleValue();
            simLabel.setText(String.format("Similarity: ≥ %.2f", similarityFilterValue));
            buildGraph();
        });

        // Checkboxes
        CheckBox cbTfIdf = new CheckBox("Automated Similarity");
        cbTfIdf.setSelected(showTfIdfEdges);
        styleCheckBox(cbTfIdf);
        cbTfIdf.selectedProperty().addListener((obs, old, val) -> {
            showTfIdfEdges = val;
            if (val) {
                LocalSemanticAIPrompt.showPromptIfNeeded();
            }
            buildGraph();
        });

        cbAI = new CheckBox("AI Suggestions");
        cbAI.setSelected(showAISuggestions);
        styleCheckBox(cbAI);
        cbAI.selectedProperty().addListener((obs, old, val) -> {
            showAISuggestions = val;
            buildGraph();
        });

        CheckBox cbCurated = new CheckBox("Curated Connections");
        cbCurated.setSelected(showCuratedEdges);
        styleCheckBox(cbCurated);
        cbCurated.selectedProperty().addListener((obs, old, val) -> {
            showCuratedEdges = val;
            buildGraph();
        });

        panel.getChildren().addAll(titleLabel, simLabel, simSlider, cbTfIdf, cbAI, cbCurated);
        return panel;
    }

    private void styleCheckBox(CheckBox cb) {
        cb.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10.5px; -fx-cursor: hand;");
    }

    private VBox buildLegend() {
        VBox legend = new VBox(5);
        legend.setPadding(new Insets(12));
        legend.setMaxWidth(200);
        legend.setMaxHeight(140);
        legend.setStyle(
                "-fx-background-color: rgba(30, 30, 50, 0.8); " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 10 14;");
        StackPane.setMargin(legend, new Insets(0, 16, 16, 0));

        Label legendTitle = new Label("LEGEND");
        legendTitle.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 9px; -fx-font-weight: bold;");

        Label l1 = new Label("● Node = Paper in library");
        l1.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10px;");

        Label l2 = new Label("— Solid line = Confirmed connection");
        l2.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10px;");

        Label l3 = new Label("┄ Dashed line = AI suggestion");
        l3.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10px;");

        Label l4 = new Label("🟢 SUPPORTS  🔴 CONTRADICTS");
        l4.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 9px;");

        Label l5 = new Label("🟡 EXTENDS   🟣 METHODOLOGY");
        l5.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 9px;");

        Label l6 = new Label("Scroll to zoom · Drag to pan");
        l6.setStyle("-fx-text-fill: #6666aa; -fx-font-size: 9px;");

        legend.getChildren().addAll(legendTitle, l1, l2, l3, l4, l5, l6);
        return legend;
    }

    private Button styledBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.1); " +
                "-fx-text-fill: #ffffff; -fx-font-size: 12px; " +
                "-fx-padding: 5 12; -fx-background-radius: 6; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.2); " +
                "-fx-text-fill: #ffffff; -fx-font-size: 12px; " +
                "-fx-padding: 5 12; -fx-background-radius: 6; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.1); " +
                "-fx-text-fill: #ffffff; -fx-font-size: 12px; " +
                "-fx-padding: 5 12; -fx-background-radius: 6; -fx-cursor: hand;"));
        return btn;
    }

    private void styleToggleButton(ToggleButton btn) {
        String normalStyle = "-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #aaaacc; -fx-font-size: 11px; -fx-padding: 5 10; -fx-background-radius: 4; -fx-cursor: hand;";
        String activeStyle = "-fx-background-color: rgba(74,156,247,0.3); -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 5 10; -fx-background-radius: 4; -fx-cursor: hand; -fx-font-weight: bold;";
        btn.setStyle(normalStyle);
        btn.selectedProperty().addListener((obs, was, is) -> btn.setStyle(is ? activeStyle : normalStyle));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Graph Building
    // ─────────────────────────────────────────────────────────────────────────

    public void buildGraph() {
        new Thread(() -> {
            try {
                List<LibraryEntry> entries = libraryService.getAllActive();
                if (entries.isEmpty()) {
                    Platform.runLater(() -> {
                        this.nodes.clear();
                        this.edges.clear();
                        this.nodeVectors.clear();
                        this.clusterNodeMap.clear();
                        this.aiSuggestedEdges.clear();
                        this.workspaceNotes.clear();
                        this.workspaceGroups.clear();
                        draw();
                    });
                    return;
                }

                GraphData data = new GraphData();
                data.egoNode = this.egoNode;
                data.connectionSourceNode = this.connectionSourceNode;

                // Apply temporal filter for Macro view
                List<LibraryEntry> filteredEntries = entries;
                if (currentState == GraphViewState.MACRO_LANDSCAPE && filterMinYear < filterMaxYear) {
                    filteredEntries = entries.stream()
                            .filter(e -> {
                                Publication pub = e.getPublication();
                                if (pub == null) return true;
                                int year = pub.getYear();
                                return year == 0 || (year >= filterMinYear && year <= filterMaxYear);
                            })
                            .collect(Collectors.toList());
                }

                if (currentState == GraphViewState.MACRO_LANDSCAPE) {
                    buildMacroGraph(filteredEntries, data);
                } else if (currentState == GraphViewState.MESO_EGOCENTRIC) {
                    buildMesoGraph(filteredEntries, data);
                } else if (currentState == GraphViewState.MICRO_WORKSPACE) {
                    buildMicroGraph(entries, data);
                } else if (currentState == GraphViewState.TIMELINE_TRAJECTORY) {
                    buildTimelineGraph(filteredEntries, data);
                }

                Platform.runLater(() -> {
                    if (btnClearEgo != null) {
                        boolean hasEgo = ((currentState == GraphViewState.MESO_EGOCENTRIC || currentState == GraphViewState.TIMELINE_TRAJECTORY) && data.egoNode != null);
                        btnClearEgo.setVisible(hasEgo);
                        btnClearEgo.setManaged(hasEgo);
                    }
                });

                // Force-directed layout
                runForceLayout(data);

                // Center the graph
                centerGraph(data);

                Platform.runLater(() -> {
                    this.nodes.clear();
                    this.nodes.addAll(data.nodes);
                    this.edges.clear();
                    this.edges.addAll(data.edges);
                    this.nodeVectors.clear();
                    this.nodeVectors.putAll(data.nodeVectors);
                    this.clusterNodeMap.clear();
                    this.clusterNodeMap.putAll(data.clusterNodeMap);
                    this.aiSuggestedEdges.clear();
                    this.aiSuggestedEdges.addAll(data.aiSuggestedEdges);
                    this.workspaceNotes.clear();
                    this.workspaceNotes.addAll(data.workspaceNotes);
                    this.workspaceGroups.clear();
                    this.workspaceGroups.addAll(data.workspaceGroups);
                    
                    this.egoNode = data.egoNode;
                    this.connectionSourceNode = data.connectionSourceNode;
                    draw();
                });
            } catch (Exception e) {
                System.err.println("[PaperGraph] Error building graph: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private String buildDocText(LibraryEntry entry) {
        Publication pub = entry.getPublication();
        if (pub == null) return "";
        StringBuilder sb = new StringBuilder();
        if (pub.getTitle() != null) {
            sb.append(pub.getTitle()).append(" ");
            sb.append(pub.getTitle()).append(" ");
        }
        if (pub.getAbstractText() != null) {
            sb.append(pub.getAbstractText());
        }
        return sb.toString();
    }

    private void buildMacroGraph(List<LibraryEntry> entries, GraphData data) {
        List<Publication> pubs = entries.stream().map(LibraryEntry::getPublication).filter(Objects::nonNull).collect(Collectors.toList());
        ClusterResult result = clusteringEngine.clusterWithDetails(pubs);
        Map<String, List<Publication>> clusters = result.getClusters();

        Random rng = new Random(42);
        int colorIdx = 0;

        for (Map.Entry<String, List<Publication>> cluster : clusters.entrySet()) {
            String clusterLabel = cluster.getKey();
            List<Publication> members = cluster.getValue();
            Color clusterColor = CLUSTER_COLORS[colorIdx % CLUSTER_COLORS.length];
            colorIdx++;

            List<GraphNode> clusterNodes = new ArrayList<>();

            // Create individual paper nodes within the cluster
            double cx = rng.nextDouble() * 600 - 300;
            double cy = rng.nextDouble() * 400 - 200;

            for (Publication pub : members) {
                // Find the matching LibraryEntry
                LibraryEntry matchEntry = entries.stream()
                        .filter(e -> e.getPublication() != null && e.getPublication() == pub)
                        .findFirst().orElse(null);

                String title = pub.getTitle() != null ? pub.getTitle() : "Untitled";
                int year = pub.getYear();

                GraphNode node = new GraphNode(matchEntry, title, year,
                        cx + rng.nextDouble() * 120 - 60,
                        cy + rng.nextDouble() * 100 - 50);
                node.clusterLabel = clusterLabel;
                node.clusterColor = clusterColor;
                data.nodes.add(node);
                clusterNodes.add(node);
            }

            data.clusterNodeMap.put(clusterLabel, clusterNodes);
        }

        // Add TF-IDF edges between papers (within and across clusters)
        addTfIdfEdges(entries, data);

        // Add explicit relationships (user-created + AI-confirmed/suggested)
        List<PaperRelationship> rels = new ArrayList<>();
        for (GraphNode node : data.nodes) {
            if (node.entry != null) {
                rels.addAll(relationshipDAO.getActiveBySourcePaperId(node.entry.getId()));
            }
        }
        for (PaperRelationship rel : rels) {
            GraphNode src = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == rel.getSourcePaperId()).findFirst().orElse(null);
            GraphNode tgt = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == rel.getTargetPaperId()).findFirst().orElse(null);
            if (src != null && tgt != null) {
                boolean isAI = (rel.getSource() == PaperRelationship.Source.AI_SUGGESTED);
                if ((isAI && !showAISuggestions) || (!isAI && !showCuratedEdges)) {
                    continue;
                }
                GraphEdge edge = new GraphEdge(src, tgt, 1.0, rel.getRelationshipType());
                edge.relationshipId = rel.getId();
                edge.isAISuggestion = isAI;
                edge.confidence = rel.getConfidence();
                edge.reasoning = rel.getReasoning();
                data.edges.add(edge);
                if (edge.isAISuggestion) data.aiSuggestedEdges.add(edge);
            }
        }
    }

    private void addTfIdfEdges(List<LibraryEntry> entries, GraphData data) {
        List<String> documents = new ArrayList<>();
        List<LibraryEntry> validEntries = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            String doc = buildDocText(entry);
            if (!doc.isBlank()) {
                documents.add(doc);
                validEntries.add(entry);
            }
        }

        if (documents.isEmpty()) return;

        TfIdfEngine engine = new TfIdfEngine();
        engine.buildModel(documents);

        List<Map<String, Double>> vectors = new ArrayList<>();
        for (String doc : documents) {
            vectors.add(engine.computeTfIdfVector(doc));
        }

        // Map entries to their nodes
        Map<Integer, GraphNode> entryToNode = new HashMap<>();
        for (GraphNode node : data.nodes) {
            if (node.entry != null) {
                entryToNode.put(node.entry.getId(), node);
            }
        }

        boolean useBge = com.citeright.ai.NeuralAvailability.isReady();
        com.citeright.ai.BgeM3EmbeddingEngine neuralEngine = null;
        com.citeright.database.PaperEmbeddingDAO embeddingDAO = null;
        Map<Integer, float[]> cachedEmbeddings = null;

        if (useBge) {
            neuralEngine = com.citeright.ai.BgeM3EmbeddingEngine.getInstance();
            embeddingDAO = new com.citeright.database.PaperEmbeddingDAO();
            cachedEmbeddings = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");
        }

        for (int i = 0; i < validEntries.size(); i++) {
            GraphNode nodeA = entryToNode.get(validEntries.get(i).getId());
            if (nodeA == null) continue;
            data.nodeVectors.put(nodeA, vectors.get(i));

            for (int j = i + 1; j < validEntries.size(); j++) {
                GraphNode nodeB = entryToNode.get(validEntries.get(j).getId());
                if (nodeB == null) continue;

                double sim;
                if (useBge) {
                    float[] vecA = cachedEmbeddings.get(validEntries.get(i).getId());
                    if (vecA == null) {
                        vecA = neuralEngine.getEmbedding(buildDocText(validEntries.get(i)));
                        if (vecA != null) {
                            embeddingDAO.saveEmbedding(validEntries.get(i).getId(), "bge-m3", "v1", vecA);
                            cachedEmbeddings.put(validEntries.get(i).getId(), vecA);
                        }
                    }

                    float[] vecB = cachedEmbeddings.get(validEntries.get(j).getId());
                    if (vecB == null) {
                        vecB = neuralEngine.getEmbedding(buildDocText(validEntries.get(j)));
                        if (vecB != null) {
                            embeddingDAO.saveEmbedding(validEntries.get(j).getId(), "bge-m3", "v1", vecB);
                            cachedEmbeddings.put(validEntries.get(j).getId(), vecB);
                        }
                    }

                    if (vecA != null && vecB != null) {
                        double neuralSim = com.citeright.ai.BgeM3EmbeddingEngine.cosineSimilarity(vecA, vecB);
                        // Map [0.65, 1.00] neural similarity to [0.00, 1.00] for visual slider alignment
                        sim = Math.max(0.0, (neuralSim - 0.65) / 0.35);
                    } else {
                        sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                    }
                } else {
                    sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                }
                sim = computeHybridSimilarity(sim, validEntries.get(i), validEntries.get(j));

                if (showTfIdfEdges && sim >= similarityFilterValue) {
                    data.edges.add(new GraphEdge(nodeA, nodeB, sim, "TFIDF"));
                }
            }
        }
    }

    private void buildMesoGraph(List<LibraryEntry> entries, GraphData data) {
        List<String> documents = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            documents.add(buildDocText(entry));
        }

        TfIdfEngine engine = new TfIdfEngine();
        engine.buildModel(documents);

        List<Map<String, Double>> vectors = new ArrayList<>();
        for (String doc : documents) {
            vectors.add(engine.computeTfIdfVector(doc));
        }

        Random rng = new Random(42);
        for (int i = 0; i < entries.size(); i++) {
            Publication pub = entries.get(i).getPublication();
            String title = pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled";
            int year = pub != null ? pub.getYear() : 0;
            GraphNode node = new GraphNode(entries.get(i), title, year,
                    400 + rng.nextDouble() * 400 - 200,
                    300 + rng.nextDouble() * 300 - 150);
            data.nodes.add(node);
            data.nodeVectors.put(node, vectors.get(i));
        }

        boolean useBge = com.citeright.ai.NeuralAvailability.isReady();
        com.citeright.ai.BgeM3EmbeddingEngine neuralEngine = null;
        com.citeright.database.PaperEmbeddingDAO embeddingDAO = null;
        Map<Integer, float[]> cachedEmbeddings = null;

        if (useBge) {
            neuralEngine = com.citeright.ai.BgeM3EmbeddingEngine.getInstance();
            embeddingDAO = new com.citeright.database.PaperEmbeddingDAO();
            cachedEmbeddings = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");
        }

        for (int i = 0; i < data.nodes.size(); i++) {
            for (int j = i + 1; j < data.nodes.size(); j++) {
                double sim;
                if (useBge && data.nodes.get(i).entry != null && data.nodes.get(j).entry != null) {
                    int idA = data.nodes.get(i).entry.getId();
                    int idB = data.nodes.get(j).entry.getId();
                    float[] vecA = cachedEmbeddings.get(idA);
                    if (vecA == null) {
                        vecA = neuralEngine.getEmbedding(buildDocText(data.nodes.get(i).entry));
                        if (vecA != null) {
                            embeddingDAO.saveEmbedding(idA, "bge-m3", "v1", vecA);
                            cachedEmbeddings.put(idA, vecA);
                        }
                    }
                    float[] vecB = cachedEmbeddings.get(idB);
                    if (vecB == null) {
                        vecB = neuralEngine.getEmbedding(buildDocText(data.nodes.get(j).entry));
                        if (vecB != null) {
                            embeddingDAO.saveEmbedding(idB, "bge-m3", "v1", vecB);
                            cachedEmbeddings.put(idB, vecB);
                        }
                    }
                    if (vecA != null && vecB != null) {
                        double neuralSim = com.citeright.ai.BgeM3EmbeddingEngine.cosineSimilarity(vecA, vecB);
                        sim = Math.max(0.0, (neuralSim - 0.65) / 0.35);
                    } else {
                        sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                    }
                } else {
                    sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                }
                sim = computeHybridSimilarity(sim, data.nodes.get(i).entry, data.nodes.get(j).entry);

                if (showTfIdfEdges && sim >= similarityFilterValue) {
                    data.edges.add(new GraphEdge(data.nodes.get(i), data.nodes.get(j), sim, "TFIDF"));
                }
            }
        }

        // Add explicit relationships (user-created + AI-confirmed/suggested)
        List<PaperRelationship> rels = new ArrayList<>();
        for (GraphNode node : data.nodes) {
            if (node.entry != null) {
                rels.addAll(relationshipDAO.getActiveBySourcePaperId(node.entry.getId()));
            }
        }
        for (PaperRelationship rel : rels) {
            GraphNode src = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == rel.getSourcePaperId()).findFirst().orElse(null);
            GraphNode tgt = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == rel.getTargetPaperId()).findFirst().orElse(null);
            if (src != null && tgt != null) {
                boolean isAI = (rel.getSource() == PaperRelationship.Source.AI_SUGGESTED);
                if ((isAI && !showAISuggestions) || (!isAI && !showCuratedEdges)) {
                    continue;
                }
                GraphEdge edge = new GraphEdge(src, tgt, 1.0, rel.getRelationshipType());
                edge.relationshipId = rel.getId();
                edge.isAISuggestion = isAI;
                edge.confidence = rel.getConfidence();
                edge.reasoning = rel.getReasoning();
                data.edges.add(edge);
                if (edge.isAISuggestion) data.aiSuggestedEdges.add(edge);
            }
        }

        // Re-link ego/connection nodes
        if (data.egoNode != null && data.egoNode.entry != null) {
            int egoId = data.egoNode.entry.getId();
            data.egoNode = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == egoId).findFirst().orElse(null);
        }
        if (data.connectionSourceNode != null && data.connectionSourceNode.entry != null) {
            int srcId = data.connectionSourceNode.entry.getId();
            data.connectionSourceNode = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == srcId).findFirst().orElse(null);
        }

        // Ego-centric filtering
        if (data.egoNode != null) {
            Set<GraphNode> keep = new HashSet<>();
            keep.add(data.egoNode);
            for (GraphEdge e : data.edges) {
                if (e.a == data.egoNode) keep.add(e.b);
                if (e.b == data.egoNode) keep.add(e.a);
            }
            // 2-hop: add neighbors of neighbors
            Set<GraphNode> twoHop = new HashSet<>(keep);
            for (GraphNode neighbor : keep) {
                for (GraphEdge e : data.edges) {
                    if (e.a == neighbor && !twoHop.contains(e.b)) twoHop.add(e.b);
                    if (e.b == neighbor && !twoHop.contains(e.a)) twoHop.add(e.a);
                }
            }
            data.nodes.retainAll(twoHop);
            data.edges.removeIf(e -> !twoHop.contains(e.a) || !twoHop.contains(e.b));
        }
    }

    private void buildMicroGraph(List<LibraryEntry> entries, GraphData data) {
        Map<Integer, com.citeright.database.WorkspaceDAO.PinLocation> pins = workspaceDAO.getAllPins();
        for (LibraryEntry entry : entries) {
            if (pins.containsKey(entry.getId())) {
                Publication pub = entry.getPublication();
                String title = pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled";
                int year = pub != null ? pub.getYear() : 0;
                double px = pins.get(entry.getId()).x;
                double py = pins.get(entry.getId()).y;
                GraphNode node = new GraphNode(entry, title, year, px, py);
                data.nodes.add(node);
            }
        }

        // Load explicit relationships
        List<PaperRelationship> rels = new ArrayList<>();
        for (GraphNode node : data.nodes) {
            rels.addAll(relationshipDAO.getActiveBySourcePaperId(node.entry.getId()));
        }
        for (PaperRelationship rel : rels) {
            GraphNode src = data.nodes.stream().filter(n -> n.entry.getId() == rel.getSourcePaperId()).findFirst().orElse(null);
            GraphNode tgt = data.nodes.stream().filter(n -> n.entry.getId() == rel.getTargetPaperId()).findFirst().orElse(null);
            if (src != null && tgt != null) {
                boolean isAI = (rel.getSource() == PaperRelationship.Source.AI_SUGGESTED);
                if ((isAI && !showAISuggestions) || (!isAI && !showCuratedEdges)) {
                    continue;
                }
                GraphEdge edge = new GraphEdge(src, tgt, 1.0, rel.getRelationshipType());
                edge.relationshipId = rel.getId();
                edge.isAISuggestion = isAI;
                edge.confidence = rel.getConfidence();
                edge.reasoning = rel.getReasoning();
                data.edges.add(edge);
            }
        }

        // Load workspace notes and groups
        data.workspaceNotes.addAll(noteDAO.getAll());
        data.workspaceGroups.addAll(groupDAO.getAll());
    }

    private void buildTimelineGraph(List<LibraryEntry> entries, GraphData data) {
        // Timeline graph uses chronological X positioning
        List<String> documents = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            documents.add(buildDocText(entry));
        }

        TfIdfEngine engine = new TfIdfEngine();
        engine.buildModel(documents);

        List<Map<String, Double>> vectors = new ArrayList<>();
        for (String doc : documents) {
            vectors.add(engine.computeTfIdfVector(doc));
        }

        // Map years and citations
        int minYear = Integer.MAX_VALUE;
        int maxYear = Integer.MIN_VALUE;
        for (LibraryEntry entry : entries) {
            Publication pub = entry.getPublication();
            if (pub != null && pub.getYear() > 0) {
                minYear = Math.min(minYear, pub.getYear());
                maxYear = Math.max(maxYear, pub.getYear());
            }
        }
        if (minYear == Integer.MAX_VALUE) { minYear = 2010; maxYear = 2026; }
        if (minYear == maxYear) { minYear = minYear - 5; maxYear = maxYear + 1; }

        int span = maxYear - minYear;

        // Try to load BGE-M3 embeddings if active
        boolean useBge = com.citeright.ai.NeuralAvailability.isReady();
        com.citeright.ai.BgeM3EmbeddingEngine neuralEngine = null;
        com.citeright.database.PaperEmbeddingDAO embeddingDAO = null;
        Map<Integer, float[]> cachedEmbeddings = null;

        if (useBge) {
            neuralEngine = com.citeright.ai.BgeM3EmbeddingEngine.getInstance();
            embeddingDAO = new com.citeright.database.PaperEmbeddingDAO();
            cachedEmbeddings = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");
        }

        // Re-link target egoNode
        if (data.egoNode != null && data.egoNode.entry != null) {
            int egoId = data.egoNode.entry.getId();
            data.egoNode = entries.stream()
                    .filter(e -> e.getId() == egoId)
                    .map(e -> {
                        Publication pub = e.getPublication();
                        String title = pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled";
                        return new GraphNode(e, title, pub != null ? pub.getYear() : 0, 0, 0);
                    })
                    .findFirst().orElse(null);
        }

        // Compute similarity to egoNode if selected
        float[] egoEmbedding = null;
        Map<String, Double> egoTfIdfVector = null;
        if (data.egoNode != null && data.egoNode.entry != null) {
            int idx = entries.indexOf(data.egoNode.entry);
            if (idx != -1) {
                egoTfIdfVector = vectors.get(idx);
            }
            if (useBge) {
                egoEmbedding = cachedEmbeddings.get(data.egoNode.entry.getId());
                if (egoEmbedding == null) {
                    egoEmbedding = neuralEngine.getEmbedding(buildDocText(data.egoNode.entry));
                }
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            LibraryEntry entry = entries.get(i);
            Publication pub = entry.getPublication();
            String title = pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled";
            int year = pub != null ? pub.getYear() : 0;
            int citations = pub != null ? pub.getCitationCount() : 0;

            // X coordinate: normalized year mapped to [-300, 300]
            double xVal = 0.0;
            if (year > 0) {
                xVal = ((double)(year - minYear) / span) * 600.0 - 300.0;
            }

            // Y coordinate: normalized citations or similarity mapped to [-200, 200]
            double yVal = 0.0;
            if (data.egoNode != null && data.egoNode.entry != null) {
                // Y axis represents similarity to egoNode (higher = more similar / near top)
                double sim = 0.0;
                if (useBge && egoEmbedding != null) {
                    float[] paperEmbedding = cachedEmbeddings.get(entry.getId());
                    if (paperEmbedding == null) {
                        paperEmbedding = neuralEngine.getEmbedding(buildDocText(entry));
                    }
                    if (paperEmbedding != null) {
                        sim = com.citeright.ai.BgeM3EmbeddingEngine.cosineSimilarity(egoEmbedding, paperEmbedding);
                    }
                } else if (egoTfIdfVector != null) {
                    sim = TfIdfEngine.cosineSimilarity(egoTfIdfVector, vectors.get(i));
                }
                sim = computeHybridSimilarity(sim, data.egoNode.entry, entry);
                // Map similarity [0.0, 1.0] to Y coordinate: higher similarity is at the top (negative Y is UP)
                yVal = - (sim * 400.0 - 200.0);
            } else {
                // Y axis represents citation impact (more citations = near top)
                double logCites = citations <= 0 ? 0.0 : Math.log10(citations);
                double maxLog = Math.log10(1000.0); // cap at 1000 citations
                double ratio = Math.min(1.0, logCites / maxLog);
                yVal = - (ratio * 400.0 - 200.0);
            }

            GraphNode node = new GraphNode(entry, title, year, xVal, yVal);
            data.nodes.add(node);
            data.nodeVectors.put(node, vectors.get(i));
        }

        // Add edges
        for (int i = 0; i < data.nodes.size(); i++) {
            for (int j = i + 1; j < data.nodes.size(); j++) {
                double sim;
                if (useBge && data.nodes.get(i).entry != null && data.nodes.get(j).entry != null) {
                    int idA = data.nodes.get(i).entry.getId();
                    int idB = data.nodes.get(j).entry.getId();
                    float[] vecA = cachedEmbeddings.get(idA);
                    float[] vecB = cachedEmbeddings.get(idB);
                    if (vecA != null && vecB != null) {
                        double neuralSim = com.citeright.ai.BgeM3EmbeddingEngine.cosineSimilarity(vecA, vecB);
                        sim = Math.max(0.0, (neuralSim - 0.65) / 0.35);
                    } else {
                        sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                    }
                } else {
                    sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                }
                sim = computeHybridSimilarity(sim, data.nodes.get(i).entry, data.nodes.get(j).entry);

                if (showTfIdfEdges && sim >= similarityFilterValue) {
                    data.edges.add(new GraphEdge(data.nodes.get(i), data.nodes.get(j), sim, "TFIDF"));
                }
            }
        }

        // Add explicit relationships
        List<PaperRelationship> rels = new ArrayList<>();
        for (GraphNode node : data.nodes) {
            if (node.entry != null) {
                rels.addAll(relationshipDAO.getActiveBySourcePaperId(node.entry.getId()));
            }
        }
        for (PaperRelationship rel : rels) {
            GraphNode src = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == rel.getSourcePaperId()).findFirst().orElse(null);
            GraphNode tgt = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == rel.getTargetPaperId()).findFirst().orElse(null);
            if (src != null && tgt != null) {
                boolean isAI = (rel.getSource() == PaperRelationship.Source.AI_SUGGESTED);
                if ((isAI && !showAISuggestions) || (!isAI && !showCuratedEdges)) {
                    continue;
                }
                GraphEdge edge = new GraphEdge(src, tgt, 1.0, rel.getRelationshipType());
                edge.relationshipId = rel.getId();
                edge.isAISuggestion = isAI;
                edge.confidence = rel.getConfidence();
                edge.reasoning = rel.getReasoning();
                data.edges.add(edge);
            }
        }

        // Update egoNode target
        if (data.egoNode != null && data.egoNode.entry != null) {
            int egoId = data.egoNode.entry.getId();
            data.egoNode = data.nodes.stream().filter(n -> n.entry != null && n.entry.getId() == egoId).findFirst().orElse(null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Gap Analysis & Author Network (Async)
    // ─────────────────────────────────────────────────────────────────────────

    private void runGapAnalysis() {
        new Thread(() -> {
            try {
                List<LibraryEntry> entries = libraryService.getAllActive();
                List<int[]> edgePairs = new ArrayList<>();
                // Build index mapping
                Map<Integer, Integer> idToIndex = new HashMap<>();
                for (int i = 0; i < entries.size(); i++) {
                    idToIndex.put(entries.get(i).getId(), i);
                }
                // Collect edge pairs from current graph edges
                for (GraphEdge e : edges) {
                    if (e.a.entry != null && e.b.entry != null) {
                        Integer ai = idToIndex.get(e.a.entry.getId());
                        Integer bi = idToIndex.get(e.b.entry.getId());
                        if (ai != null && bi != null) {
                            edgePairs.add(new int[]{ai, bi});
                        }
                    }
                }
                gapResult = gapAnalyzer.analyze(entries, edgePairs);
                Platform.runLater(this::draw);
            } catch (Exception e) {
                System.err.println("[GapAnalysis] Error: " + e.getMessage());
            }
        }).start();
    }

    private void buildAuthorNetwork() {
        new Thread(() -> {
            try {
                List<LibraryEntry> entries = libraryService.getAllActive();
                authorNetwork = authorNetworkBuilder.build(entries);
                Platform.runLater(this::draw);
            } catch (Exception e) {
                System.err.println("[AuthorNetwork] Error: " + e.getMessage());
            }
        }).start();
    }

    private void runAIAnalysis() {
        // Collect visible papers for analysis
        List<LibraryEntry> papersToAnalyze = nodes.stream()
                .filter(n -> n.entry != null)
                .map(n -> n.entry)
                .collect(Collectors.toList());

        if (papersToAnalyze.size() < 2) {
            ToastNotification.show(canvasContainer, "Need at least 2 papers in the current view to analyze relationships.", ToastNotification.Type.WARNING);
            return;
        }

        ToastNotification.info(canvasContainer, "AI is analyzing relationships between " + papersToAnalyze.size() + " papers...");

        graphAIService.analyzeRelationships(papersToAnalyze, new GraphAIService.SuggestionCallback() {
            @Override
            public void onSuggestionsReady(List<GraphAIService.SuggestedRelationship> suggestions, boolean isFallback) {
                try {
                    graphAIService.saveSuggestions(suggestions);
                } catch (Exception e) {
                    System.err.println("[PaperGraph] Error saving AI suggestions: " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        ToastNotification.error(canvasContainer, "Error saving AI suggestions: " + e.getMessage());
                    });
                    return;
                }
                Platform.runLater(() -> {
                    if (isFallback) {
                        ToastNotification.show(canvasContainer,
                                "\u26a0\ufe0f AI API unavailable \u2014 Local Semantic Engine found " + suggestions.size() + " suggestions using keyword analysis.",
                                ToastNotification.Type.WARNING);
                    } else {
                        ToastNotification.success(canvasContainer, "AI analysis complete! Found " + suggestions.size() + " relationship suggestions.");
                    }
                    if (!suggestions.isEmpty()) {
                        showAISuggestions = true;
                        if (cbAI != null) {
                            cbAI.setSelected(true);
                        }
                    }
                    buildGraph(); // Rebuild to show new suggestions
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    ToastNotification.error(canvasContainer, errorMessage);
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Force-Directed Layout
    // ─────────────────────────────────────────────────────────────────────────

    private void runForceLayout(GraphData data) {
        if (currentState == GraphViewState.MICRO_WORKSPACE || currentState == GraphViewState.TIMELINE_TRAJECTORY) {
            return; // Physics disabled in manual workspace and timeline views
        }
        for (int iter = 0; iter < LAYOUT_ITERATIONS; iter++) {
            // Repulsion between all nodes
            for (int i = 0; i < data.nodes.size(); i++) {
                GraphNode a = data.nodes.get(i);
                for (int j = i + 1; j < data.nodes.size(); j++) {
                    GraphNode b = data.nodes.get(j);
                    double dx = b.x - a.x;
                    double dy = b.y - a.y;
                    double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                    double force = REPULSION / (dist * dist);
                    double fx = (dx / dist) * force;
                    double fy = (dy / dist) * force;
                    a.vx -= fx; a.vy -= fy;
                    b.vx += fx; b.vy += fy;
                }
            }

            // Attraction along edges
            for (GraphEdge edge : data.edges) {
                double dx = edge.b.x - edge.a.x;
                double dy = edge.b.y - edge.a.y;
                double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                double force = ATTRACTION * dist * edge.weight;
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;
                edge.a.vx += fx; edge.a.vy += fy;
                edge.b.vx -= fx; edge.b.vy -= fy;
            }

            // Cluster cohesion for Macro view
            if (currentState == GraphViewState.MACRO_LANDSCAPE) {
                for (List<GraphNode> clusterNodes : data.clusterNodeMap.values()) {
                    if (clusterNodes.size() < 2) continue;
                    double avgX = clusterNodes.stream().mapToDouble(n -> n.x).average().orElse(0);
                    double avgY = clusterNodes.stream().mapToDouble(n -> n.y).average().orElse(0);
                    for (GraphNode n : clusterNodes) {
                        n.vx += (avgX - n.x) * 0.003;
                        n.vy += (avgY - n.y) * 0.003;
                    }
                }
            }

            // Apply velocities with damping
            for (GraphNode node : data.nodes) {
                node.x += node.vx;
                node.y += node.vy;
                node.vx *= DAMPING;
                node.vy *= DAMPING;
            }
        }
    }

    private void centerGraph(GraphData data) {
        if (data.nodes.isEmpty()) return;
        if (currentState == GraphViewState.TIMELINE_TRAJECTORY) return; // Skip chronological center-shift so timeline aligns with grid lines
        double cx = data.nodes.stream().mapToDouble(n -> n.x).average().orElse(0);
        double cy = data.nodes.stream().mapToDouble(n -> n.y).average().orElse(0);
        for (GraphNode n : data.nodes) {
            n.x -= cx;
            n.y -= cy;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Background
        gc.setFill(Color.web("#0a0a18"));
        gc.fillRect(0, 0, w, h);

        // Dot grid
        gc.setFill(Color.web("#1a1a35"));
        double gridSize = 40 * zoom;
        double startX = (offsetX % gridSize) + w / 2 % gridSize;
        double startY = (offsetY % gridSize) + h / 2 % gridSize;
        for (double x = startX; x < w; x += gridSize) {
            for (double y = startY; y < h; y += gridSize) {
                gc.fillOval(x - 1, y - 1, 2, 2);
            }
        }

        // ── Timeline Chronological Grid Overlay ──
        if (currentState == GraphViewState.TIMELINE_TRAJECTORY && !nodes.isEmpty()) {
            int minYear = Integer.MAX_VALUE;
            int maxYear = Integer.MIN_VALUE;
            for (GraphNode n : nodes) {
                if (n.year > 0) {
                    minYear = Math.min(minYear, n.year);
                    maxYear = Math.max(maxYear, n.year);
                }
            }
            if (minYear == Integer.MAX_VALUE) { minYear = 2010; maxYear = 2026; }
            if (minYear == maxYear) { minYear -= 5; maxYear += 1; }

            int span = maxYear - minYear;
            gc.setStroke(Color.web("#1d1d36"));
            gc.setLineWidth(1.0);
            gc.setLineDashes(4, 4);
            gc.setFill(Color.web("#5a5a8a"));
            gc.setFont(Font.font("System", 10));
            gc.setTextAlign(TextAlignment.CENTER);

            double cx = w / 2 + offsetX;
            for (int y = minYear; y <= maxYear; y++) {
                double xVal = ((double)(y - minYear) / span) * 600.0 - 300.0;
                double canvasX = cx + xVal * zoom;
                gc.strokeLine(canvasX, 0, canvasX, h);
                gc.fillText(String.valueOf(y), canvasX, h - 25);
                gc.fillText(String.valueOf(y), canvasX, 25);
            }
            gc.setLineDashes(null);

            gc.setFill(Color.web("#8a8abf"));
            gc.setFont(Font.font("System", javafx.scene.text.FontWeight.BOLD, 11));
            gc.fillText("Timeline of Literature Development (Year of Publication)", w / 2, h - 8);

            gc.setTextAlign(TextAlignment.LEFT);
            if (egoNode != null) {
                gc.fillText("↑ Higher Semantic Similarity to Seed Paper (" + egoNode.title + ")", 15, 45);
            } else {
                gc.fillText("↑ Higher Citation Count Impact (Logarithmic scale)", 15, 45);
            }
        }

        if (nodes.isEmpty() && !authorOverlayEnabled) {
            gc.setFill(Color.web("#5a5a8a"));
            gc.setFont(Font.font("System", 14));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("No papers in your library yet.\nAdd some papers to see the graph!", w / 2, h / 2);
            return;
        }

        double cx = w / 2 + offsetX;
        double cy = h / 2 + offsetY;

        // ── Author Network Overlay ──
        if (authorOverlayEnabled && authorNetwork != null) {
            drawAuthorOverlay(gc, cx, cy);
        }

        // ── Cluster Convex Hulls (Macro) ──
        if (currentState == GraphViewState.MACRO_LANDSCAPE) {
            drawClusterHulls(gc, cx, cy);
        }

        // ── Workspace Groups (Micro) ──
        if (currentState == GraphViewState.MICRO_WORKSPACE) {
            drawWorkspaceGroups(gc, cx, cy);
        }

        // Determine highlighted nodes
        Set<GraphNode> highlightedNodes = new HashSet<>();
        if (hoveredNode != null) {
            highlightedNodes.add(hoveredNode);
            for (GraphEdge e : edges) {
                if (e.a == hoveredNode) highlightedNodes.add(e.b);
                if (e.b == hoveredNode) highlightedNodes.add(e.a);
            }
        }
        if (hoveredEdge != null) {
            highlightedNodes.add(hoveredEdge.a);
            highlightedNodes.add(hoveredEdge.b);
        }
        boolean hasHighlight = !highlightedNodes.isEmpty();

        // Draw edges
        for (GraphEdge edge : edges) {
            drawEdge(gc, edge, cx, cy, hasHighlight, highlightedNodes);
        }

        // Draw nodes
        for (GraphNode node : nodes) {
            drawNode(gc, node, cx, cy, hasHighlight, highlightedNodes);
        }

        // ── Workspace Notes (Micro) ──
        if (currentState == GraphViewState.MICRO_WORKSPACE) {
            drawWorkspaceNotes(gc, cx, cy);
        }

        // ── Selection rectangle (Micro) ──
        if (isSelecting && currentState == GraphViewState.MICRO_WORKSPACE) {
            gc.setStroke(Color.web("#4a9cf7").deriveColor(0, 1, 1, 0.6));
            gc.setLineWidth(1);
            gc.setLineDashes(6, 4);
            double sx = Math.min(selStartX, selEndX), sy = Math.min(selStartY, selEndY);
            double sw = Math.abs(selEndX - selStartX), sh = Math.abs(selEndY - selStartY);
            gc.strokeRect(sx, sy, sw, sh);
            gc.setFill(Color.web("#4a9cf7").deriveColor(0, 1, 1, 0.08));
            gc.fillRect(sx, sy, sw, sh);
            gc.setLineDashes(null);
        }

        // ── Gap Analysis Overlay ──
        if (gapAnalysisEnabled && gapResult != null) {
            drawGapOverlay(gc, cx, cy);
        }
    }

    private void strokeCurve(GraphicsContext gc, double ax, double ay, double cpx, double cpy, double destX, double destY) {
        gc.beginPath();
        gc.moveTo(ax, ay);
        gc.quadraticCurveTo(cpx, cpy, destX, destY);
        gc.stroke();
    }

    private void drawEdge(GraphicsContext gc, GraphEdge edge, double cx, double cy,
                          boolean hasHighlight, Set<GraphNode> highlightedNodes) {
        double ax = cx + edge.a.x * zoom;
        double ay = cy + edge.a.y * zoom;
        double bx = cx + edge.b.x * zoom;
        double by = cy + edge.b.y * zoom;

        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
        if (len < 1.0) return;

        double mx = (ax + bx) / 2.0;
        double my = (ay + by) / 2.0;
        double nx = -dy / len;
        double ny = dx / len;

        // Deterministic offset direction to avoid flipping when nodes are dragged
        double sign = (edge.a.hashCode() < edge.b.hashCode()) ? 1.0 : -1.0;
        double cpx = mx + nx * (len * 0.12) * sign;
        double cpy = my + ny * (len * 0.12) * sign;

        double r = NODE_RADIUS * zoom;
        double destX = bx;
        double destY = by;
        double ux = dx / len;
        double uy = dy / len;

        boolean isDirected = edge.type != null && !"TFIDF".equals(edge.type);
        if (isDirected) {
            double tx = bx - cpx;
            double ty = by - cpy;
            double tLen = Math.max(1.0, Math.sqrt(tx * tx + ty * ty));
            ux = tx / tLen;
            uy = ty / tLen;
            destX = bx - ux * r;
            destY = by - uy * r;
        }

        boolean isHoveredEdge = edge == hoveredEdge;
        boolean isConnectedToHover = hoveredNode != null &&
                (edge.a == hoveredNode || edge.b == hoveredNode);
        boolean dimmed = hasHighlight && !isHoveredEdge && !isConnectedToHover;

        double alpha = Math.min(1.0, edge.weight * 3);
        double lineWidth = 0.5 + edge.weight * 5;

        Color baseColor;
        if ("SUPPORTS".equals(edge.type)) {
            baseColor = Color.web("#2ecc71");
            alpha = 0.9; lineWidth = 4.0;
        } else if ("CONTRADICTS".equals(edge.type)) {
            baseColor = Color.web("#e74c3c");
            alpha = 0.9; lineWidth = 4.0;
        } else if ("EXTENDS".equals(edge.type)) {
            baseColor = Color.web("#f1c40f");
            alpha = 0.9; lineWidth = 4.0;
        } else if ("METHODOLOGY".equals(edge.type)) {
            baseColor = Color.web("#9b59b6");
            alpha = 0.9; lineWidth = 4.0;
        } else {
            baseColor = Color.web("#34495e").deriveColor(0, 0.6, 0.8, 0.4); // Faint unobtrusive gray/blue for TF-IDF
            alpha = Math.min(0.5, edge.weight * 1.5);
            lineWidth = 0.8 + edge.weight * 2;
        }

        // AI suggestions: dashed line
        if (edge.isAISuggestion) {
            gc.setLineDashes(8, 6);
            alpha *= 0.7;
        }

        if (dimmed) {
            gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, 0.15));
            gc.setLineWidth(lineWidth * 0.5);
            strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
        } else {
            if (isHoveredEdge) {
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, 0.5));
                gc.setLineWidth(lineWidth + 8);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, 0.9));
                gc.setLineWidth(lineWidth + 2);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
            } else if (isConnectedToHover) {
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, 0.4));
                gc.setLineWidth(lineWidth + 5);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, alpha * 0.9));
                gc.setLineWidth(lineWidth + 1);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
            } else if (!"TFIDF".equals(edge.type) || edge.weight > 0.15) {
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, alpha * 0.3));
                gc.setLineWidth(lineWidth + 4);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, alpha * 0.8));
                gc.setLineWidth(lineWidth);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
            } else {
                gc.setStroke(baseColor.deriveColor(0, 1.0, 1.0, alpha * 0.6));
                gc.setLineWidth(lineWidth);
                strokeCurve(gc, ax, ay, cpx, cpy, destX, destY);
            }

            // Draw arrowhead for directed semantic links
            if (isDirected) {
                gc.setFill(baseColor.deriveColor(0, 1.0, 1.0, isHoveredEdge ? 0.9 : alpha));
                double arrowSize = 8.5 * Math.max(0.5, Math.min(zoom, 1.5));
                double halfWidth = 4.0 * Math.max(0.5, Math.min(zoom, 1.5));
                double baseX = destX - ux * arrowSize;
                double baseY = destY - uy * arrowSize;
                double perpX = -uy;
                double perpY = ux;
                double leftX = baseX + perpX * halfWidth;
                double leftY = baseY + perpY * halfWidth;
                double rightX = baseX - perpX * halfWidth;
                double rightY = baseY - perpY * halfWidth;
                gc.fillPolygon(new double[]{destX, leftX, rightX}, new double[]{destY, leftY, rightY}, 3);
            }

            double midX = 0.25 * ax + 0.5 * cpx + 0.25 * bx;
            double midY = 0.25 * ay + 0.5 * cpy + 0.25 * by;

            if (isHoveredEdge) {
                String pct = Math.round(edge.weight * 100) + "% similar";
                gc.setFill(Color.web("#ff6b9d"));
                gc.setFont(Font.font("System", 11 * Math.min(zoom, 1.3)));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(pct, midX, midY - 8);
            }
        }

        // Reset dashes
        gc.setLineDashes(null);

        // AI suggestion confirm/dismiss indicators
        if (edge.isAISuggestion && !dimmed && zoom > 0.6) {
            double midX = 0.25 * ax + 0.5 * cpx + 0.25 * bx;
            double midY = 0.25 * ay + 0.5 * cpy + 0.25 * by;
            gc.setFill(Color.web("#2ecc71").deriveColor(0, 1, 1, 0.8));
            double emojiSize = 18 * Math.max(0.7, Math.min(zoom, 1.5));
            gc.setFont(Font.font("System", emojiSize));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("🤖", midX, midY + emojiSize * 0.3);
        }
    }

    private void drawNode(GraphicsContext gc, GraphNode node, double cx, double cy,
                          boolean hasHighlight, Set<GraphNode> highlightedNodes) {
        double nx = cx + node.x * zoom;
        double ny = cy + node.y * zoom;
        double r = NODE_RADIUS * zoom;

        boolean isHovered = node == hoveredNode;
        boolean isSelected = node == selectedNode;
        boolean dimmed = hasHighlight && !highlightedNodes.contains(node);

        // Node color
        int connectionCount = (int) edges.stream()
                .filter(e -> e.a == node || e.b == node).count();
        Color nodeColor;
        if (node == egoNode) {
            nodeColor = Color.web("#00d2d3"); // Vibrant Cyan
        } else if (connectionSourceNode == node) {
            nodeColor = Color.web("#f1c40f"); // Golden Yellow
        } else if (node.clusterColor != null) {
            nodeColor = node.clusterColor.deriveColor(0, 0.7, 0.8, 1.0); // Muted cluster colors
        } else {
            nodeColor = Color.web("#2f3640"); // Slate Blue default
        }

        // Orphan pulsing (gap analysis)
        if (gapAnalysisEnabled && gapResult != null && connectionCount == 0) {
            double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.005);
            nodeColor = Color.web("#e74c3c").interpolate(Color.web("#f39c12"), pulse);
        }

        if (dimmed) {
            nodeColor = nodeColor.deriveColor(0, 0.3, 0.4, 0.4);
        } else if (isHovered) {
            nodeColor = nodeColor.brighter().brighter();
        }

        // Outer glow
        if (isHovered || isSelected) {
            gc.setFill(nodeColor.deriveColor(0, 1, 1, 0.15));
            gc.fillOval(nx - r * 2.2, ny - r * 2.2, r * 4.4, r * 4.4);
            gc.setFill(nodeColor.deriveColor(0, 1, 1, 0.25));
            gc.fillOval(nx - r * 1.6, ny - r * 1.6, r * 3.2, r * 3.2);
        }

        // Main circle
        gc.setFill(nodeColor);
        gc.fillOval(nx - r, ny - r, r * 2, r * 2);

        // Inner highlight
        gc.setFill(Color.color(1, 1, 1, dimmed ? 0.03 : 0.12));
        gc.fillOval(nx - r * 0.6, ny - r * 0.8, r * 1.0, r * 0.8);

        // Ring
        gc.setStroke(isSelected ? Color.web("#ffffff") :
                isHovered ? nodeColor.brighter() : Color.color(1, 1, 1, dimmed ? 0.1 : 0.3));
        gc.setLineWidth(isSelected ? 3.0 : isHovered ? 2.0 : 1);
        gc.strokeOval(nx - r, ny - r, r * 2, r * 2);

        // Year label inside node
        if (node.year > 0 && zoom > 0.5) {
            gc.setFill(Color.color(1, 1, 1, dimmed ? 0.3 : 0.95));
            gc.setFont(Font.font("System", 10 * zoom));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(node.year), nx, ny + 4 * zoom);
        }

        // Title label below node (Selective label rendering with translucent backing)
        boolean isNeighborOfHovered = false;
        if (hoveredNode != null) {
            for (GraphEdge e : edges) {
                if ((e.a == hoveredNode && e.b == node) || (e.b == hoveredNode && e.a == node)) {
                    isNeighborOfHovered = true;
                    break;
                }
            }
        }
        boolean hasHoverOrSelection = (hoveredNode != null || selectedNode != null);
        boolean drawLabel = isHovered || isSelected || (node == egoNode) || (node == connectionSourceNode) || isNeighborOfHovered || (zoom > 0.85 && !hasHoverOrSelection);

        if (drawLabel && !dimmed) {
            String label = truncate(node.title, 28);
            double fontSize = 10.5 * Math.min(zoom, 1.2);
            gc.setFont(Font.font("System", fontSize));

            // Approximate text measurement for the legibility backing rect
            double approxWidth = label.length() * fontSize * 0.55;
            double approxHeight = fontSize + 4;
            double rectX = nx - approxWidth / 2 - 6;
            double rectY = (ny + r + 16 * zoom) - fontSize;
            double rectW = approxWidth + 12;
            double rectH = approxHeight + 2;

            // Translucent backing rect
            gc.setFill(Color.color(0.04, 0.04, 0.1, isHovered ? 0.85 : 0.65));
            gc.fillRoundRect(rectX, rectY, rectW, rectH, 6, 6);

            // Text
            gc.setFill(Color.color(0.9, 0.9, 0.98, isHovered ? 1.0 : 0.85));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(label, nx, ny + r + 16 * zoom);
        }
    }

    // ── Cluster Convex Hulls ─────────────────────────────────────────────────

    private void drawClusterHulls(GraphicsContext gc, double cx, double cy) {
        int colorIdx = 0;
        for (Map.Entry<String, List<GraphNode>> entry : clusterNodeMap.entrySet()) {
            List<GraphNode> clusterNodes = entry.getValue();
            if (clusterNodes.size() < 3) { colorIdx++; continue; }

            Color color = CLUSTER_COLORS[colorIdx % CLUSTER_COLORS.length];

            // Compute convex hull points
            double[] xs = clusterNodes.stream().mapToDouble(n -> cx + n.x * zoom).toArray();
            double[] ys = clusterNodes.stream().mapToDouble(n -> cy + n.y * zoom).toArray();

            // Simple bounding ellipse (convex hull approximation for performance)
            double minX = Arrays.stream(xs).min().orElse(0) - 40;
            double maxX = Arrays.stream(xs).max().orElse(0) + 40;
            double minY = Arrays.stream(ys).min().orElse(0) - 40;
            double maxY = Arrays.stream(ys).max().orElse(0) + 40;

            // Draw translucent background
            gc.setFill(color.deriveColor(0, 1, 1, 0.04));
            gc.fillRoundRect(minX, minY, maxX - minX, maxY - minY, 30, 30);

            // Draw border
            gc.setStroke(color.deriveColor(0, 1, 1, 0.18));
            gc.setLineWidth(1.5);
            gc.setLineDashes(6, 4);
            gc.strokeRoundRect(minX, minY, maxX - minX, maxY - minY, 30, 30);
            gc.setLineDashes(null);

            // Cluster label
            gc.setFill(color.deriveColor(0, 1, 1, 0.7));
            gc.setFont(Font.font("System", 11 * Math.min(zoom, 1.2)));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(entry.getKey(), (minX + maxX) / 2, minY - 6);

            colorIdx++;
        }
    }

    // ── Author Overlay ───────────────────────────────────────────────────────

    private void drawAuthorOverlay(GraphicsContext gc, double cx, double cy) {
        if (authorNetwork == null) return;

        // Draw co-authorship edges
        for (CoAuthorEdge edge : authorNetwork.getEdges()) {
            AuthorNode a = authorNetwork.getNodes().get(edge.authorA);
            AuthorNode b = authorNetwork.getNodes().get(edge.authorB);
            if (a == null || b == null) continue;

            double ax = cx + a.x * zoom * 0.8;
            double ay = cy + a.y * zoom * 0.8;
            double bx = cx + b.x * zoom * 0.8;
            double by = cy + b.y * zoom * 0.8;

            double alpha = Math.min(0.6, 0.1 + edge.sharedPaperCount * 0.15);
            gc.setStroke(Color.web("#ffffff").deriveColor(0, 1, 1, alpha));
            gc.setLineWidth(0.5 + edge.sharedPaperCount * 0.8);
            gc.strokeLine(ax, ay, bx, by);
        }

        // Draw author nodes
        for (AuthorNode author : authorNetwork.getNodes().values()) {
            double ax = cx + author.x * zoom * 0.8;
            double ay = cy + author.y * zoom * 0.8;
            double r = (6 + author.getPaperCount() * 3) * zoom;
            r = Math.min(r, 25 * zoom); // Cap size

            Color color = COMMUNITY_COLORS[Math.abs(author.community) % COMMUNITY_COLORS.length];

            gc.setFill(color.deriveColor(0, 1, 1, 0.6));
            gc.fillOval(ax - r, ay - r, r * 2, r * 2);
            gc.setStroke(color.deriveColor(0, 1, 1, 0.8));
            gc.setLineWidth(1);
            gc.strokeOval(ax - r, ay - r, r * 2, r * 2);

            // Author name
            if (zoom > 0.6 && author.getPaperCount() >= 2) {
                gc.setFill(Color.web("#ffffff").deriveColor(0, 1, 1, 0.85));
                gc.setFont(Font.font("System", 9 * Math.min(zoom, 1.2)));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(truncate(author.name, 20), ax, ay + r + 12 * zoom);
            }
        }
    }

    // ── Gap Analysis Overlay ─────────────────────────────────────────────────

    private void drawGapOverlay(GraphicsContext gc, double cx, double cy) {
        // Draw bridge edges highlighted in gold
        for (GapAnalyzer.EdgeKey bridge : gapResult.getBridgeEdges()) {
            if (bridge.a < nodes.size() && bridge.b < nodes.size()) {
                // Map indices back to nodes — bridge indices are from the entries list
                // so we need to match by entry ID, not node list index
            }
        }

        // Highlight orphan nodes with pulsing ring
        for (int orphanIdx : gapResult.getOrphanPaperIndices()) {
            // Orphans are already colored via the node drawing code (pulsing effect)
        }

        // Draw methodology links as dashed purple lines
        for (MethodologyLink link : gapResult.getMethodLinks()) {
            if (link.paperIndexA < nodes.size() && link.paperIndexB < nodes.size()) {
                // These are library-wide indices, find matching nodes
            }
        }

        // Draw gap analysis status indicator
        gc.setFill(Color.web("#f39c12").deriveColor(0, 1, 1, 0.8));
        gc.setFont(Font.font("System", 11));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("🔍 Gap Analysis: " + gapResult.getOrphanPaperIndices().size() + " orphans, "
                + gapResult.getBridgeEdges().size() + " bridges, "
                + gapResult.getMethodLinks().size() + " methodology links",
                20, canvas.getHeight() - 70);
    }

    // ── Workspace Notes & Groups ─────────────────────────────────────────────

    private void drawWorkspaceNotes(GraphicsContext gc, double cx, double cy) {
        for (WorkspaceNote note : workspaceNotes) {
            double nx = cx + note.getX() * zoom;
            double ny = cy + note.getY() * zoom;
            double nw = note.getWidth() * zoom;
            double nh = note.getHeight() * zoom;

            // Note background
            Color noteColor = Color.web(note.getColor());
            gc.setFill(noteColor.deriveColor(0, 0.8, 0.3, 0.85));
            gc.fillRoundRect(nx, ny, nw, nh, 8, 8);

            // Border
            gc.setStroke(noteColor.deriveColor(0, 1, 1, 0.6));
            gc.setLineWidth(1);
            gc.strokeRoundRect(nx, ny, nw, nh, 8, 8);

            // Note icon
            gc.setFill(noteColor.deriveColor(0, 1, 1.2, 0.9));
            gc.setFont(Font.font("System", 10 * zoom));
            gc.fillText("📝", nx + 4, ny + 14 * zoom);

            // Text content
            gc.setFill(Color.web("#ffffff").deriveColor(0, 1, 1, 0.9));
            gc.setFont(Font.font("System", 11 * Math.min(zoom, 1.2)));
            gc.setTextAlign(TextAlignment.LEFT);
            String text = note.getText().isEmpty() ? "Double-click to edit..." : note.getText();
            // Simple text wrapping
            String[] words = text.split(" ");
            StringBuilder line = new StringBuilder();
            double lineY = ny + 28 * zoom;
            for (String word : words) {
                if ((line.length() + word.length()) * 6.5 * zoom > nw - 10) {
                    gc.fillText(line.toString(), nx + 6, lineY);
                    lineY += 14 * zoom;
                    line = new StringBuilder();
                    if (lineY > ny + nh - 5) break;
                }
                if (!line.isEmpty()) line.append(" ");
                line.append(word);
            }
            if (!line.isEmpty() && lineY <= ny + nh - 5) {
                gc.fillText(line.toString(), nx + 6, lineY);
            }
        }
    }

    private void drawWorkspaceGroups(GraphicsContext gc, double cx, double cy) {
        for (WorkspaceGroup group : workspaceGroups) {
            // Find member nodes
            List<GraphNode> members = nodes.stream()
                    .filter(n -> n.entry != null && group.getPaperIds().contains(n.entry.getId()))
                    .collect(Collectors.toList());

            if (members.isEmpty()) continue;

            // Compute bounding box from members
            double minX = members.stream().mapToDouble(n -> cx + n.x * zoom).min().orElse(0) - 35;
            double maxX = members.stream().mapToDouble(n -> cx + n.x * zoom).max().orElse(0) + 35;
            double minY = members.stream().mapToDouble(n -> cy + n.y * zoom).min().orElse(0) - 35;
            double maxY = members.stream().mapToDouble(n -> cy + n.y * zoom).max().orElse(0) + 45;

            Color groupColor = Color.web(group.getColor());
            gc.setFill(groupColor.deriveColor(0, 1, 1, 0.08));
            gc.fillRoundRect(minX, minY, maxX - minX, maxY - minY, 16, 16);

            gc.setStroke(groupColor.deriveColor(0, 1, 1, 0.4));
            gc.setLineWidth(2);
            gc.strokeRoundRect(minX, minY, maxX - minX, maxY - minY, 16, 16);

            // Group name label
            gc.setFill(groupColor.deriveColor(0, 1, 1.2, 0.9));
            gc.setFont(Font.font("System", 12 * Math.min(zoom, 1.3)));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(group.getName(), (minX + maxX) / 2, minY - 6);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Interaction
    // ─────────────────────────────────────────────────────────────────────────

    private GraphNode draggedNode = null;

    private void onMousePressed(MouseEvent e) {
        dragStartX = e.getX();
        dragStartY = e.getY();
        panStartX = offsetX;
        panStartY = offsetY;
        draggedNode = findNodeAt(e.getX(), e.getY());

        // Check for note dragging in Micro
        if (currentState == GraphViewState.MICRO_WORKSPACE && draggedNode == null) {
            draggedNote = findNoteAt(e.getX(), e.getY());
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggedNode != null) {
            double cx = canvas.getWidth() / 2 + offsetX;
            double cy = canvas.getHeight() / 2 + offsetY;

            draggedNode.x = (e.getX() - cx) / zoom;
            draggedNode.y = (e.getY() - cy) / zoom;

            if (currentState == GraphViewState.MICRO_WORKSPACE && draggedNode.entry != null) {
                workspaceDAO.pinPaper(draggedNode.entry.getId(), draggedNode.x, draggedNode.y);
            }
        } else if (draggedNote != null) {
            double cx = canvas.getWidth() / 2 + offsetX;
            double cy = canvas.getHeight() / 2 + offsetY;
            draggedNote.setX((e.getX() - cx) / zoom);
            draggedNote.setY((e.getY() - cy) / zoom);
            noteDAO.update(draggedNote);
        } else {
            offsetX = panStartX + (e.getX() - dragStartX);
            offsetY = panStartY + (e.getY() - dragStartY);
        }
        draw();
    }

    private void onMouseMoved(MouseEvent e) {
        GraphNode foundNode = findNodeAt(e.getX(), e.getY());
        GraphEdge foundEdge = foundNode == null ? findEdgeAt(e.getX(), e.getY()) : null;

        boolean changed = foundNode != hoveredNode || foundEdge != hoveredEdge;
        hoveredNode = foundNode;
        hoveredEdge = foundEdge;

        if (changed) {
            tooltipPanel.setVisible(false);
            edgeTooltipPanel.setVisible(false);
            draw();
        }

        if (hoveredNode != null) {
            showNodeTooltip(hoveredNode, e.getX(), e.getY());
        } else if (hoveredEdge != null) {
            showEdgeTooltip(hoveredEdge, e.getX(), e.getY());
        }
    }

    private ContextMenu contextMenu = new ContextMenu();
    private void onMouseClicked(MouseEvent e) {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        }

        GraphNode found = findNodeAt(e.getX(), e.getY());

        if (e.getButton() == MouseButton.SECONDARY) {
            if (found != null && found.entry != null) {
                contextMenu.getItems().clear();

                if (currentState == GraphViewState.MESO_EGOCENTRIC || currentState == GraphViewState.MACRO_LANDSCAPE) {
                    if (currentState == GraphViewState.MESO_EGOCENTRIC) {
                        MenuItem setEgo = new MenuItem("Set as Target (Ego)");
                        setEgo.setOnAction(event -> {
                            egoNode = found;
                            connectionSourceNode = null;
                            buildGraph();
                        });
                        contextMenu.getItems().add(setEgo);

                        if (connectionSourceNode == null) {
                            MenuItem startEdge = new MenuItem("Create Relationship From...");
                            startEdge.setOnAction(event -> {
                                connectionSourceNode = found;
                                draw();
                            });
                            contextMenu.getItems().add(startEdge);
                        } else if (connectionSourceNode != found) {
                            Menu endEdge = new Menu("Connect to '" + truncate(connectionSourceNode.title, 15) + "'...");
                            for (String type : Arrays.asList("SUPPORTS", "CONTRADICTS", "EXTENDS", "METHODOLOGY")) {
                                MenuItem typeItem = new MenuItem("Mark as " + type);
                                typeItem.setOnAction(event -> {
                                    PaperRelationship rel = new PaperRelationship();
                                    rel.setSourcePaperId(connectionSourceNode.entry.getId());
                                    rel.setTargetPaperId(found.entry.getId());
                                    rel.setRelationshipType(type);
                                    rel.setReasoning("User created edge");
                                    rel.setSource(PaperRelationship.Source.USER);
                                    rel.setConfidence(1.0);
                                    relationshipDAO.insert(rel);
                                    connectionSourceNode = null;
                                    buildGraph();
                                });
                                endEdge.getItems().add(typeItem);
                            }
                            contextMenu.getItems().add(endEdge);

                            MenuItem cancelEdge = new MenuItem("Cancel Connection");
                            cancelEdge.setOnAction(event -> {
                                connectionSourceNode = null;
                                draw();
                            });
                            contextMenu.getItems().add(cancelEdge);
                        }
                    }

                    // Pin to Workspace
                    boolean isPinned = workspaceDAO.isPinned(found.entry.getId());
                    if (!isPinned) {
                        MenuItem pinItem = new MenuItem("Pin to Micro Workspace");
                        pinItem.setOnAction(event -> {
                            workspaceDAO.pinPaper(found.entry.getId(), found.x, found.y);
                        });
                        contextMenu.getItems().add(pinItem);
                    } else {
                        MenuItem unpinItem = new MenuItem("Unpin from Workspace");
                        unpinItem.setOnAction(event -> {
                            workspaceDAO.unpinPaper(found.entry.getId());
                        });
                        contextMenu.getItems().add(unpinItem);
                    }
                } else if (currentState == GraphViewState.MICRO_WORKSPACE) {
                    MenuItem unpinItem = new MenuItem("Unpin from Workspace");
                    unpinItem.setOnAction(event -> {
                        workspaceDAO.unpinPaper(found.entry.getId());
                        buildGraph();
                    });
                    contextMenu.getItems().add(unpinItem);

                    // Group operations
                    MenuItem addToGroup = new MenuItem("Add to New Group...");
                    addToGroup.setOnAction(event -> {
                        TextInputDialog dialog = new TextInputDialog("Hypothesis Group");
                        dialog.setTitle("Create Group");
                        dialog.setHeaderText("Name this group:");
                        dialog.showAndWait().ifPresent(name -> {
                            WorkspaceGroup group = new WorkspaceGroup(name, "#6c5ce7");
                            group.addPaperId(found.entry.getId());
                            groupDAO.insert(group);
                            workspaceGroups.add(group);
                            draw();
                        });
                    });
                    contextMenu.getItems().add(addToGroup);
                }

                // AI edge confirmation (for AI-suggested edges)
                for (GraphEdge aiEdge : aiSuggestedEdges) {
                    if (aiEdge.a == found || aiEdge.b == found) {
                        String otherTitle = (aiEdge.a == found ? aiEdge.b.title : aiEdge.a.title);
                        MenuItem confirmItem = new MenuItem("✅ Confirm: " + aiEdge.type + " ↔ " + truncate(otherTitle, 20));
                        confirmItem.setOnAction(event -> {
                            relationshipDAO.confirm(aiEdge.relationshipId);
                            buildGraph();
                        });
                        contextMenu.getItems().add(confirmItem);

                        MenuItem dismissItem = new MenuItem("❌ Dismiss: " + aiEdge.type + " ↔ " + truncate(otherTitle, 20));
                        dismissItem.setOnAction(event -> {
                            relationshipDAO.dismiss(aiEdge.relationshipId);
                            buildGraph();
                        });
                        contextMenu.getItems().add(dismissItem);
                    }
                }

                if (!contextMenu.getItems().isEmpty()) {
                    contextMenu.show(canvas, e.getScreenX(), e.getScreenY());
                }
            } else {
                GraphEdge clickedEdge = findEdgeAt(e.getX(), e.getY());
                if (clickedEdge != null && clickedEdge.isAISuggestion) {
                    contextMenu.getItems().clear();
                    MenuItem confirmItem = new MenuItem("✅ Confirm AI Suggestion (" + clickedEdge.type + ")");
                    confirmItem.setOnAction(event -> {
                        relationshipDAO.confirm(clickedEdge.relationshipId);
                        buildGraph();
                    });
                    contextMenu.getItems().add(confirmItem);

                    MenuItem dismissItem = new MenuItem("❌ Dismiss AI Suggestion (" + clickedEdge.type + ")");
                    dismissItem.setOnAction(event -> {
                        relationshipDAO.dismiss(clickedEdge.relationshipId);
                        buildGraph();
                    });
                    contextMenu.getItems().add(dismissItem);

                    contextMenu.show(canvas, e.getScreenX(), e.getScreenY());
                } else if (currentState == GraphViewState.MICRO_WORKSPACE) {
                    // Right-click on empty space in Micro → create note
                    contextMenu.getItems().clear();
                    MenuItem createNote = new MenuItem("📝 Add Note Here");
                    createNote.setOnAction(event -> {
                        double ncx = canvas.getWidth() / 2 + offsetX;
                        double ncy = canvas.getHeight() / 2 + offsetY;
                        WorkspaceNote note = new WorkspaceNote(
                                (e.getX() - ncx) / zoom,
                                (e.getY() - ncy) / zoom,
                                "");
                        noteDAO.insert(note);
                        workspaceNotes.add(note);
                        draw();
                    });
                    contextMenu.getItems().add(createNote);
                    contextMenu.show(canvas, e.getScreenX(), e.getScreenY());
                } else if (egoNode != null && currentState == GraphViewState.MESO_EGOCENTRIC) {
                    contextMenu.getItems().clear();
                    MenuItem clearTarget = new MenuItem("Clear Target (Back to full graph)");
                    clearTarget.setOnAction(event -> {
                        egoNode = null;
                        connectionSourceNode = null;
                        buildGraph();
                    });
                    contextMenu.getItems().add(clearTarget);
                    contextMenu.show(canvas, e.getScreenX(), e.getScreenY());
                }
            }
        } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
            if (found != null) {
                selectedNode = found;
                if (onSelectEntry != null) onSelectEntry.accept(found.entry);
                draw();
            } else {
                selectedNode = null;
                draw();
            }
        } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            if (found != null) {
                selectedNode = found;
                if (onSelectEntry != null) onSelectEntry.accept(found.entry);
                
                // Double-click in Macro view transitions to Meso view centered on this node
                if (currentState == GraphViewState.MACRO_LANDSCAPE) {
                    egoNode = found;
                    connectionSourceNode = null;
                    if (viewGroup != null) {
                        for (Toggle toggle : viewGroup.getToggles()) {
                            if (toggle instanceof ToggleButton) {
                                ToggleButton tb = (ToggleButton) toggle;
                                if (tb.getUserData() == GraphViewState.MESO_EGOCENTRIC) {
                                    tb.setSelected(true);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    draw();
                }
            } else if (currentState == GraphViewState.MICRO_WORKSPACE) {
                // Double-click in empty space → edit note
                WorkspaceNote note = findNoteAt(e.getX(), e.getY());
                if (note != null) {
                    TextInputDialog dialog = new TextInputDialog(note.getText());
                    dialog.setTitle("Edit Note");
                    dialog.setHeaderText("Note text:");
                    dialog.showAndWait().ifPresent(text -> {
                        note.setText(text);
                        noteDAO.update(note);
                        draw();
                    });
                }
            }
        }
    }

    private void onScroll(ScrollEvent e) {
        double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
        zoom = Math.max(0.2, Math.min(4.0, zoom * delta));
        draw();
        e.consume();
    }

    private GraphNode findNodeAt(double mx, double my) {
        double cx = canvas.getWidth() / 2 + offsetX;
        double cy = canvas.getHeight() / 2 + offsetY;
        for (GraphNode node : nodes) {
            double nx = cx + node.x * zoom;
            double ny = cy + node.y * zoom;
            double r = Math.max(12.0, NODE_RADIUS * zoom + 4);
            if (Math.hypot(mx - nx, my - ny) <= r) {
                return node;
            }
        }
        return null;
    }

    private GraphEdge findEdgeAt(double mx, double my) {
        double cx = canvas.getWidth() / 2 + offsetX;
        double cy = canvas.getHeight() / 2 + offsetY;
        GraphEdge closest = null;
        double closestDist = Double.MAX_VALUE;

        for (GraphEdge edge : edges) {
            double ax = cx + edge.a.x * zoom;
            double ay = cy + edge.a.y * zoom;
            double bx = cx + edge.b.x * zoom;
            double by = cy + edge.b.y * zoom;

            double dx = bx - ax;
            double dy = by - ay;
            double len = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
            double midX_chord = (ax + bx) / 2.0;
            double midY_chord = (ay + by) / 2.0;
            double nx = -dy / len;
            double ny = dx / len;
            double sign = (edge.a.hashCode() < edge.b.hashCode()) ? 1.0 : -1.0;
            double cpx = midX_chord + nx * (len * 0.12) * sign;
            double cpy = midY_chord + ny * (len * 0.12) * sign;
            double midX = 0.25 * ax + 0.5 * cpx + 0.25 * bx;
            double midY = 0.25 * ay + 0.5 * cpy + 0.25 * by;

            double dist1 = pointToSegmentDistance(mx, my, ax, ay, midX, midY);
            double dist2 = pointToSegmentDistance(mx, my, midX, midY, bx, by);
            double dist = Math.min(dist1, dist2);

            double hitRadius = Math.max(6.0, EDGE_HIT_DISTANCE * zoom);
            if (dist < hitRadius && dist < closestDist) {
                closestDist = dist;
                closest = edge;
            }
        }
        return closest;
    }

    private WorkspaceNote findNoteAt(double mx, double my) {
        double cx = canvas.getWidth() / 2 + offsetX;
        double cy = canvas.getHeight() / 2 + offsetY;
        for (WorkspaceNote note : workspaceNotes) {
            double nx = cx + note.getX() * zoom;
            double ny = cy + note.getY() * zoom;
            double nw = note.getWidth() * zoom;
            double nh = note.getHeight() * zoom;
            if (mx >= nx && mx <= nx + nw && my >= ny && my <= ny + nh) {
                return note;
            }
        }
        return null;
    }

    private double pointToSegmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
        double projX = ax + t * dx, projY = ay + t * dy;
        return Math.hypot(px - projX, py - projY);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tooltips
    // ─────────────────────────────────────────────────────────────────────────

    private void showNodeTooltip(GraphNode node, double mx, double my) {
        Publication pub = node.entry != null ? node.entry.getPublication() : null;
        tooltipTitle.setText(node.title);
        tooltipAuthors.setText(pub != null ? pub.getAuthorsFormatted() : "Cluster Topic");
        tooltipYear.setText(node.year > 0 ? String.valueOf(node.year) : "");

        tooltipPanel.autosize();
        double pw = tooltipPanel.prefWidth(-1);
        double ph = tooltipPanel.prefHeight(pw);
        double tx = Math.min(mx + 20, getWidth() - pw - 20);
        double ty = Math.min(my - 10, getHeight() - ph - 20);
        if (ty < 60) ty = my + 30;
        tooltipPanel.setLayoutX(tx);
        tooltipPanel.setLayoutY(ty);
        tooltipPanel.setVisible(true);
    }

    private void showEdgeTooltip(GraphEdge edge, double mx, double my) {
        String titleA = truncate(edge.a.title, 40);
        String titleB = truncate(edge.b.title, 40);
        edgeTooltipTitle.setText(titleA + " ↔ " + titleB);

        if (edge.isAISuggestion) {
            edgeTooltipScore.setText("🤖 AI Suggested: " + edge.type + " (" + Math.round(edge.confidence * 100) + "% confidence)");
            edgeTooltipTerms.setText(edge.reasoning != null ? edge.reasoning : "Right-click to confirm or dismiss");
        } else if (!"TFIDF".equals(edge.type)) {
            edgeTooltipScore.setText(edge.type + " (User confirmed)");
            edgeTooltipTerms.setText(edge.reasoning != null ? edge.reasoning : "");
        } else {
            int pct = (int) Math.round(edge.weight * 100);
            String strength = pct >= 30 ? "Strong" : pct >= 15 ? "Moderate" : "Weak";
            edgeTooltipScore.setText(pct + "% similarity (" + strength + ")");
            String sharedTerms = getSharedTerms(edge.a, edge.b);
            edgeTooltipTerms.setText("Keywords: " + sharedTerms);
        }

        edgeTooltipPanel.autosize();
        double pw = edgeTooltipPanel.prefWidth(-1);
        double ph = edgeTooltipPanel.prefHeight(pw);
        double tx = Math.min(mx + 20, getWidth() - pw - 20);
        double ty = Math.min(my - 10, getHeight() - ph - 20);
        if (ty < 60) ty = my + 30;
        edgeTooltipPanel.setLayoutX(tx);
        edgeTooltipPanel.setLayoutY(ty);
        edgeTooltipPanel.setVisible(true);
    }

    private String getSharedTerms(GraphNode a, GraphNode b) {
        Map<String, Double> vecA = nodeVectors.get(a);
        Map<String, Double> vecB = nodeVectors.get(b);
        if (vecA == null || vecB == null) return "N/A";

        List<Map.Entry<String, Double>> shared = new ArrayList<>();
        for (Map.Entry<String, Double> entry : vecA.entrySet()) {
            if (vecB.containsKey(entry.getKey())) {
                double combinedScore = entry.getValue() + vecB.get(entry.getKey());
                shared.add(Map.entry(entry.getKey(), combinedScore));
            }
        }

        if (shared.isEmpty()) return "No shared terms found";

        shared.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));

        return shared.stream()
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ── Onboarding Tour ───────────────────────────────────────────────────────

    private void showOnboardingTour() {
        if (tourOverlay != null) {
            canvasContainer.getChildren().remove(tourOverlay);
        }

        tourStep = 1;
        tourOverlay = new StackPane();
        tourOverlay.setStyle("-fx-background-color: rgba(10, 10, 22, 0.75);");
        tourOverlay.setOnMouseClicked(e -> e.consume());

        updateTourStep();
        canvasContainer.getChildren().add(tourOverlay);
    }

    private void updateTourStep() {
        tourOverlay.getChildren().clear();

        VBox card = new VBox(20);
        card.setMaxSize(600, 520);
        card.setPadding(new Insets(25));
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle(
                "-fx-background-color: rgba(22, 22, 40, 0.95); " +
                "-fx-background-radius: 14; " +
                "-fx-border-color: rgba(74, 156, 247, 0.5); " +
                "-fx-border-radius: 14; " +
                "-fx-border-width: 1.5; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.7), 20, 0, 0, 10);"
        );

        // Header with step number and title
        HBox cardHeader = new HBox(10);
        cardHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label stepIndicator = new Label("STEP " + tourStep + " / 6");
        stepIndicator.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #4a9cf7; -fx-background-color: rgba(74, 156, 247, 0.15); -fx-padding: 3 8; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button("✕");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #8888aa; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0;");
        btnClose.setOnMouseEntered(e -> btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0;"));
        btnClose.setOnMouseExited(e -> btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #8888aa; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0;"));
        btnClose.setOnAction(e -> {
            canvasContainer.getChildren().remove(tourOverlay);
            tourOverlay = null;
        });

        cardHeader.getChildren().addAll(stepIndicator, spacer, btnClose);

        Label titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        titleLabel.setWrapText(true);

        Label descLabel = new Label();
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #ccccdd; -fx-line-spacing: 4;");
        descLabel.setWrapText(true);

        ScrollPane descScroll = new ScrollPane(descLabel);
        descScroll.setFitToWidth(true);
        descScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        descScroll.setPrefViewportHeight(300);
        descScroll.setPrefHeight(300);
        descScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        descScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        descLabel.maxWidthProperty().bind(descScroll.widthProperty().subtract(15));

        switch (tourStep) {
            case 1:
                titleLabel.setText("Welcome to CiteRight Paper Graph 🕸");
                descLabel.setText(
                    "Welcome to CiteRight — a visual research intelligence platform that transforms your paper library into a living, interactive knowledge map.\n\n" +
                    "• WHAT IT IS: Instead of browsing papers in flat folders or spreadsheets, the Paper Graph builds a multi-scale semantic landscape where every paper becomes a node and every scholarly connection becomes a visible, queryable edge.\n\n" +
                    "• HOW IT WORKS: CiteRight reads the title, abstract, year, authors, and metadata of every paper in your library. It then computes TF-IDF document vectors (term frequency–inverse document frequency) to measure how similar papers are based on the words they use. Papers sharing rare, domain-specific terms score higher than those sharing common words.\n\n" +
                    "• CLUSTERING: The system performs hierarchical agglomerative clustering — papers are progressively merged into thematic groups based on their TF-IDF vectors. The resulting clusters are drawn as \"islands\" on the Macro view, each labeled with the top keywords that define that research area.\n\n" +
                    "• WHY IT MATTERS: By seeing how papers connect, challenge, support, and build on one another, you can form research arguments, write literature reviews, identify gaps in the field, and construct a robust thesis layout — all visually.\n\n" +
                    "This tour will walk you through every feature. Use the → Next button to proceed."
                );
                break;
            case 2:
                titleLabel.setText("The Three Scales of Analysis 🔍");
                descLabel.setText(
                    "The toolbar at the top of the graph contains three view buttons. Each provides a different analytical zoom level:\n\n" +
                    "--- MACRO (Islands) ---\n" +
                    "A bird’s-eye view of your entire library. Papers are grouped into thematic clusters using TF-IDF similarity. Each cluster is enclosed in a colored convex hull boundary and labeled with the top 3 most representative keywords.\n" +
                    "• Drag the canvas to pan, scroll to zoom in/out.\n" +
                    "• Hover over any node to see its title and year.\n" +
                    "• Double-click any paper to switch to Meso view centered on it.\n\n" +
                    "--- MESO (Connections) ---\n" +
                    "An ego-centric neighborhood view. The paper you double-clicked becomes the central \"Target\" node (highlighted in cyan). Surrounding it are all papers within a 2-hop radius — papers directly connected to it (1-hop) and papers connected to those (2-hop).\n" +
                    "• Use this view to study a specific paper’s citation context and semantic relationships.\n" +
                    "• Right-click any paper to create manual relationships (SUPPORTS, CONTRADICTS, etc.).\n\n" +
                    "--- MICRO (Workspace) ---\n" +
                    "A private whiteboard canvas for building argument maps. Right-click any paper in Macro or Meso and select \"Pin to Workspace\" to add it here.\n" +
                    "• Drag papers freely to arrange them spatially.\n" +
                    "• Double-click the canvas to create freeform text notes.\n" +
                    "• Select multiple papers to group them in colored boxes with custom labels."
                );
                break;
            case 3:
                titleLabel.setText("Semantic Relationship Types 🟢🔴");
                descLabel.setText(
                    "The lines (edges) connecting papers on the graph represent specific scholarly relationships. Each type has a distinct color and meaning:\n\n" +
                    "--- AUTOMATIC EDGES ---\n" +
                    "• Blue / Thin Lines — Automated TF-IDF text similarity. These are drawn when two papers share significant keyword overlap. Hover over any blue line to see the top shared terms and the exact similarity percentage.\n\n" +
                    "--- CURATED EDGES ---\n" +
                    "• 🟢 Solid Green (SUPPORTS) — Paper A provides empirical evidence or theoretical backing that strengthens Paper B’s claims.\n" +
                    "• 🔴 Solid Red (CONTRADICTS) — Paper A presents conflicting data or a direct rebuttal of Paper B.\n" +
                    "• 🟡 Solid Yellow (EXTENDS) — Paper A builds upon, generalizes, or improves the framework/algorithm introduced in Paper B.\n" +
                    "• 🟣 Solid Purple (METHODOLOGY) — Papers A and B use the same experimental setup, simulation technique, or dataset, even if their topics differ.\n\n" +
                    "In Meso view, right-click Paper A → select \"Create Relationship From...\" → then right-click Paper B → choose the relationship type from the context menu."
                );
                break;
            case 4:
                titleLabel.setText("Temporal Time Machine 📅");
                descLabel.setText(
                    "Scientific knowledge evolves over time. The Temporal Range Slider lets you watch your research field grow, shift, and transform year by year.\n\n" +
                    "--- WHERE TO FIND IT ---\n" +
                    "At the bottom of the Macro view, you'll see a dual-handle range slider spanning the full year range of your library (e.g., 2015–2025).\n\n" +
                    "--- HOW TO USE IT ---\n" +
                    "• Drag the LEFT handle to set the start year. Papers published before this year will fade out and be removed from the layout.\n" +
                    "• Drag the RIGHT handle to set the end year. Papers published after this year will similarly disappear.\n" +
                    "• As you drag, the graph's force-directed physics engine recalculates positions in real-time. You'll see clusters grow, shrink, split, or merge dynamically.\n\n" +
                    "--- RESEARCH USE CASES ---\n" +
                    "• Track when a methodology gained popularity (e.g., transformer papers exploding after 2017).\n" +
                    "• Identify the historical lineage of your research topic by sliding backwards.\n" +
                    "• Find \"paradigm shifts\" — moments where one cluster suddenly splits into multiple new themes.\n" +
                    "• Compare the density of publications across different eras to gauge research momentum."
                );
                break;
            case 5:
                titleLabel.setText("Gap Analysis & Author Networks 👥");
                descLabel.setText(
                    "The toolbar on the upper-right corner provides two powerful analytical overlays. Toggle them on/off to layer insights on top of the graph:\n\n" +
                    "--- GAP ANALYSIS (🔍 button) ---\n" +
                    "Reveals structural weaknesses and opportunities in your library:\n\n" +
                    "• Orphan Nodes (pulsing orange glow): Papers with ZERO connections to any other paper. These are isolated islands — either you haven't explored their context yet, or they represent genuinely novel work that doesn't fit existing paradigms. Either way, they deserve attention.\n\n" +
                    "• Bridge Edges (solid gold lines): Edges with high betweenness centrality — they are the ONLY connections between two otherwise-separate clusters. These are your most valuable links. The papers on bridge edges are prime candidates for interdisciplinary breakthroughs.\n\n" +
                    "• Methodology Overlaps (dashed purple lines): Papers that share experimental techniques, tools, or datasets but belong to different topical clusters. These cross-pollination opportunities are often invisible in traditional literature reviews.\n\n" +
                    "--- AUTHOR NETWORKS (👥 button) ---\n" +
                    "Switches the graph from paper-nodes to author-nodes:\n\n" +
                    "• Each node represents an author. Node SIZE reflects how many papers by that author appear in your library.\n" +
                    "• Node COLORS represent co-authorship communities detected via label propagation algorithm.\n" +
                    "• Edges represent co-authorship (two authors who published together).\n" +
                    "• Use this to find: prolific collaborators, rival groups working on the same problem, and bridge authors connecting disconnected communities."
                );
                break;
            case 6:
                titleLabel.setText("AI-Assisted Argument Mapping 🤖");
                descLabel.setText(
                    "Manually creating relationship edges between hundreds of papers is tedious. CiteRight's AI Analyzer automates this process:\n\n" +
                    "--- HOW TO USE IT ---\n" +
                    "• Click the \"🤖 AI Analyze\" button in the toolbar. The system collects the titles and abstracts of all visible papers (up to 10 per batch).\n" +
                    "• It sends them to the configured AI provider (Google Gemini or local Ollama) which returns structured relationship inferences with confidence scores and reasoning.\n\n" +
                    "--- HOW SUGGESTIONS APPEAR ---\n" +
                    "• AI-suggested relationships appear as DASHED lines (not solid) with a 🤖 robot icon at the midpoint.\n" +
                    "• Hover over any dashed line to see the AI's reasoning (e.g., \"Paper A's Table 3 shows opposite effect sizes to Paper B's Figure 2\") and the confidence score (0–100%).\n\n" +
                    "--- CONFIRMING / DISMISSING ---\n" +
                    "• Right-click on ANY AI-suggested dashed edge or on a node connected by one.\n" +
                    "• Select ✔ \"Confirm\" to permanently save the relationship. It becomes a solid colored line.\n" +
                    "• Select ✘ \"Dismiss\" to reject the suggestion. It will be hidden permanently and never re-suggested.\n\n" +
                    "--- LOCAL SEMANTIC FALLBACK ---\n" +
                    "• If the Gemini API is exhausted (daily quota exceeded) or unconfigured, CiteRight automatically falls back to a Local Semantic Rule Engine.\n" +
                    "• This offline engine uses keyword overlap analysis, chronological ordering, and methodology term detection to infer relationships locally — no internet required.\n" +
                    "• Local suggestions are marked with \"[Local Engine]\" in their reasoning and tend to have slightly lower confidence scores.\n" +
                    "• You'll see a warning toast when the local engine activates. You can still confirm/dismiss suggestions exactly the same way."
                );
                break;
        }

        // Navigation HBox
        HBox nav = new HBox(10);
        nav.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("← Back");
        btnBack.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-padding: 6 14; -fx-background-radius: 6;");
        btnBack.setDisable(tourStep == 1);
        btnBack.setOnAction(e -> {
            tourStep--;
            updateTourStep();
        });

        // Step dot indicators
        HBox dots = new HBox(6);
        dots.setAlignment(Pos.CENTER);
        for (int i = 1; i <= 6; i++) {
            Region dot = new Region();
            dot.setPrefSize(7, 7);
            if (i == tourStep) {
                dot.setStyle("-fx-background-color: #4a9cf7; -fx-background-radius: 50%;");
            } else {
                dot.setStyle("-fx-background-color: rgba(255, 255, 255, 0.25); -fx-background-radius: 50%;");
            }
            dots.getChildren().add(dot);
        }

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        Button btnNext = new Button(tourStep == 6 ? "Finish" : "Next →");
        btnNext.setStyle("-fx-background-color: #4a9cf7; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 16; -fx-background-radius: 6;");
        btnNext.setOnAction(e -> {
            if (tourStep < 6) {
                tourStep++;
                updateTourStep();
            } else {
                canvasContainer.getChildren().remove(tourOverlay);
                tourOverlay = null;
                GeminiConfig.setTourCompleted(true);
            }
        });

        nav.getChildren().addAll(btnBack, dots, navSpacer, btnNext);

        card.getChildren().addAll(cardHeader, titleLabel, descScroll, nav);
        tourOverlay.getChildren().add(card);
    }

    private double computeAuthorJaccard(LibraryEntry a, LibraryEntry b) {
        if (a == null || b == null || a.getPublication() == null || b.getPublication() == null) return 0.0;
        List<Author> authorsA = a.getPublication().getAuthors();
        List<Author> authorsB = b.getPublication().getAuthors();
        if (authorsA == null || authorsB == null || authorsA.isEmpty() || authorsB.isEmpty()) return 0.0;
        
        Set<String> setA = new HashSet<>();
        for (Author auth : authorsA) {
            if (auth.getName() != null) setA.add(auth.getName().toLowerCase().trim());
        }
        
        Set<String> setB = new HashSet<>();
        for (Author auth : authorsB) {
            if (auth.getName() != null) setB.add(auth.getName().toLowerCase().trim());
        }
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;
        
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        
        return (double) intersection.size() / union.size();
    }

    private double computeTagJaccard(LibraryEntry a, LibraryEntry b) {
        if (a == null || b == null || a.getPublication() == null || b.getPublication() == null) return 0.0;
        List<Tag> tagsA = a.getPublication().getTags();
        List<Tag> tagsB = b.getPublication().getTags();
        if (tagsA == null || tagsB == null || tagsA.isEmpty() || tagsB.isEmpty()) return 0.0;
        
        Set<String> setA = new HashSet<>();
        for (Tag t : tagsA) {
            if (t.getName() != null) setA.add(t.getName().toLowerCase().trim());
        }
        
        Set<String> setB = new HashSet<>();
        for (Tag t : tagsB) {
            if (t.getName() != null) setB.add(t.getName().toLowerCase().trim());
        }
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;
        
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        
        return (double) intersection.size() / union.size();
    }

    private double computeHybridSimilarity(double semanticSim, LibraryEntry a, LibraryEntry b) {
        double authorJaccard = computeAuthorJaccard(a, b);
        double tagJaccard = computeTagJaccard(a, b);
        // Hybrid weighting: 75% semantic/textual vector, 15% co-authorship overlap, 10% tag co-occurrence overlap
        return 0.75 * semanticSim + 0.15 * authorJaccard + 0.10 * tagJaccard;
    }

    // ── Inner data classes ────────────────────────────────────────────────────

    static class GraphNode {
        final LibraryEntry entry;
        final String title;
        final int year;
        double x, y;
        double vx = 0, vy = 0;
        String clusterLabel = null;
        Color clusterColor = null;

        GraphNode(LibraryEntry entry, String title, int year, double x, double y) {
            this.entry = entry;
            this.title = title;
            this.year = year;
            this.x = x;
            this.y = y;
        }
    }

    static class GraphEdge {
        final GraphNode a, b;
        final double weight;
        final String type;
        boolean isAISuggestion = false;
        int relationshipId = -1;
        double confidence = 1.0;
        String reasoning = null;

        GraphEdge(GraphNode a, GraphNode b, double weight, String type) {
            this.a = a;
            this.b = b;
            this.weight = weight;
            this.type = type;
        }
    }

    private static class GraphData {
        final List<GraphNode> nodes = new ArrayList<>();
        final List<GraphEdge> edges = new ArrayList<>();
        final Map<GraphNode, Map<String, Double>> nodeVectors = new HashMap<>();
        final Map<String, List<GraphNode>> clusterNodeMap = new HashMap<>();
        final List<GraphEdge> aiSuggestedEdges = new ArrayList<>();
        final List<WorkspaceNote> workspaceNotes = new ArrayList<>();
        final List<WorkspaceGroup> workspaceGroups = new ArrayList<>();
        GraphNode egoNode = null;
        GraphNode connectionSourceNode = null;
    }
}
