package com.citeright.ui;

import com.citeright.model.EvidenceStrength;
import com.citeright.model.GapSeverity;
import com.citeright.model.ResearchGap;
import com.citeright.service.ResearchGapExplainer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Visual card representing a single Research Gap / Opportunity.
 */
public class GapCardView extends VBox {

    private final ResearchGap gap;
    private final ResearchGapExplainer explainer;
    private Consumer<ResearchGap> onSelectHandler;

    public GapCardView(ResearchGap gap, ResearchGapExplainer explainer) {
        this.gap = gap;
        this.explainer = explainer;
        buildUI();
    }

    public void setOnSelectHandler(Consumer<ResearchGap> handler) {
        this.onSelectHandler = handler;
    }

    private void buildUI() {
        setPadding(new Insets(14));
        setSpacing(10);
        setStyle("-fx-background-color: #262637; -fx-background-radius: 10; " +
                "-fx-border-color: #36364d; -fx-border-radius: 10; -fx-cursor: hand;");

        setOnMouseEntered(e -> setStyle("-fx-background-color: #2d2d42; -fx-background-radius: 10; " +
                "-fx-border-color: #5c5c8a; -fx-border-radius: 10; -fx-cursor: hand;"));
        setOnMouseExited(e -> setStyle("-fx-background-color: #262637; -fx-background-radius: 10; " +
                "-fx-border-color: #36364d; -fx-border-radius: 10; -fx-cursor: hand;"));

        setOnMouseClicked(e -> {
            if (onSelectHandler != null) {
                onSelectHandler.accept(gap);
            }
        });

        // Top Row: Type Badge + Score Pill
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(getTypeLabel());
        typeBadge.setStyle("-fx-background-color: " + getTypeBgColor() + "; -fx-text-fill: " + getTypeFgColor() + "; " +
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");

        Label scorePill = new Label(String.format("🔥 Opp Score: %.0f", gap.getOpportunityScore()));
        scorePill.setStyle("-fx-background-color: " + getScoreBgColor(gap.getOpportunityScore()) + "; " +
                "-fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 12;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label severityBadge = new Label(gap.getSeverity() != null ? gap.getSeverity().name() : "LOW");
        severityBadge.setStyle("-fx-text-fill: #a0a0c0; -fx-font-size: 10px; -fx-font-weight: bold;");

        topRow.getChildren().addAll(typeBadge, scorePill, spacer, severityBadge);

        // Title
        Label titleLabel = new Label(gap.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Summary
        String cardSummary = explainer != null ? explainer.generateCardSummary(gap) : gap.getExplanation();
        Label summaryLabel = new Label(cardSummary);
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-text-fill: #b0b0d0; -fx-font-size: 12px;");

        // Bottom Row: Confidence & Evidence strength
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        Label confidenceLabel = new Label(String.format("Confidence: %.0f%%", gap.getConfidence() * 100));
        confidenceLabel.setStyle("-fx-text-fill: #8080a0; -fx-font-size: 11px;");

        EvidenceStrength ev = gap.getEvidenceStrength();
        Label evidenceLabel = new Label("Evidence: " + (ev != null ? ev.name() : "LOW"));
        evidenceLabel.setStyle("-fx-text-fill: " + getEvidenceColor(ev) + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        bottomRow.getChildren().addAll(confidenceLabel, evidenceLabel);

        getChildren().addAll(topRow, titleLabel, summaryLabel, bottomRow);
    }

    private String getTypeLabel() {
        if (gap.getType() == null) return "GAP";
        return switch (gap.getType()) {
            case TOPIC -> "💡 TOPIC COMBINATION";
            case METHODOLOGY_TRANSFER -> "⚡ METHOD TRANSFER";
            case TEMPORAL_DORMANT -> "⏳ DORMANT TOPIC";
            case TEMPORAL_DECLINING -> "📉 DECLINING AREA";
            case INTERDISCIPLINARY -> "🌉 INTERDISCIPLINARY";
        };
    }

    private String getTypeBgColor() {
        if (gap.getType() == null) return "#3a3a50";
        return switch (gap.getType()) {
            case TOPIC -> "#2d4a68";
            case METHODOLOGY_TRANSFER -> "#4a3b68";
            case TEMPORAL_DORMANT -> "#5c4328";
            case TEMPORAL_DECLINING -> "#5c2838";
            case INTERDISCIPLINARY -> "#285c4d";
        };
    }

    private String getTypeFgColor() {
        return "#ffffff";
    }

    private String getScoreBgColor(double score) {
        if (score >= 70) return "#d9534f"; // high opportunity (red/orange)
        if (score >= 40) return "#f0ad4e"; // medium
        return "#5bc0de"; // low
    }

    private String getEvidenceColor(EvidenceStrength strength) {
        if (strength == null) return "#8080a0";
        return switch (strength) {
            case HIGH -> "#5cb85c";
            case MEDIUM -> "#f0ad4e";
            case LOW -> "#d9534f";
        };
    }
}
