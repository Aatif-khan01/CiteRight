package com.citeright.ui;

import com.citeright.ai.EmbeddingService;
import com.citeright.database.LibraryDAO;
import com.citeright.database.PaperEmbeddingDAO;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.service.HybridSimilarityCalculator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Idea Similarity Search Pane (V1 Foundation for Novelty Analyzer).
 * User inputs a research hypothesis or idea, and CiteRight finds the most
 * semantically similar papers, related methods, and computes an estimated overlap score.
 */
public class IdeaSimilarityPane extends VBox {

    private final LibraryDAO libraryDAO;
    private final PaperEmbeddingDAO embeddingDAO;
    private final HybridSimilarityCalculator similarityCalculator;
    private final EmbeddingService embeddingService;

    private TextField ideaInputField;
    private Button searchButton;
    private VBox resultsContainer;
    private ProgressIndicator progressIndicator;

    public IdeaSimilarityPane() {
        this.libraryDAO = new LibraryDAO();
        this.embeddingDAO = new PaperEmbeddingDAO();
        this.similarityCalculator = new HybridSimilarityCalculator();
        this.embeddingService = EmbeddingService.getInstance();

        buildUI();
    }

    private void buildUI() {
        setPadding(new Insets(20));
        setSpacing(16);
        setStyle("-fx-background-color: #1e1e2e;");

        // Title Header
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("💡 Idea Similarity & Novelty Checker");
        titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Test your research hypothesis against your entire library before writing code or papers.");
        subtitleLabel.setStyle("-fx-text-fill: #a0a0c0; -fx-font-size: 13px;");

        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Input Bar
        HBox inputBar = new HBox(12);
        inputBar.setAlignment(Pos.CENTER_LEFT);

        ideaInputField = new TextField();
        ideaInputField.setPromptText("Enter your research idea (e.g., 'Using Graph Neural Networks for thermal mapping in remote sensing')...");
        ideaInputField.setStyle("-fx-background-color: #282a36; -fx-text-fill: #ffffff; -fx-font-size: 13px; " +
                "-fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #44475a; -fx-border-radius: 8;");
        HBox.setHgrow(ideaInputField, Priority.ALWAYS);
        ideaInputField.setOnAction(e -> runSimilarityCheck());

        searchButton = new Button("🔍 Check Overlap");
        searchButton.setStyle("-fx-background-color: #bd93f9; -fx-text-fill: #282a36; -fx-font-weight: bold; " +
                "-fx-padding: 10 16; -fx-background-radius: 8; -fx-cursor: hand;");
        searchButton.setOnAction(e -> runSimilarityCheck());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(24, 24);
        progressIndicator.setVisible(false);

        inputBar.getChildren().addAll(ideaInputField, searchButton, progressIndicator);

        // Results Container
        resultsContainer = new VBox(14);
        resultsContainer.setPadding(new Insets(10, 0, 10, 0));

        ScrollPane scrollPane = new ScrollPane(resultsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(headerBox, inputBar, scrollPane);
    }

    private void runSimilarityCheck() {
        String ideaText = ideaInputField.getText().trim();
        if (ideaText.isEmpty()) return;

        searchButton.setDisable(true);
        progressIndicator.setVisible(true);
        resultsContainer.getChildren().clear();

        new Thread(() -> {
            try {
                // 1. Generate BGE-M3 embedding for input idea
                float[] ideaEmbedding = null;
                if (embeddingService.isBgeM3Ready()) {
                    ideaEmbedding = embeddingService.getEngine().getEmbedding(ideaText);
                }

                // 2. Fetch cached paper embeddings
                Map<Integer, float[]> paperEmbeddings = embeddingDAO.getAllCachedEmbeddings("bge-m3", "v1");

                // 3. Find top K similar papers
                List<int[]> topResults = similarityCalculator.findSimilarPapers(ideaEmbedding, paperEmbeddings, 10);

                List<LibraryEntry> allEntries = libraryDAO.getAll(null, null, null);
                Map<Integer, LibraryEntry> entryMap = new java.util.HashMap<>();
                for (LibraryEntry entry : allEntries) {
                    entryMap.put(entry.getId(), entry);
                }

                List<SearchResult> searchResults = new ArrayList<>();
                for (int[] res : topResults) {
                    int paperId = res[0];
                    double score = res[1] / 1000.0;
                    LibraryEntry entry = entryMap.get(paperId);
                    if (entry != null) {
                        searchResults.add(new SearchResult(entry, score));
                    }
                }

                Platform.runLater(() -> {
                    displayResults(ideaText, searchResults);
                    searchButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    searchButton.setDisable(false);
                    progressIndicator.setVisible(false);
                    Label err = new Label("Error computing similarity: " + e.getMessage());
                    err.setStyle("-fx-text-fill: #ff5555;");
                    resultsContainer.getChildren().add(err);
                });
            }
        }).start();
    }

    private void displayResults(String idea, List<SearchResult> results) {
        if (results.isEmpty()) {
            Label empty = new Label("No embeddings found or library is empty. Please import papers first.");
            empty.setStyle("-fx-text-fill: #f1fa8c; -fx-font-size: 14px;");
            resultsContainer.getChildren().add(empty);
            return;
        }

        // Top Summary Card
        double maxSimilarity = results.get(0).score * 100;
        VBox summaryCard = new VBox(8);
        summaryCard.setPadding(new Insets(14));
        summaryCard.setStyle("-fx-background-color: #282a36; -fx-background-radius: 8; -fx-border-color: #44475a; -fx-border-radius: 8;");

        Label summaryHeader = new Label(String.format("Highest Semantic Overlap: %.1f%%", maxSimilarity));
        summaryHeader.setStyle(maxSimilarity > 75 ? "-fx-text-fill: #ff5555; -fx-font-size: 16px; -fx-font-weight: bold;"
                : (maxSimilarity > 45 ? "-fx-text-fill: #ffb86c; -fx-font-size: 16px; -fx-font-weight: bold;"
                : "-fx-text-fill: #50fa7b; -fx-font-size: 16px; -fx-font-weight: bold;"));

        String verdictText = maxSimilarity > 75
                ? "⚠️ High overlap with existing work. Consider focusing on a more specific angle or niche dataset."
                : (maxSimilarity > 45
                ? "⚡ Moderate overlap. Key related work exists, but your idea appears to offer a novel methodology or target domain."
                : "🌟 High estimated novelty! Very few direct precedents found in your library.");

        Label verdict = new Label(verdictText);
        verdict.setWrapText(true);
        verdict.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13px;");

        summaryCard.getChildren().addAll(summaryHeader, verdict);
        resultsContainer.getChildren().add(summaryCard);

        // List of similar papers
        Label sectionTitle = new Label("Most Related Papers in Library:");
        sectionTitle.setStyle("-fx-text-fill: #8be9fd; -fx-font-size: 14px; -fx-font-weight: bold;");
        resultsContainer.getChildren().add(sectionTitle);

        for (SearchResult sr : results) {
            Publication pub = sr.entry.getPublication();
            if (pub == null) continue;

            VBox paperCard = new VBox(6);
            paperCard.setPadding(new Insets(10));
            paperCard.setStyle("-fx-background-color: #21222c; -fx-background-radius: 6; -fx-border-color: #383a59; -fx-border-radius: 6;");

            HBox cardHeader = new HBox(10);
            cardHeader.setAlignment(Pos.CENTER_LEFT);

            Label simLabel = new Label(String.format("%.1f%% Similar", sr.score * 100));
            simLabel.setStyle("-fx-background-color: #44475a; -fx-text-fill: #ff79c6; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4;");

            Label title = new Label(pub.getTitle());
            title.setStyle("-fx-text-fill: #f8f8f2; -fx-font-weight: bold; -fx-font-size: 13px;");
            HBox.setHgrow(title, Priority.ALWAYS);

            cardHeader.getChildren().addAll(simLabel, title);

            String absSnippet = pub.getAbstractText() != null ? pub.getAbstractText() : "No abstract available.";
            if (absSnippet.length() > 200) absSnippet = absSnippet.substring(0, 200) + "...";

            Label absLabel = new Label(absSnippet);
            absLabel.setWrapText(true);
            absLabel.setStyle("-fx-text-fill: #6272a4; -fx-font-size: 11px;");

            paperCard.getChildren().addAll(cardHeader, absLabel);
            resultsContainer.getChildren().add(paperCard);
        }
    }

    private static class SearchResult {
        final LibraryEntry entry;
        final double score;

        SearchResult(LibraryEntry entry, double score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
