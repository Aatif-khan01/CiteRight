package com.citeright.ui;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.nlp.TextRankSummarizer;
import com.citeright.service.CitationStyleManager;
import com.citeright.service.LibraryService;
import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import com.citeright.ui.PaperGraphPane.GraphNode;
import com.citeright.ui.PaperGraphPane.GraphEdge;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.awt.Desktop;
import java.net.URI;
import java.util.*;

import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;

public class PaperGraphInspector extends VBox {

    private static final double PREF_WIDTH = 320.0;
    private static final double LABEL_MAX_WIDTH = PREF_WIDTH - 28 - 12; // minus padding minus scrollbar
    private final LibraryService libraryService;
    private java.util.function.Consumer<GraphNode> onNavigateToNode;
    private Runnable onAddRelationship;
    private GraphNode currentNode;
    private final VBox content = new VBox(10);
    private List<GraphNode> allNodes;

    public PaperGraphInspector(LibraryService libraryService) {
        this.libraryService = libraryService;

        setPrefWidth(PREF_WIDTH);
        setMinWidth(PREF_WIDTH);
        setMaxWidth(PREF_WIDTH);
        setStyle("-fx-background-color: #151522; -fx-border-color: #2a2a3e; -fx-border-width: 0 0 0 1;");

        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color: transparent;");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #151522;");
        scroll.getStylesheets().add("data:text/css," +
                ".scroll-bar:vertical { -fx-background-color: #151522; -fx-pref-width: 8px; }" +
                ".scroll-bar:vertical .thumb { -fx-background-color: #4a9cf7; -fx-background-radius: 4px; }" +
                ".scroll-pane > .viewport { -fx-background-color: transparent; }"
        );
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().add(scroll);
        showEmpty();
    }

    public void setOnNavigateToNode(java.util.function.Consumer<GraphNode> handler) {
        this.onNavigateToNode = handler;
    }

    public void setOnAddRelationship(Runnable r) {
        this.onAddRelationship = r;
    }

    public void showEmpty() {
        content.getChildren().clear();
        Label icon = new Label("📊");
        icon.setStyle("-fx-font-size: 32px;");
        Label hint = new Label("Click a node to inspect it");
        hint.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 11px;");
        hint.setWrapText(true);
        VBox empty = new VBox(8, icon, hint);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(40, 16, 16, 16));
        content.getChildren().add(empty);
    }

    public void show(GraphNode node, List<GraphEdge> allEdges, List<GraphNode> allNodes) {
        this.currentNode = node;
        this.allNodes = allNodes;
        content.getChildren().clear();

        LibraryEntry entry = node.entry;
        if (entry == null || entry.getPublication() == null) { showEmpty(); return; }
        Publication pub = entry.getPublication();

        List<GraphEdge> incidentEdges = new ArrayList<>();
        for (GraphEdge e : allEdges) {
            if (e.a == node || e.b == node) {
                incidentEdges.add(e);
            }
        }

        content.getChildren().addAll(
                buildAtAGlance(node, incidentEdges),
                buildDivider(),
                buildHeader(pub),
                buildImpactScore(node),
                buildDivider(),
                buildWhyThisPaperMatters(node, incidentEdges),
                buildDivider(),
                buildDoiRow(pub),
                buildDivider(),
                buildAbstractSection(pub),
                buildDivider(),
                buildAiSummarySection(pub),
                buildDivider(),
                buildResearchTimeline(node),
                buildDivider(),
                buildReadingPath(node, incidentEdges),
                buildDivider(),
                buildRelationships(incidentEdges),
                buildDivider(),
                buildExploreSection(node),
                buildDivider(),
                buildAskAISection(node),
                buildDivider(),
                buildNotesSection(entry),
                buildDivider(),
                buildActions(pub)
        );
    }

    private VBox buildAtAGlance(GraphNode node, List<GraphEdge> edges) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: rgba(74,156,247,0.08); -fx-background-radius: 8; -fx-padding: 10;");
        box.setMaxWidth(LABEL_MAX_WIDTH);

        Label icon = new Label("📌");
        icon.setStyle("-fx-font-size: 14px;");
        Label title = new Label("AT A GLANCE");
        title.setStyle("-fx-text-fill: #4a9cf7; -fx-font-size: 9px; -fx-font-weight: bold;");
        HBox header = new HBox(6, icon, title);
        header.setAlignment(Pos.CENTER_LEFT);

        String sentence = computeAtAGlance(node, edges);
        Label text = new Label(sentence);
        text.setWrapText(true);
        text.setMaxWidth(LABEL_MAX_WIDTH - 20);
        text.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10.5px; -fx-line-spacing: 2;");

        box.getChildren().addAll(header, text);
        return box;
    }

    private String computeAtAGlance(GraphNode node, List<GraphEdge> edges) {
        int extendedBy = 0, extends_ = 0, contradicts = 0, supports = 0, methodology = 0;
        for (GraphEdge e : edges) {
            String t = e.type.toUpperCase();
            if (t.equals("EXTENDS") && e.a == node) extendedBy++;
            if (t.equals("EXTENDS") && e.b == node) extends_++;
            if (t.equals("CONTRADICTS")) contradicts++;
            if (t.equals("SUPPORTS")) supports++;
            if (t.equals("METHODOLOGY")) methodology++;
        }

        boolean isFoundational = node.importance > 0.7 && extends_ == 0 && extendedBy > 0;
        boolean isLatest = node.year > 0 && extendedBy == 0 && extends_ > 0;
        boolean isMethodological = methodology > 1;

        StringBuilder sb = new StringBuilder("This is ");
        if (isFoundational) {
            sb.append("a foundational paper");
            if (extendedBy > 0) sb.append(" that ").append(extendedBy).append(extendedBy == 1 ? " paper extends" : " papers extend");
        } else if (isLatest) {
            sb.append("a recent contribution that builds on ").append(extends_).append(extends_ == 1 ? " earlier work" : " earlier works");
        } else if (isMethodological) {
            sb.append("a methodological hub sharing techniques with ").append(methodology).append(" related works");
        } else if (node.importance > 0.5) {
            sb.append("a significant paper in this research area");
        } else {
            sb.append("a paper in your library");
        }

        if (contradicts > 0) sb.append(", with ").append(contradicts).append(contradicts == 1 ? " contradicting study" : " contradicting studies");
        if (node.clusterLabel != null) sb.append(", within the \"" + truncate(node.clusterLabel, 30) + "\" cluster");
        sb.append(".");
        return sb.toString();
    }

    private VBox buildHeader(Publication pub) {
        VBox box = new VBox(4);
        box.setMaxWidth(LABEL_MAX_WIDTH);

        Label title = new Label(pub.getTitle() != null ? pub.getTitle() : "Untitled");
        title.setWrapText(true);
        title.setMaxWidth(LABEL_MAX_WIDTH);
        title.setMinHeight(USE_COMPUTED_SIZE);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        String authStr = pub.getAuthorsShort();
        if (authStr != null && !authStr.isBlank()) {
            Label authors = new Label(authStr);
            authors.setWrapText(true);
            authors.setMaxWidth(LABEL_MAX_WIDTH);
            authors.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10px;");
            box.getChildren().add(authors);
        }

        FlowPane meta = new FlowPane(6, 4);
        meta.setMaxWidth(LABEL_MAX_WIDTH);
        if (pub.getVenue() != null && !pub.getVenue().isBlank()) {
            meta.getChildren().add(metaChip(pub.getVenue(), "#4a9cf7"));
        }
        if (pub.getYear() > 0) {
            meta.getChildren().add(metaChip(String.valueOf(pub.getYear()), "#6c5ce7"));
        }
        if (pub.getCitationCount() > 0) {
            meta.getChildren().add(metaChip(pub.getCitationCount() + " cit.", "#2ecc71"));
        }

        box.getChildren().addAll(0, List.of(title));
        box.getChildren().addAll(meta);
        return box;
    }

    private VBox buildImpactScore(GraphNode node) {
        VBox box = new VBox(4);
        int score = (int) Math.round(node.importance * 100);

        int total = allNodes != null ? allNodes.size() : 1;
        int rank = 1;
        if (allNodes != null) {
            for (GraphNode other : allNodes) {
                if (other.importance > node.importance) rank++;
            }
        }
        int percentile = total > 0 ? (int) Math.round(100.0 * (total - rank + 1) / total) : 0;
        String percentileLabel = "Top " + (100 - percentile) + "% in your library";
        if (rank <= 3) percentileLabel = "Top " + rank + " in your library";

        Label impLabel = new Label("RESEARCH IMPACT");
        impLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 9px; -fx-font-weight: bold;");

        HBox scoreRow = new HBox(8);
        scoreRow.setAlignment(Pos.CENTER_LEFT);
        Label scoreNum = new Label(score + "/100");
        scoreNum.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        ProgressBar bar = new ProgressBar(node.importance);
        bar.setPrefWidth(120);
        bar.setPrefHeight(8);
        String barColor = score >= 70 ? "#2ecc71" : score >= 40 ? "#f1c40f" : "#e74c3c";
        bar.setStyle("-fx-accent: " + barColor + "; -fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 4;");
        HBox.setHgrow(bar, Priority.ALWAYS);

        scoreRow.getChildren().addAll(scoreNum, bar);
        Label percLabel = new Label(percentileLabel);
        percLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 9px;");

        box.getChildren().addAll(impLabel, scoreRow, percLabel);
        return box;
    }

    private VBox buildWhyThisPaperMatters(GraphNode node, List<GraphEdge> edges) {
        VBox box = new VBox(4);
        box.getChildren().add(sectionLabel("WHY THIS PAPER MATTERS"));

        List<String> reasons = new ArrayList<>();
        Publication pub = node.entry != null ? node.entry.getPublication() : null;

        if (pub != null && pub.getCitationCount() > 0) {
            reasons.add("📊 Cited by " + pub.getCitationCount() + " papers");
        }

        int incomingExtends = 0, outgoingExtends = 0;
        for (GraphEdge e : edges) {
            if (e.type.equalsIgnoreCase("EXTENDS") && e.a == node) incomingExtends++;
            if (e.type.equalsIgnoreCase("EXTENDS") && e.b == node) outgoingExtends++;
        }
        if (incomingExtends > 0) reasons.add("🏗 Extended by " + incomingExtends + (incomingExtends == 1 ? " subsequent work" : " subsequent works"));
        if (outgoingExtends > 0) reasons.add("🏛 Builds on " + outgoingExtends + (outgoingExtends == 1 ? " foundational paper" : " foundational papers"));

        if (node.isAnchor) reasons.add("⭐ Most important paper in its cluster");
        if (node.importance > 0.8) reasons.add("🔥 Very high research impact (" + (int)(node.importance*100) + "/100)");
        if (edges.size() > 5) reasons.add("🔗 Highly connected (" + edges.size() + " relationships)");

        if (node.clusterLabel != null && allNodes != null) {
            long count = allNodes.stream().filter(n -> node.clusterLabel.equals(n.clusterLabel)).count();
            reasons.add("📂 Part of \"" + truncate(node.clusterLabel, 25) + "\" (" + count + " papers)");
        }

        if (pub != null && pub.getYear() >= 2023) reasons.add("🆕 Recent publication (" + pub.getYear() + ")");

        if (reasons.isEmpty()) {
            box.getChildren().add(dimLabel("No strong importance signals detected."));
        } else {
            for (String reason : reasons) {
                Label lbl = new Label(reason);
                lbl.setWrapText(true);
                lbl.setMaxWidth(LABEL_MAX_WIDTH);
                lbl.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10px; -fx-padding: 2 0;");
                box.getChildren().add(lbl);
            }
        }
        return box;
    }

    private VBox buildDoiRow(Publication pub) {
        VBox container = new VBox(4);
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Label doiLbl = sectionLabel("DOI");
        if (pub.getDoi() != null && !pub.getDoi().isBlank()) {
            Hyperlink link = new Hyperlink(pub.getDoi());
            link.setStyle("-fx-text-fill: #4a9cf7; -fx-font-size: 10px; -fx-underline: true;");
            link.setOnAction(e -> openUrl("https://doi.org/" + pub.getDoi()));
            row.getChildren().addAll(doiLbl, link);
        } else {
            row.getChildren().addAll(doiLbl, dimLabel("Not available"));
        }
        container.getChildren().add(row);

        String doi = pub.getDoi();
        String title = pub.getTitle();
        String sQuery = (doi != null && !doi.isBlank()) ? doi : (title != null ? title : "");
        String rQuery = (title != null && !title.isBlank()) ? title : (doi != null ? doi : "");

        if (!sQuery.isBlank() || !rQuery.isBlank()) {
            HBox linkBox = new HBox(6);
            linkBox.setAlignment(Pos.CENTER_LEFT);
            if (!sQuery.isBlank()) {
                Hyperlink scholarLink = new Hyperlink("🎓 Google Scholar");
                scholarLink.setStyle("-fx-text-fill: #82b1ff; -fx-font-size: 10px; -fx-padding: 0;");
                scholarLink.setOnAction(e -> {
                    try {
                        openUrl("https://scholar.google.com/scholar?q=" + java.net.URLEncoder.encode(sQuery, java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                });
                linkBox.getChildren().add(scholarLink);
            }
            if (!rQuery.isBlank()) {
                Hyperlink rgLink = new Hyperlink("🟢 ResearchGate");
                rgLink.setStyle("-fx-text-fill: #64ffda; -fx-font-size: 10px; -fx-padding: 0;");
                rgLink.setOnAction(e -> {
                    try {
                        openUrl("https://www.researchgate.net/search/publication?q=" + java.net.URLEncoder.encode(rQuery, java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                });
                linkBox.getChildren().add(rgLink);
            }
            container.getChildren().add(linkBox);
        }

        return container;
    }

    private VBox buildAbstractSection(Publication pub) {
        VBox box = new VBox(4);
        box.getChildren().add(sectionLabel("ABSTRACT"));

        String abs = pub.getAbstractText();
        if (abs == null || abs.isBlank()) {
            box.getChildren().add(dimLabel("No abstract available."));
            return box;
        }

        Label text = new Label(abs);
        text.setWrapText(true);
        text.setMaxWidth(LABEL_MAX_WIDTH);
        text.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10.5px; -fx-line-spacing: 2;");
        text.setMaxHeight(80);
        text.setMinHeight(Region.USE_PREF_SIZE);

        boolean[] expanded = {false};
        Hyperlink toggle = new Hyperlink("Show more ▾");
        toggle.setStyle("-fx-text-fill: #4a9cf7; -fx-font-size: 10px;");
        toggle.setOnAction(e -> {
            expanded[0] = !expanded[0];
            text.setMaxHeight(expanded[0] ? Double.MAX_VALUE : 80);
            toggle.setText(expanded[0] ? "Show less ▴" : "Show more ▾");
        });

        box.getChildren().addAll(text, toggle);
        return box;
    }

    private VBox buildAiSummarySection(Publication pub) {
        VBox box = new VBox(4);
        box.getChildren().add(sectionLabel("AI SUMMARY"));

        String abs = pub.getAbstractText();
        if (abs == null || abs.length() < 60) {
            box.getChildren().add(dimLabel("Abstract too short to summarize."));
            return box;
        }

        List<String> sentences = TextRankSummarizer.summarize(abs, 2);
        String summary = String.join(" ", sentences);
        Label text = new Label(summary);
        text.setWrapText(true);
        text.setMaxWidth(LABEL_MAX_WIDTH);
        text.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10.5px;"
                + " -fx-background-color: rgba(74,108,247,0.06); -fx-padding: 6;"
                + " -fx-background-radius: 6;");
        box.getChildren().add(text);
        return box;
    }

    private HBox buildResearchTimeline(GraphNode node) {
        HBox box = new HBox(0);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 4, 0));

        List<Integer> years = new ArrayList<>();
        int nodeYear = node.year;
        if (allNodes != null) {
            for (GraphNode n : allNodes) {
                if (n.clusterLabel != null && n.clusterLabel.equals(node.clusterLabel) && n.year > 0) {
                    years.add(n.year);
                }
            }
        }
        if (years.isEmpty()) {
            box.getChildren().add(dimLabel("No year data available."));
            return box;
        }

        int minY = Collections.min(years);
        int maxY = Collections.max(years);
        if (minY == maxY) maxY = minY + 1;

        VBox container = new VBox(2);
        container.getChildren().add(sectionLabel("RESEARCH TIMELINE"));

        HBox timeline = new HBox(0);
        timeline.setAlignment(Pos.CENTER_LEFT);
        double totalWidth = 250.0;
        double range = maxY - minY;

        Region bgBar = new Region();
        bgBar.setPrefSize(totalWidth, 4);
        bgBar.setMinSize(totalWidth, 4);
        bgBar.setMaxSize(totalWidth, 4);
        bgBar.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 2;");

        StackPane timelineStack = new StackPane();
        timelineStack.setAlignment(Pos.CENTER_LEFT);
        timelineStack.getChildren().add(bgBar);

        for (int year : years) {
            double xPos = totalWidth * (year - minY) / range;
            boolean isCurrent = (year == nodeYear);
            Region marker = new Region();
            double size = isCurrent ? 10 : 5;
            marker.setMinSize(size, size);
            marker.setMaxSize(size, size);
            String markerColor = isCurrent ? "#ffffff" : "rgba(74,156,247,0.5)";
            String markerBorder = isCurrent ? "-fx-border-color: #4a9cf7; -fx-border-width: 2; -fx-border-radius: " + (size/2) + ";" : "";
            marker.setStyle("-fx-background-color: " + markerColor + "; -fx-background-radius: " + (size/2) + ";" + markerBorder);
            marker.setTranslateX(xPos - size/2);
            timelineStack.getChildren().add(marker);
        }

        HBox labels = new HBox();
        labels.setPrefWidth(totalWidth);
        Label minLabel = new Label(String.valueOf(minY));
        minLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 8px;");
        Region labelSpacer = new Region();
        HBox.setHgrow(labelSpacer, Priority.ALWAYS);
        Label curLabel = new Label(nodeYear > 0 ? "★ " + nodeYear : "");
        curLabel.setStyle("-fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold;");
        Region labelSpacer2 = new Region();
        HBox.setHgrow(labelSpacer2, Priority.ALWAYS);
        Label maxLabel = new Label(String.valueOf(maxY));
        maxLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 8px;");
        labels.getChildren().addAll(minLabel, labelSpacer, curLabel, labelSpacer2, maxLabel);

        container.getChildren().addAll(timelineStack, labels);
        box.getChildren().add(container);
        return box;
    }

    private VBox buildReadingPath(GraphNode node, List<GraphEdge> edges) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel("READING PATH"));

        GraphNode mustRead = findMustRead(node, edges);
        if (mustRead != null) box.getChildren().add(readingPathRow("🎯 Must-Read First", mustRead));

        List<GraphNode> preds = predecessors(node, edges);
        if (!preds.isEmpty()) {
            box.getChildren().add(subLabel("⬅ Predecessors"));
            for (GraphNode n : preds.subList(0, Math.min(3, preds.size()))) box.getChildren().add(readingPathRow("", n));
        }

        List<GraphNode> succs = successors(node, edges);
        if (!succs.isEmpty()) {
            box.getChildren().add(subLabel("➡ Successors"));
            for (GraphNode n : succs.subList(0, Math.min(3, succs.size()))) box.getChildren().add(readingPathRow("", n));
        }

        if (box.getChildren().size() == 1) {
            box.getChildren().add(dimLabel("No directed relationships found."));
        }
        return box;
    }

    private GraphNode findMustRead(GraphNode node, List<GraphEdge> edges) {
        GraphNode best = null;
        for (GraphEdge e : edges) {
            if (e.type.equalsIgnoreCase("RELATED")) continue;
            if (e.b != node) continue; // incoming
            GraphNode src = e.a;
            if (best == null || src.importance > best.importance
                    || (src.importance == best.importance && e.confidence > 0.5)) {
                best = src;
            }
        }
        return best;
    }

    private List<GraphNode> predecessors(GraphNode node, List<GraphEdge> edges) {
        List<GraphNode> out = new ArrayList<>();
        for (GraphEdge e : edges) {
            if (e.b != node) continue;
            if (e.type.equalsIgnoreCase("EXTENDS") || e.type.equalsIgnoreCase("SUPPORTS") || e.type.equalsIgnoreCase("METHODOLOGY")) {
                out.add(e.a);
            }
        }
        out.sort((a, b) -> Integer.compare(b.year, a.year));
        return out;
    }

    private List<GraphNode> successors(GraphNode node, List<GraphEdge> edges) {
        List<GraphNode> out = new ArrayList<>();
        for (GraphEdge e : edges) {
            if (e.a != node) continue;
            out.add(e.b);
        }
        out.sort((a, b) -> Double.compare(b.importance, a.importance));
        return out;
    }

    private HBox readingPathRow(String prefix, GraphNode target) {
        String yr = target.year > 0 ? "(" + target.year + ")" : "";
        String text = (prefix.isBlank() ? "" : prefix + " ") + truncate(target.title, 34) + " " + yr;
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10px;"
                + " -fx-padding: 3 6; -fx-background-color: rgba(74,156,247,0.07);"
                + " -fx-background-radius: 4; -fx-cursor: hand;");

        lbl.setOnMouseClicked(e -> {
            if (onNavigateToNode != null) onNavigateToNode.accept(target);
            show(target, allNodes != null ? collectEdges(target) : new ArrayList<>(), allNodes);
        });
        lbl.setOnMouseEntered(e -> lbl.setStyle(lbl.getStyle().replace("rgba(74,156,247,0.07)", "rgba(74,156,247,0.18)")));
        lbl.setOnMouseExited(e -> lbl.setStyle(lbl.getStyle().replace("rgba(74,156,247,0.18)", "rgba(74,156,247,0.07)")));

        Color cc = target.clusterColor != null ? target.clusterColor : Color.web("#4a9cf7");
        Region dot = new Region();
        dot.setMinSize(6, 6);
        dot.setMaxSize(6, 6);
        dot.setStyle("-fx-background-color: " + toHex(cc) + "; -fx-background-radius: 3;");

        HBox row = new HBox(6, dot, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private List<GraphEdge> collectEdges(GraphNode target) {
        // We do not have all edges stored directly here, so we have to just rely on the ones passed in
        return new ArrayList<>();
    }

    private VBox buildRelationships(List<GraphEdge> edges) {
        VBox box = new VBox(6);
        
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label header = sectionLabel("RELATIONSHIPS");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnAdd = new Button("＋ Add");
        btnAdd.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9cf7; -fx-padding: 0; -fx-cursor: hand;");
        btnAdd.setOnAction(e -> {
            if (onAddRelationship != null) onAddRelationship.run();
        });
        headerBox.getChildren().addAll(header, spacer, btnAdd);
        box.getChildren().add(headerBox);

        if (edges.isEmpty()) {
            box.getChildren().add(dimLabel("No connections."));
            return box;
        }

        edges.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        for (GraphEdge edge : edges.subList(0, Math.min(12, edges.size()))) {
            box.getChildren().add(buildEdgeRow(edge));
        }

        return box;
    }

    private VBox buildEdgeRow(GraphEdge edge) {
        GraphNode other = (currentNode != null && edge.a == currentNode) ? edge.b : edge.a;

        Label typeBadge = new Label(edge.type.toUpperCase());
        String tc = getTypeColor(edge.type);
        typeBadge.setStyle("-fx-background-color: " + tc.replace("#", "rgba(") + ",0.2);"
                + "-fx-text-fill: " + tc + ";"
                + "-fx-font-size: 8px; -fx-font-weight: bold;"
                + "-fx-padding: 2 5; -fx-background-radius: 3;");

        Label title = new Label(truncate(other.title, 26));
        title.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10px; -fx-cursor: hand;");
        title.setWrapText(true);
        title.setOnMouseClicked(ev -> {
            if (onNavigateToNode != null) onNavigateToNode.accept(other);
            show(other, new ArrayList<>(), allNodes);
        });
        title.setOnMouseEntered(ev -> title.setStyle(title.getStyle() + "-fx-underline: true;"));
        title.setOnMouseExited(ev -> title.setStyle(title.getStyle().replace("-fx-underline: true;", "")));

        ProgressBar bar = new ProgressBar(edge.confidence);
        bar.setPrefWidth(60);
        bar.setPrefHeight(4);
        bar.setStyle("-fx-accent: " + tc + ";"
                + "-fx-background-color: rgba(255,255,255,0.1);"
                + "-fx-background-radius: 2;");
        Label confLbl = new Label((int) Math.round(edge.confidence * 100) + "%");
        confLbl.setStyle("-fx-text-fill: " + tc + "; -fx-font-size: 9px;");

        HBox confRow = new HBox(4, bar, confLbl);
        confRow.setAlignment(Pos.CENTER_LEFT);
        
        VBox row = new VBox(3, typeBadge, title, confRow);
        if (edge.reasoning != null && !edge.reasoning.isEmpty()) {
            Label reason = new Label("AI: " + edge.reasoning);
            reason.setWrapText(true);
            reason.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 9px; -fx-font-style: italic;");
            row.getChildren().add(reason);
        }
        
        row.setPadding(new Insets(6));
        row.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-background-radius: 6;");
        return row;
    }

    private VBox buildExploreSection(GraphNode node) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel("EXPLORE CONNECTIONS"));

        String[] prompts = {
                "🔍 Find Similar Papers",
                "⬅️ Find Prior Work (Citations)",
                "➡️ Find Derivative Work (References)"
        };

        for (String prompt : prompts) {
            Button btn = new Button(prompt);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: rgba(255,255,255,0.04);"
                    + "-fx-text-fill: #4a9cf7;"
                    + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 6 10;"
                    + "-fx-background-radius: 6; -fx-cursor: hand;");
            btn.setOnMouseEntered(ev -> btn.setStyle(btn.getStyle().replace("rgba(255,255,255,0.04)", "rgba(74,156,247,0.2)")));
            btn.setOnMouseExited(ev -> btn.setStyle(btn.getStyle().replace("rgba(74,156,247,0.2)", "rgba(255,255,255,0.04)")));
            btn.setOnAction(ev -> {
                Label stub = new Label("Local graph expansion using AI running...");
                stub.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 9px;");
                if (!box.getChildren().contains(stub)) box.getChildren().add(stub);
            });
            box.getChildren().add(btn);
        }
        return box;
    }

    private VBox buildAskAISection(GraphNode node) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel("ASK AI"));

        String[] prompts = {
                "Why is this paper important?",
                "Explain the methodology.",
                "Summarize key contributions."
        };

        for (String prompt : prompts) {
            Button btn = new Button("💬 " + prompt);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: rgba(255,255,255,0.04);"
                    + "-fx-text-fill: #bbbbdd;"
                    + "-fx-font-size: 9.5px; -fx-padding: 6 10;"
                    + "-fx-background-radius: 6; -fx-cursor: hand;");
            btn.setOnMouseEntered(ev -> btn.setStyle(btn.getStyle().replace("rgba(255,255,255,0.04)", "rgba(74,156,247,0.15)")));
            btn.setOnMouseExited(ev -> btn.setStyle(btn.getStyle().replace("rgba(74,156,247,0.15)", "rgba(255,255,255,0.04)")));
            btn.setOnAction(ev -> runAskAI(node, prompt, box));
            box.getChildren().add(btn);
        }
        return box;
    }

    private void runAskAI(GraphNode node, String question, VBox container) {
        Publication pub = node.entry != null ? node.entry.getPublication() : null;
        if (pub == null) return;

        if (!GeminiConfig.isConfigured()) {
            Label err = new Label("⚠ Gemini API not configured. Set your API key in Settings.");
            err.setWrapText(true);
            err.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 9.5px; -fx-padding: 6;"
                    + "-fx-background-color: rgba(241,196,15,0.08); -fx-background-radius: 6;");
            container.getChildren().add(err);
            return;
        }

        Label loading = new Label("⏳ AI is thinking...");
        loading.setStyle("-fx-text-fill: #4a9cf7; -fx-font-size: 9.5px;");
        container.getChildren().add(loading);

        String context = "Paper: " + pub.getTitle() + "\n";
        if (pub.getAbstractText() != null) context += "Abstract: " + pub.getAbstractText() + "\n";
        if (pub.getVenue() != null) context += "Venue: " + pub.getVenue() + "\n";
        if (pub.getYear() > 0) context += "Year: " + pub.getYear() + "\n";
        context += "\nQuestion: " + question;
        
        final String finalContext = context;

        new Thread(() -> {
            try {
                GeminiAIService ai = new GeminiAIService();
                String systemPrompt = "You are a research assistant helping analyze scientific papers. Give concise, insightful answers (3-5 sentences). Focus on academic value.";
                String response = ai.chat(systemPrompt, finalContext);
                Platform.runLater(() -> {
                    container.getChildren().remove(loading);
                    Label answer = new Label(response != null ? response : "No response received.");
                    answer.setWrapText(true);
                    answer.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10px;"
                            + "-fx-background-color: rgba(74,156,247,0.06); -fx-padding: 8;"
                            + "-fx-background-radius: 6; -fx-line-spacing: 2;");
                    container.getChildren().add(answer);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    container.getChildren().remove(loading);
                    Label err = new Label("❌ " + ex.getMessage());
                    err.setWrapText(true);
                    err.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 9.5px;");
                    container.getChildren().add(err);
                });
            }
        }).start();
    }

    private VBox buildNotesSection(LibraryEntry entry) {
        VBox box = new VBox(4);
        box.getChildren().add(sectionLabel("NOTES"));

        TextArea notes = new TextArea(entry.getNotes() != null ? entry.getNotes() : "");
        notes.setPromptText("Add your notes here...");
        notes.setWrapText(true);
        notes.setPrefRowCount(4);
        notes.setStyle("-fx-font-size: 10.5px; -fx-control-inner-background: #1a1a30;"
                + "-fx-text-fill: #bbbbdd;"
                + "-fx-border-color: #2a2a3e; -fx-background-radius: 6;");

        notes.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && libraryService != null) {
                libraryService.updateNotes(entry.getId(), notes.getText());
            }
        });
        box.getChildren().add(notes);
        return box;
    }

    private HBox buildActions(Publication pub) {
        Button openDoi = actionBtn("🔗 DOI", "#4a9cf7");
        openDoi.setOnAction(e -> {
            if (pub.getDoi() != null) openUrl("https://doi.org/" + pub.getDoi());
        });
        openDoi.setDisable(pub.getDoi() == null || pub.getDoi().isBlank());

        String doi = pub.getDoi();
        String title = pub.getTitle();
        String sQuery = (doi != null && !doi.isBlank()) ? doi : (title != null ? title : "");
        String rQuery = (title != null && !title.isBlank()) ? title : (doi != null ? doi : "");

        Button scholar = actionBtn("🎓 Scholar", "#1a73e8");
        scholar.setOnAction(e -> {
            try {
                openUrl("https://scholar.google.com/scholar?q=" + java.net.URLEncoder.encode(sQuery, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        });
        scholar.setDisable(sQuery.isBlank());

        Button rg = actionBtn("🟢 RG", "#00897b");
        rg.setOnAction(e -> {
            try {
                openUrl("https://www.researchgate.net/search/publication?q=" + java.net.URLEncoder.encode(rQuery, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        });
        rg.setDisable(rQuery.isBlank());

        Button cite = actionBtn("📋 Cite", "#6c5ce7");
        cite.setOnAction(e -> copyToClipboard(buildCitation(pub)));

        HBox bar = new HBox(6, openDoi, scholar, rg, cite);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 0, 8, 0));
        return bar;
    }

    private String getTypeColor(String type) {
        switch (type.toUpperCase()) {
            case "CITES": return "#8888bb";
            case "SUPPORTS": return "#2ecc71";
            case "CONTRADICTS": return "#e74c3c";
            case "EXTENDS": return "#9b59b6";
            default: return "#4a9cf7";
        }
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 9px;"
                + "-fx-font-weight: bold; -fx-padding: 2 0 0 0;");
        return l;
    }

    private static Label subLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #bbbbdd; -fx-font-size: 10px;"
                + "-fx-font-weight: bold;");
        return l;
    }

    private static Label dimLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 10px;");
        l.setWrapText(true);
        l.setMaxWidth(LABEL_MAX_WIDTH);
        return l;
    }

    private static Label metaChip(String text, String hex) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + hex.replace("#", "rgba(") + ",0.18);"
                + "-fx-text-fill: " + hex + ";"
                + "-fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 8;");
        return l;
    }

    private static Button actionBtn(String text, String hexColor) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: rgba(255,255,255,0.06);"
                + "-fx-text-fill: " + hexColor + ";"
                + "-fx-font-size: 10px; -fx-padding: 5 10;"
                + "-fx-background-radius: 6; -fx-cursor: hand;");
        b.setOnMouseEntered(e -> b.setStyle(b.getStyle().replace("rgba(255,255,255,0.06)", "rgba(255,255,255,0.12)")));
        b.setOnMouseExited(e -> b.setStyle(b.getStyle().replace("rgba(255,255,255,0.12)", "rgba(255,255,255,0.06)")));
        return b;
    }

    private static Separator buildDivider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2a2a3e;");
        return sep;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String toHex(Color c) {
        if (c == null) return "#4a9cf7";
        return String.format("#%02x%02x%02x",
                (int)(c.getRed()   * 255),
                (int)(c.getGreen() * 255),
                (int)(c.getBlue()  * 255));
    }

    private static void openUrl(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception ignored) {}
    }

    private static void copyToClipboard(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private static String buildCitation(Publication pub) {
        try {
            return CitationStyleManager.getInstance().formatCitation(pub, "apa");
        } catch (Exception e) {
            return pub.getTitle() != null ? pub.getTitle() : "Unknown";
        }
    }
}
