package com.citeright.ui;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.nlp.TfIdfEngine;
import com.citeright.service.LibraryService;
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

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Interactive 2D graph visualization showing how papers in the user's library
 * are connected through semantic similarity. Like "Connected Papers" — but
 * built-in, offline, and free.
 *
 * Uses TF-IDF cosine similarity between paper abstracts to determine edge
 * strength, then applies a simple force-directed layout for positioning.
 */
public class PaperGraphPane extends StackPane {

    private static final double SIMILARITY_THRESHOLD = 0.06;
    private static final double NODE_RADIUS = 22;
    private static final double REPULSION = 8000;
    private static final double ATTRACTION = 0.006;
    private static final double DAMPING = 0.85;
    private static final int LAYOUT_ITERATIONS = 180;
    private static final double EDGE_HIT_DISTANCE = 8;

    private final LibraryService libraryService;
    private Consumer<LibraryEntry> onSelectEntry;

    private Canvas canvas;
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

    // Tooltip panel (for nodes)
    private VBox tooltipPanel;
    private Label tooltipTitle, tooltipAuthors, tooltipYear, tooltipVenue, tooltipAbstract, tooltipConnections;

    // Edge tooltip panel
    private VBox edgeTooltipPanel;
    private Label edgeTooltipTitle, edgeTooltipScore, edgeTooltipTerms;

    public PaperGraphPane(LibraryService libraryService) {
        this.libraryService = libraryService;
        buildUI();
    }

    public void setOnSelectEntry(Consumer<LibraryEntry> handler) { this.onSelectEntry = handler; }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI() {
        setStyle("-fx-background-color: #0d0d1a;");

        canvas = new Canvas();
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        // Interaction
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMouseClicked(this::onMouseClicked);
        canvas.setOnScroll(this::onScroll);

        // Tooltip panel (floating card, hidden by default)
        tooltipPanel = new VBox(6);
        tooltipPanel.setVisible(false);
        tooltipPanel.setMouseTransparent(true);
        tooltipPanel.setPrefWidth(310);
        tooltipPanel.setMaxWidth(320);
        tooltipPanel.setStyle(
                "-fx-background-color: rgba(20,20,40,0.95); " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 14 18; " +
                "-fx-border-color: rgba(74,108,247,0.4); -fx-border-radius: 12; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(74,108,247,0.3), 20, 0.1, 0, 4);");
        StackPane.setAlignment(tooltipPanel, Pos.TOP_LEFT);

        tooltipTitle = new Label();
        tooltipTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        tooltipTitle.setWrapText(true);

        tooltipAuthors = new Label();
        tooltipAuthors.setStyle("-fx-font-size: 11px; -fx-text-fill: #4a9cf7;");

        tooltipYear = new Label();
        tooltipYear.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaaacc;");

        tooltipVenue = new Label();
        tooltipVenue.setStyle("-fx-font-size: 10px; -fx-text-fill: #88aadd; -fx-font-style: italic;");

        tooltipAbstract = new Label();
        tooltipAbstract.setStyle("-fx-font-size: 10px; -fx-text-fill: #bbbbdd; -fx-line-spacing: 2;");
        tooltipAbstract.setWrapText(true);
        tooltipAbstract.setMaxHeight(90);

        tooltipConnections = new Label();
        tooltipConnections.setStyle("-fx-font-size: 10px; -fx-text-fill: #ff6b9d; -fx-font-weight: bold;");

        tooltipPanel.getChildren().addAll(tooltipTitle, tooltipAuthors, tooltipYear, tooltipVenue, tooltipAbstract, tooltipConnections);

        // Edge tooltip panel
        edgeTooltipPanel = new VBox(6);
        edgeTooltipPanel.setVisible(false);
        edgeTooltipPanel.setMouseTransparent(true);
        edgeTooltipPanel.setPrefWidth(330);
        edgeTooltipPanel.setMaxWidth(340);
        edgeTooltipPanel.setStyle(
                "-fx-background-color: rgba(20,20,40,0.95); " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 14 18; " +
                "-fx-border-color: rgba(255,107,157,0.4); -fx-border-radius: 12; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(255,107,157,0.25), 20, 0.1, 0, 4);");
        StackPane.setAlignment(edgeTooltipPanel, Pos.TOP_LEFT);

        edgeTooltipTitle = new Label();
        edgeTooltipTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        edgeTooltipTitle.setWrapText(true);

        edgeTooltipScore = new Label();
        edgeTooltipScore.setStyle("-fx-font-size: 11px; -fx-text-fill: #ff6b9d; -fx-font-weight: bold;");

        edgeTooltipTerms = new Label();
        edgeTooltipTerms.setStyle("-fx-font-size: 10px; -fx-text-fill: #bbbbdd; -fx-line-spacing: 2;");
        edgeTooltipTerms.setWrapText(true);
        edgeTooltipTerms.setMaxHeight(100);

        edgeTooltipPanel.getChildren().addAll(edgeTooltipTitle, edgeTooltipScore, edgeTooltipTerms);

        // Header bar
        HBox header = buildHeader();
        StackPane.setAlignment(header, Pos.TOP_LEFT);

        // Legend
        VBox legend = buildLegend();
        StackPane.setAlignment(legend, Pos.BOTTOM_RIGHT);

        getChildren().addAll(canvas, header, tooltipPanel, edgeTooltipPanel, legend);

        // Redraw on resize
        widthProperty().addListener((o, a, b) -> draw());
        heightProperty().addListener((o, a, b) -> draw());
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
        zoomIn.setOnAction(e -> { zoom = Math.min(3.0, zoom * 1.2); draw(); });

        Button zoomOut = styledBtn("－");
        zoomOut.setOnAction(e -> { zoom = Math.max(0.3, zoom / 1.2); draw(); });

        Button reset = styledBtn("Reset");
        reset.setOnAction(e -> { zoom = 1.0; offsetX = 0; offsetY = 0; draw(); });

        header.getChildren().addAll(icon, title, subtitle, spacer, zoomOut, zoomIn, reset);
        return header;
    }

    private VBox buildLegend() {
        VBox legend = new VBox(5);
        legend.setPadding(new Insets(12));
        legend.setMaxWidth(180);
        legend.setMaxHeight(100);
        legend.setStyle(
                "-fx-background-color: rgba(30, 30, 50, 0.8); " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 10 14;");
        StackPane.setMargin(legend, new Insets(0, 16, 16, 0));

        Label legendTitle = new Label("LEGEND");
        legendTitle.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 9px; -fx-font-weight: bold;");

        Label l1 = new Label("● Node = Paper in library");
        l1.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10px;");

        Label l2 = new Label("— Line = Semantic similarity");
        l2.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10px;");

        Label l3 = new Label("Thicker line = Stronger connection");
        l3.setStyle("-fx-text-fill: #9999bb; -fx-font-size: 9px;");

        Label l4 = new Label("Scroll to zoom · Drag to pan");
        l4.setStyle("-fx-text-fill: #6666aa; -fx-font-size: 9px;");

        legend.getChildren().addAll(legendTitle, l1, l2, l3, l4);
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Graph Building
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads all library papers, computes pairwise semantic similarity,
     * then runs a force-directed layout. Call once when the pane is shown.
     */
    public void buildGraph() {
        nodes.clear();
        edges.clear();
        nodeVectors.clear();

        new Thread(() -> {
            try {
                List<LibraryEntry> entries = libraryService.getAllActive();
                if (entries.isEmpty()) {
                    Platform.runLater(this::draw);
                    return;
                }

                // Build document texts
                List<String> documents = new ArrayList<>();
                for (LibraryEntry entry : entries) {
                    documents.add(buildDocText(entry));
                }

                // Train TF-IDF model
                TfIdfEngine engine = new TfIdfEngine();
                engine.buildModel(documents);

                // Compute vectors
                List<Map<String, Double>> vectors = new ArrayList<>();
                for (String doc : documents) {
                    vectors.add(engine.computeTfIdfVector(doc));
                }

                // Create nodes
                Random rng = new Random(42);
                for (int i = 0; i < entries.size(); i++) {
                    Publication pub = entries.get(i).getPublication();
                    String title = pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled";
                    int year = pub != null ? pub.getYear() : 0;
                    GraphNode node = new GraphNode(entries.get(i), title, year,
                            400 + rng.nextDouble() * 400 - 200,
                            300 + rng.nextDouble() * 300 - 150);
                    nodes.add(node);
                    nodeVectors.put(node, vectors.get(i));
                }

                // Create edges (pairwise similarity)
                for (int i = 0; i < nodes.size(); i++) {
                    for (int j = i + 1; j < nodes.size(); j++) {
                        double sim = TfIdfEngine.cosineSimilarity(vectors.get(i), vectors.get(j));
                        if (sim >= SIMILARITY_THRESHOLD) {
                            edges.add(new GraphEdge(nodes.get(i), nodes.get(j), sim));
                        }
                    }
                }

                // Force-directed layout
                runForceLayout();

                // Center the graph
                centerGraph();

                Platform.runLater(this::draw);
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Force-Directed Layout
    // ─────────────────────────────────────────────────────────────────────────

    private void runForceLayout() {
        for (int iter = 0; iter < LAYOUT_ITERATIONS; iter++) {
            // Repulsion between all nodes
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode a = nodes.get(i);
                for (int j = i + 1; j < nodes.size(); j++) {
                    GraphNode b = nodes.get(j);
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
            for (GraphEdge edge : edges) {
                double dx = edge.b.x - edge.a.x;
                double dy = edge.b.y - edge.a.y;
                double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                double force = ATTRACTION * dist * edge.weight;
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;
                edge.a.vx += fx; edge.a.vy += fy;
                edge.b.vx -= fx; edge.b.vy -= fy;
            }

            // Apply velocities with damping
            for (GraphNode node : nodes) {
                node.x += node.vx;
                node.y += node.vy;
                node.vx *= DAMPING;
                node.vy *= DAMPING;
            }
        }
    }

    private void centerGraph() {
        if (nodes.isEmpty()) return;
        double cx = nodes.stream().mapToDouble(n -> n.x).average().orElse(0);
        double cy = nodes.stream().mapToDouble(n -> n.y).average().orElse(0);
        for (GraphNode n : nodes) {
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

        // Background - dark gradient feel
        gc.setFill(Color.web("#0a0a18"));
        gc.fillRect(0, 0, w, h);

        // Draw subtle dot grid instead of lines
        gc.setFill(Color.web("#1a1a35"));
        double gridSize = 40 * zoom;
        double startX = (offsetX % gridSize) + w / 2 % gridSize;
        double startY = (offsetY % gridSize) + h / 2 % gridSize;
        for (double x = startX; x < w; x += gridSize) {
            for (double y = startY; y < h; y += gridSize) {
                gc.fillOval(x - 1, y - 1, 2, 2);
            }
        }

        if (nodes.isEmpty()) {
            gc.setFill(Color.web("#5a5a8a"));
            gc.setFont(Font.font("System", 14));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("No papers in your library yet.\nAdd some papers to see the graph!", w / 2, h / 2);
            return;
        }

        double cx = w / 2 + offsetX;
        double cy = h / 2 + offsetY;

        // Determine which nodes are connected to hovered node/edge
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
            double ax = cx + edge.a.x * zoom;
            double ay = cy + edge.a.y * zoom;
            double bx = cx + edge.b.x * zoom;
            double by = cy + edge.b.y * zoom;

            boolean isHoveredEdge = edge == hoveredEdge;
            boolean isConnectedToHover = hoveredNode != null &&
                    (edge.a == hoveredNode || edge.b == hoveredNode);
            boolean dimmed = hasHighlight && !isHoveredEdge && !isConnectedToHover;

            double alpha = Math.min(1.0, edge.weight * 3);
            double lineWidth = 0.5 + edge.weight * 5;

            if (dimmed) {
                // Dim unrelated edges
                gc.setStroke(Color.color(0.2, 0.25, 0.5, 0.12));
                gc.setLineWidth(lineWidth * 0.5);
                gc.strokeLine(ax, ay, bx, by);
            } else {
                // Glow effect for hovered or strong connections
                if (isHoveredEdge) {
                    gc.setStroke(Color.color(1.0, 0.42, 0.62, 0.5));
                    gc.setLineWidth(lineWidth + 8);
                    gc.strokeLine(ax, ay, bx, by);
                    gc.setStroke(Color.color(1.0, 0.42, 0.62, 0.9));
                    gc.setLineWidth(lineWidth + 2);
                    gc.strokeLine(ax, ay, bx, by);
                } else if (isConnectedToHover) {
                    gc.setStroke(Color.color(0.4, 0.6, 1.0, 0.4));
                    gc.setLineWidth(lineWidth + 5);
                    gc.strokeLine(ax, ay, bx, by);
                    gc.setStroke(Color.color(0.4, 0.6, 1.0, alpha * 0.9));
                    gc.setLineWidth(lineWidth + 1);
                    gc.strokeLine(ax, ay, bx, by);
                } else if (edge.weight > 0.15) {
                    gc.setStroke(Color.color(0.4, 0.5, 1.0, alpha * 0.3));
                    gc.setLineWidth(lineWidth + 4);
                    gc.strokeLine(ax, ay, bx, by);
                    gc.setStroke(Color.color(0.35, 0.45, 0.95, alpha * 0.6));
                    gc.setLineWidth(lineWidth);
                    gc.strokeLine(ax, ay, bx, by);
                } else {
                    gc.setStroke(Color.color(0.35, 0.45, 0.95, alpha * 0.6));
                    gc.setLineWidth(lineWidth);
                    gc.strokeLine(ax, ay, bx, by);
                }

                // Similarity % label on hovered edge
                if (isHoveredEdge) {
                    double midX = (ax + bx) / 2;
                    double midY = (ay + by) / 2;
                    String pct = Math.round(edge.weight * 100) + "% similar";
                    gc.setFill(Color.web("#ff6b9d"));
                    gc.setFont(Font.font("System", 11 * Math.min(zoom, 1.3)));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(pct, midX, midY - 8);
                }
            }
        }

        // Draw nodes
        for (GraphNode node : nodes) {
            double nx = cx + node.x * zoom;
            double ny = cy + node.y * zoom;
            double r = NODE_RADIUS * zoom;

            boolean isHovered = node == hoveredNode;
            boolean isSelected = node == selectedNode;
            boolean dimmed = hasHighlight && !highlightedNodes.contains(node);

            // Node color based on connection count
            int connectionCount = (int) edges.stream()
                    .filter(e -> e.a == node || e.b == node).count();
            Color nodeColor;
            if (connectionCount >= 5) {
                nodeColor = Color.web("#ff6b9d"); // Hub — pink
            } else if (connectionCount >= 3) {
                nodeColor = Color.web("#4a9cf7"); // Well-connected — blue
            } else if (connectionCount >= 1) {
                nodeColor = Color.web("#6c5ce7"); // Some connections — purple
            } else {
                nodeColor = Color.web("#636e72"); // Isolated — gray
            }

            if (dimmed) {
                nodeColor = nodeColor.deriveColor(0, 0.3, 0.4, 0.4);
            } else if (isHovered) {
                nodeColor = nodeColor.brighter().brighter();
            }

            // Outer glow for hovered/selected
            if (isHovered || isSelected) {
                gc.setFill(nodeColor.deriveColor(0, 1, 1, 0.15));
                gc.fillOval(nx - r * 2.2, ny - r * 2.2, r * 4.4, r * 4.4);
                gc.setFill(nodeColor.deriveColor(0, 1, 1, 0.25));
                gc.fillOval(nx - r * 1.6, ny - r * 1.6, r * 3.2, r * 3.2);
            }

            // Main circle
            gc.setFill(nodeColor);
            gc.fillOval(nx - r, ny - r, r * 2, r * 2);

            // Inner highlight (top-left light)
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

            // Title label below node
            if (zoom > 0.5 && !dimmed) {
                String label = truncate(node.title, 28);
                gc.setFill(Color.color(0.85, 0.85, 0.95, isHovered ? 1.0 : 0.85));
                gc.setFont(Font.font("System", 10.5 * Math.min(zoom, 1.2)));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(label, nx, ny + r + 16 * zoom);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Interaction
    // ─────────────────────────────────────────────────────────────────────────

    private void onMousePressed(MouseEvent e) {
        dragStartX = e.getX();
        dragStartY = e.getY();
        panStartX = offsetX;
        panStartY = offsetY;
    }

    private void onMouseDragged(MouseEvent e) {
        offsetX = panStartX + (e.getX() - dragStartX);
        offsetY = panStartY + (e.getY() - dragStartY);
        draw();
    }

    private void onMouseMoved(MouseEvent e) {
        GraphNode foundNode = findNodeAt(e.getX(), e.getY());
        GraphEdge foundEdge = foundNode == null ? findEdgeAt(e.getX(), e.getY()) : null;

        boolean changed = foundNode != hoveredNode || foundEdge != hoveredEdge;
        hoveredNode = foundNode;
        hoveredEdge = foundEdge;

        if (changed) {
            // Hide both tooltips first
            tooltipPanel.setVisible(false);
            edgeTooltipPanel.setVisible(false);

            if (foundNode != null) {
                showNodeTooltip(foundNode, e.getX(), e.getY());
            } else if (foundEdge != null) {
                showEdgeTooltip(foundEdge, e.getX(), e.getY());
            }
            draw();
        }
    }

    private void onMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            GraphNode found = findNodeAt(e.getX(), e.getY());
            if (found != null) {
                selectedNode = found;
                if (onSelectEntry != null) onSelectEntry.accept(found.entry);
                draw();
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
            double r = NODE_RADIUS * zoom + 4;
            if (Math.hypot(mx - nx, my - ny) <= r) {
                return node;
            }
        }
        return null;
    }

    /** Finds the nearest edge to the mouse position using point-to-segment distance. */
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

            double dist = pointToSegmentDistance(mx, my, ax, ay, bx, by);
            if (dist < EDGE_HIT_DISTANCE * zoom && dist < closestDist) {
                closestDist = dist;
                closest = edge;
            }
        }
        return closest;
    }

    /** Computes the distance from point (px,py) to line segment (ax,ay)-(bx,by). */
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
        Publication pub = node.entry.getPublication();
        tooltipTitle.setText("📄 " + node.title);
        tooltipAuthors.setText("👤 " + (pub != null ? pub.getAuthorsFormatted() : "Unknown"));
        tooltipYear.setText(node.year > 0 ? "📅 Year: " + node.year : "");
        tooltipVenue.setText(pub != null && pub.getVenue() != null && !pub.getVenue().isEmpty()
                ? "📰 " + pub.getVenue() : "");

        String abs = pub != null && pub.getAbstractText() != null ? pub.getAbstractText() : "";
        tooltipAbstract.setText(abs.length() > 250 ? abs.substring(0, 250) + "…" : (abs.isEmpty() ? "No abstract available." : abs));

        int connCount = (int) edges.stream().filter(e -> e.a == node || e.b == node).count();
        tooltipConnections.setText("🔗 " + connCount + " connection" + (connCount != 1 ? "s" : "") + " in graph");

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
        edgeTooltipTitle.setText("🔗 " + titleA + "\n↔ " + titleB);

        int pct = (int) Math.round(edge.weight * 100);
        String strength;
        if (pct >= 30) strength = "Strong";
        else if (pct >= 15) strength = "Moderate";
        else strength = "Weak";
        edgeTooltipScore.setText("📊 Similarity: " + pct + "% (" + strength + ")");

        // Extract shared keywords from TF-IDF vectors
        String sharedTerms = getSharedTerms(edge.a, edge.b);
        edgeTooltipTerms.setText("🔑 Shared keywords: " + sharedTerms);

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

    /** Extracts the top shared keywords between two papers using stored TF-IDF vectors. */
    private String getSharedTerms(GraphNode a, GraphNode b) {
        Map<String, Double> vecA = nodeVectors.get(a);
        Map<String, Double> vecB = nodeVectors.get(b);
        if (vecA == null || vecB == null) return "N/A";

        // Find terms present in both vectors, ranked by combined weight
        List<Map.Entry<String, Double>> shared = new ArrayList<>();
        for (Map.Entry<String, Double> entry : vecA.entrySet()) {
            if (vecB.containsKey(entry.getKey())) {
                double combinedScore = entry.getValue() + vecB.get(entry.getKey());
                shared.add(Map.entry(entry.getKey(), combinedScore));
            }
        }

        if (shared.isEmpty()) return "No shared terms found";

        // Sort by combined weight descending and take top 8
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

    // ── Inner data classes ────────────────────────────────────────────────────

    private static class GraphNode {
        final LibraryEntry entry;
        final String title;
        final int year;
        double x, y;
        double vx = 0, vy = 0;

        GraphNode(LibraryEntry entry, String title, int year, double x, double y) {
            this.entry = entry;
            this.title = title;
            this.year = year;
            this.x = x;
            this.y = y;
        }
    }

    private static class GraphEdge {
        final GraphNode a, b;
        final double weight;

        GraphEdge(GraphNode a, GraphNode b, double weight) {
            this.a = a;
            this.b = b;
            this.weight = weight;
        }
    }
}
