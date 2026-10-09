package com.citeright.ui;

import com.citeright.database.ResearchGapDAO;
import com.citeright.model.GapStatus;
import com.citeright.model.ResearchGap;
import com.citeright.model.UncertaintyFactor;
import com.citeright.service.ResearchGapExplainer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Detailed view for an individual Research Gap / Opportunity.
 */
public class GapDetailView extends VBox {

    private final ResearchGap gap;
    private final ResearchGapExplainer explainer;
    private final ResearchGapDAO dao;
    private Consumer<GapStatus> onStatusChangeHandler;

    private TextArea notesArea;
    private Label statusLabel;

    public GapDetailView(ResearchGap gap, ResearchGapExplainer explainer) {
        this.gap = gap;
        this.explainer = explainer;
        this.dao = new ResearchGapDAO();
        buildUI();
    }

    public void setOnStatusChangeHandler(Consumer<GapStatus> handler) {
        this.onStatusChangeHandler = handler;
    }

    private void buildUI() {
        setPadding(new Insets(20));
        setSpacing(16);
        setStyle("-fx-background-color: #1e1e2e;");

        // Header Section
        VBox headerBox = new VBox(8);
        
        HBox topMeta = new HBox(12);
        topMeta.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label("TYPE: " + (gap.getType() != null ? gap.getType().name() : "GAP"));
        typeLabel.setStyle("-fx-text-fill: #8a8ab0; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label scoreLabel = new Label(String.format("OPPORTUNITY SCORE: %.1f / 100", gap.getOpportunityScore()));
        scoreLabel.setStyle("-fx-text-fill: #ff79c6; -fx-font-size: 11px; -fx-font-weight: bold;");

        statusLabel = new Label("STATUS: " + (gap.getStatus() != null ? gap.getStatus().name() : "NEW"));
        statusLabel.setStyle("-fx-text-fill: #50fa7b; -fx-font-size: 11px; -fx-font-weight: bold;");

        topMeta.getChildren().addAll(typeLabel, scoreLabel, statusLabel);

        Label titleLabel = new Label(gap.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;");

        // Action Buttons Row
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("⭐ Save Opportunity");
        saveBtn.setStyle("-fx-background-color: #f1fa8c; -fx-text-fill: #282a36; -fx-font-weight: bold; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> updateStatus(GapStatus.SAVED));

        Button investigateBtn = new Button("🔍 Mark Investigating");
        investigateBtn.setStyle("-fx-background-color: #8be9fd; -fx-text-fill: #282a36; -fx-font-weight: bold; -fx-cursor: hand;");
        investigateBtn.setOnAction(e -> updateStatus(GapStatus.INVESTIGATED));

        Button dismissBtn = new Button("🚫 Dismiss");
        dismissBtn.setStyle("-fx-background-color: #ff5555; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        dismissBtn.setOnAction(e -> updateStatus(GapStatus.DISMISSED));

        actionsRow.getChildren().addAll(saveBtn, investigateBtn, dismissBtn);

        headerBox.getChildren().addAll(topMeta, titleLabel, actionsRow);

        // Scrollable Body
        VBox bodyBox = new VBox(16);
        bodyBox.setPadding(new Insets(10, 0, 10, 0));

        // 1. Explanation Section (Why, How, Evidence, Confidence)
        VBox explanationBox = createCardSection("📊 Research Intelligence Analysis");
        String fullExp = explainer != null ? explainer.generateFullExplanation(gap) : gap.getExplanation();
        Label expText = new Label(fullExp);
        expText.setWrapText(true);
        expText.setStyle("-fx-text-fill: #d6d6f0; -fx-font-size: 13px; -fx-line-spacing: 3;");
        explanationBox.getChildren().add(expText);

        // 2. Uncertainty Factors (Strengths vs Weaknesses)
        VBox uncertaintyBox = createCardSection("⚖️ Evidence Strengths & Uncertainty Factors");
        if (gap.getUncertaintyFactors() != null && !gap.getUncertaintyFactors().isEmpty()) {
            VBox factorsList = new VBox(6);
            for (UncertaintyFactor uf : gap.getUncertaintyFactors()) {
                Label factorLabel = new Label(uf.toDisplayString());
                factorLabel.setStyle(uf.getDirection() == UncertaintyFactor.Direction.STRENGTHENS
                        ? "-fx-text-fill: #50fa7b; -fx-font-size: 12px;"
                        : "-fx-text-fill: #ffb86c; -fx-font-size: 12px;");
                factorsList.getChildren().add(factorLabel);
            }
            uncertaintyBox.getChildren().add(factorsList);
        } else {
            Label noUncertainty = new Label("No specific uncertainty metrics logged for this gap.");
            noUncertainty.setStyle("-fx-text-fill: #707090; -fx-font-size: 12px;");
            uncertaintyBox.getChildren().add(noUncertainty);
        }

        // 3. Recommended Actions & Methods
        VBox methodsBox = createCardSection("💡 Recommended Methodologies & Focus Areas");
        if (gap.getRecommendedMethods() != null && !gap.getRecommendedMethods().isEmpty()) {
            FlowPane methodsPane = new FlowPane(8, 8);
            for (String method : gap.getRecommendedMethods()) {
                Label methodTag = new Label(method);
                methodTag.setStyle("-fx-background-color: #bd93f9; -fx-text-fill: #282a36; " +
                        "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
                methodsPane.getChildren().add(methodTag);
            }
            methodsBox.getChildren().add(methodsPane);
        } else {
            Label noMethods = new Label("No specific methodologies isolated yet.");
            noMethods.setStyle("-fx-text-fill: #707090; -fx-font-size: 12px;");
            methodsBox.getChildren().add(noMethods);
        }

        // 4. User Notes
        VBox notesBox = createCardSection("📝 Researcher Notes");
        notesArea = new TextArea();
        notesArea.setPromptText("Write your observations or hypothesis for this research gap...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);
        notesArea.setStyle("-fx-control-inner-background: #282a36; -fx-text-fill: #f8f8f2; -fx-font-size: 12px;");
        if (gap.getUserNotes() != null) {
            notesArea.setText(gap.getUserNotes());
        }

        Button saveNotesBtn = new Button("Save Notes");
        saveNotesBtn.setStyle("-fx-background-color: #6272a4; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-cursor: hand;");
        saveNotesBtn.setOnAction(e -> {
            gap.setUserNotes(notesArea.getText());
            dao.updateNotes(gap.getGapId(), notesArea.getText());
        });

        notesBox.getChildren().addAll(notesArea, saveNotesBtn);

        bodyBox.getChildren().addAll(explanationBox, uncertaintyBox, methodsBox, notesBox);

        ScrollPane scrollPane = new ScrollPane(bodyBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(headerBox, scrollPane);
    }

    private VBox createCardSection(String title) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: #282a36; -fx-background-radius: 8; -fx-border-color: #44475a; -fx-border-radius: 8;");

        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-text-fill: #8be9fd; -fx-font-size: 14px; -fx-font-weight: bold;");

        section.getChildren().add(sectionTitle);
        return section;
    }

    private void updateStatus(GapStatus newStatus) {
        gap.setStatus(newStatus);
        dao.updateStatus(gap.getGapId(), newStatus);
        statusLabel.setText("STATUS: " + newStatus.name());
        if (onStatusChangeHandler != null) {
            onStatusChangeHandler.accept(newStatus);
        }
    }
}
