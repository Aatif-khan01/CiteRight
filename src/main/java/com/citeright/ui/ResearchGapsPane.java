package com.citeright.ui;

import com.citeright.model.GapType;
import com.citeright.model.ResearchGap;
import com.citeright.service.GapAnalysisScheduler;
import com.citeright.service.ResearchGapEngine;
import com.citeright.service.ResearchGapExplainer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main Research Gaps & Opportunities Discovery Workspace.
 */
public class ResearchGapsPane extends BorderPane {

    private final GapAnalysisScheduler scheduler;
    private final ResearchGapEngine engine;
    private final ResearchGapExplainer explainer;

    private VBox cardsListContainer;
    private ScrollPane cardsScrollPane;
    private StackPane detailContainer;

    private Label totalGapsCounter;
    private Label opportunitiesCounter;
    private Button runAnalysisBtn;
    private ProgressIndicator analysisProgress;

    private GapType activeFilterType = null; // null = ALL
    private boolean showOnlyOpportunities = false;

    public ResearchGapsPane() {
        this.scheduler = new GapAnalysisScheduler();
        this.engine = scheduler.getEngine();
        this.explainer = engine.getExplainer();

        buildUI();
        initScheduler();
    }

    private void buildUI() {
        setStyle("-fx-background-color: #1e1e2e;");

        // ── Top Bar ─────────────────────────────────────────────────────────
        VBox topBar = new VBox(12);
        topBar.setPadding(new Insets(16, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 0 0 1 0;");

        HBox headerRow = new HBox(16);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("💡 Research Gap & Opportunity Engine");
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        totalGapsCounter = new Label("Gaps Detected: 0");
        totalGapsCounter.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12px; -fx-font-weight: bold;");

        opportunitiesCounter = new Label("🔥 Opportunities: 0");
        opportunitiesCounter.setStyle("-fx-text-fill: #ff79c6; -fx-font-size: 12px; -fx-font-weight: bold;");

        runAnalysisBtn = new Button("⚡ Analyze Library Now");
        runAnalysisBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #11111b; -fx-font-weight: bold; " +
                "-fx-padding: 8 16; -fx-background-radius: 8; -fx-cursor: hand;");
        runAnalysisBtn.setOnAction(e -> triggerManualAnalysis());

        analysisProgress = new ProgressIndicator();
        analysisProgress.setPrefSize(20, 20);
        analysisProgress.setVisible(false);

        headerRow.getChildren().addAll(titleLabel, spacer, totalGapsCounter, opportunitiesCounter, runAnalysisBtn, analysisProgress);

        // Filter Tabs Row
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        ToggleButton allBtn = createFilterTab("All Gaps", true);
        allBtn.setOnAction(e -> applyFilter(null, false));

        ToggleButton oppsBtn = createFilterTab("🔥 Opportunities Only", false);
        oppsBtn.setOnAction(e -> applyFilter(null, true));

        ToggleButton topicBtn = createFilterTab("💡 Topic Combination", false);
        topicBtn.setOnAction(e -> applyFilter(GapType.TOPIC, false));

        ToggleButton methodBtn = createFilterTab("⚡ Method Transfer", false);
        methodBtn.setOnAction(e -> applyFilter(GapType.METHODOLOGY_TRANSFER, false));

        ToggleButton dormantBtn = createFilterTab("⏳ Dormant Topics", false);
        dormantBtn.setOnAction(e -> applyFilter(GapType.TEMPORAL_DORMANT, false));

        ToggleButton interBtn = createFilterTab("🌉 Interdisciplinary", false);
        interBtn.setOnAction(e -> applyFilter(GapType.INTERDISCIPLINARY, false));

        Button ideaSimBtn = new Button("🔍 Check Idea Novelty");
        ideaSimBtn.setStyle("-fx-background-color: #cba6f7; -fx-text-fill: #11111b; -fx-font-weight: bold; " +
                "-fx-padding: 5 12; -fx-background-radius: 6; -fx-cursor: hand;");
        ideaSimBtn.setOnAction(e -> showIdeaSimilarityPane());

        ToggleGroup group = new ToggleGroup();
        allBtn.setToggleGroup(group);
        oppsBtn.setToggleGroup(group);
        topicBtn.setToggleGroup(group);
        methodBtn.setToggleGroup(group);
        dormantBtn.setToggleGroup(group);
        interBtn.setToggleGroup(group);

        filterBar.getChildren().addAll(allBtn, oppsBtn, topicBtn, methodBtn, dormantBtn, interBtn, ideaSimBtn);

        topBar.getChildren().addAll(headerRow, filterBar);
        setTop(topBar);

        // ── Main Content SplitPane ──────────────────────────────────────────
        cardsListContainer = new VBox(12);
        cardsListContainer.setPadding(new Insets(16));

        cardsScrollPane = new ScrollPane(cardsListContainer);
        cardsScrollPane.setFitToWidth(true);
        cardsScrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");
        cardsScrollPane.setMinWidth(380);
        cardsScrollPane.setPrefWidth(480);

        detailContainer = new StackPane();
        detailContainer.setStyle("-fx-background-color: #181825;");
        showEmptyDetailPlaceholder();

        SplitPane splitPane = new SplitPane(cardsScrollPane, detailContainer);
        splitPane.setStyle("-fx-background-color: #1e1e2e;");
        splitPane.setDividerPositions(0.4);

        setCenter(splitPane);
    }

    private ToggleButton createFilterTab(String title, boolean selected) {
        ToggleButton btn = new ToggleButton(title);
        btn.setSelected(selected);
        btn.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-font-size: 12px; " +
                "-fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand;");

        btn.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                btn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #ffffff; -fx-font-weight: bold; " +
                        "-fx-font-size: 12px; -fx-padding: 6 12; -fx-background-radius: 6;");
            } else {
                btn.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-font-size: 12px; " +
                        "-fx-padding: 6 12; -fx-background-radius: 6;");
            }
        });
        return btn;
    }

    private void initScheduler() {
        scheduler.setCompletionCallback(gaps -> Platform.runLater(this::renderResults));
    }

    public void triggerManualAnalysis() {
        runAnalysisBtn.setDisable(true);
        analysisProgress.setVisible(true);

        new Thread(() -> {
            scheduler.triggerImmediate();
        }).start();
    }

    private void renderResults() {
        runAnalysisBtn.setDisable(false);
        analysisProgress.setVisible(false);

        List<ResearchGap> allGaps = engine.getLastResults();
        List<ResearchGap> opportunities = engine.getOpportunities();

        totalGapsCounter.setText("Gaps Detected: " + allGaps.size());
        opportunitiesCounter.setText("🔥 Opportunities: " + opportunities.size());

        populateCardsList(allGaps);
    }

    private void applyFilter(GapType type, boolean onlyOpps) {
        this.activeFilterType = type;
        this.showOnlyOpportunities = onlyOpps;
        populateCardsList(engine.getLastResults());
    }

    private void populateCardsList(List<ResearchGap> gaps) {
        cardsListContainer.getChildren().clear();

        if (gaps == null || gaps.isEmpty()) {
            VBox emptyBox = new VBox(12);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40));

            Label msg = new Label("No research gaps detected yet.");
            msg.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 14px; -fx-font-weight: bold;");

            Label hint = new Label("Click 'Analyze Library Now' or add more papers to run automatic discovery.");
            hint.setStyle("-fx-text-fill: #6c7086; -fx-font-size: 12px;");

            emptyBox.getChildren().addAll(msg, hint);
            cardsListContainer.getChildren().add(emptyBox);
            return;
        }

        List<ResearchGap> filtered = new ArrayList<>();
        for (ResearchGap g : gaps) {
            if (showOnlyOpportunities && !g.isOpportunity()) continue;
            if (activeFilterType != null && g.getType() != activeFilterType) continue;
            filtered.add(g);
        }

        if (filtered.isEmpty()) {
            Label noMatch = new Label("No gaps match the selected filter.");
            noMatch.setStyle("-fx-text-fill: #a6adc8; -fx-padding: 20;");
            cardsListContainer.getChildren().add(noMatch);
            return;
        }

        for (ResearchGap gap : filtered) {
            GapCardView card = new GapCardView(gap, explainer);
            card.setOnSelectHandler(this::showGapDetail);
            cardsListContainer.getChildren().add(card);
        }
    }

    private void showGapDetail(ResearchGap gap) {
        GapDetailView detailView = new GapDetailView(gap, explainer);
        detailView.setOnStatusChangeHandler(status -> renderResults());
        detailContainer.getChildren().clear();
        detailContainer.getChildren().add(detailView);
    }

    private void showIdeaSimilarityPane() {
        IdeaSimilarityPane pane = new IdeaSimilarityPane();
        detailContainer.getChildren().clear();
        detailContainer.getChildren().add(pane);
    }

    private void showEmptyDetailPlaceholder() {
        VBox placeholder = new VBox(12);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(40));

        Label icon = new Label("💡");
        icon.setStyle("-fx-font-size: 48px;");

        Label text = new Label("Select a research gap card on the left to view deep insights, evidence, and provenance.");
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: #6c7086; -fx-font-size: 13px; -fx-text-alignment: center;");

        placeholder.getChildren().addAll(icon, text);
        detailContainer.getChildren().clear();
        detailContainer.getChildren().add(placeholder);
    }
}
