package com.citeright.ui;

import com.citeright.formatter.*;
import com.citeright.model.CitationResult;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.service.LibraryService;
import com.citeright.service.SearchService;
import com.citeright.service.SemanticLibrarySearch;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.function.Consumer;

/**
 * Smart Citation Finder pane.
 *
 * Matches the target UI:
 *   - Sentence-based TextArea ("Enter a sentence from your paper...")
 *   - "Find Citations" button + Ctrl+Enter shortcut + Sort by combo
 *   - Empty hero state with feature pills
 *   - Custom result cards: number, title, match %, authors, year, citations,
 *     venue, one-click citation copy (APA / MLA / IEEE / Harvard), Open button
 */
public class SearchPane extends VBox {

    // ── Services ──────────────────────────────────────────────────────────────
    private final SearchService searchService;
    private final LibraryService libraryService;
    private Consumer<LibraryEntry> onSelect;
    private Runnable onLibraryUpdated;

    // ── UI state ──────────────────────────────────────────────────────────────
    private TextArea inputArea;
    private ComboBox<String> sortCombo;
    private Label statusLabel;
    private Button findBtn;
    private VBox resultsContainer;
    private ScrollPane resultsScroll;
    private StackPane centerArea;  // toggles between hero and results

    // ── Last raw results (for re-sort) ────────────────────────────────────────
    private List<CitationResult> lastResults;

    // ── Search mode ───────────────────────────────────────────────────────────
    private boolean libraryMode = false;  // false = web search, true = semantic library search
    private Button webModeBtn;
    private Button libraryModeBtn;

    public SearchPane(SearchService searchService, LibraryService libraryService,
                      Consumer<LibraryEntry> onSelect) {
        this.searchService  = searchService;
        this.libraryService = libraryService;
        this.onSelect       = onSelect;
        buildUI();
    }

    public void setOnLibraryUpdated(Runnable onLibraryUpdated) {
        this.onLibraryUpdated = onLibraryUpdated;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI Construction
    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI() {
        setSpacing(0);
        setStyle("-fx-background-color: #ffffff;");

        // ── TOP SEARCH STRIP ──────────────────────────────────────────────────
        VBox topStrip = buildTopStrip();
        topStrip.setStyle("-fx-background-color: #ffffff; " +
                "-fx-border-color: #e8e8ec; -fx-border-width: 0 0 1 0;");

        // ── CENTER (hero / results) ───────────────────────────────────────────
        centerArea = new StackPane();
        VBox.setVgrow(centerArea, Priority.ALWAYS);
        centerArea.setStyle("-fx-background-color: #f9f9fb;");

        // Results scroll
        resultsContainer = new VBox(0);
        resultsContainer.setStyle("-fx-background-color: #f9f9fb;");

        resultsScroll = new ScrollPane(resultsContainer);
        resultsScroll.setFitToWidth(true);
        resultsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultsScroll.setStyle("-fx-background: #f9f9fb; -fx-background-color: #f9f9fb; " +
                "-fx-border-color: transparent;");

        // Hero (visible initially)
        VBox hero = buildHero();
        centerArea.getChildren().add(hero);

        getChildren().addAll(topStrip, centerArea);
    }

    // ── TOP STRIP ─────────────────────────────────────────────────────────────

    private VBox buildTopStrip() {
        VBox strip = new VBox(0);
        strip.setPadding(new Insets(16, 24, 0, 24));

        // ── MODE TOGGLE ROW ──────────────────────────────────────────────────
        HBox modeRow = new HBox(0);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        modeRow.setPadding(new Insets(0, 0, 12, 0));

        String activeStyle   = "-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff; " +
                               "-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 7 20; " +
                               "-fx-cursor: hand; -fx-background-radius: 7 0 0 7;";
        String inactiveStyle = "-fx-background-color: #ededf5; -fx-text-fill: #5a5a7a; " +
                               "-fx-font-size: 11.5px; -fx-padding: 7 20; " +
                               "-fx-cursor: hand; -fx-background-radius: 0 7 7 0;";

        webModeBtn     = new Button("🌐  Web Search");
        libraryModeBtn = new Button("📚  My Library");

        webModeBtn.setStyle(activeStyle);
        libraryModeBtn.setStyle(inactiveStyle.replace("-fx-background-radius: 0 7 7 0", "-fx-background-radius: 0 7 7 0"));

        webModeBtn.setOnAction(e -> setSearchMode(false));
        libraryModeBtn.setOnAction(e -> setSearchMode(true));

        modeRow.getChildren().addAll(webModeBtn, libraryModeBtn);

        // "Enter a sentence from your paper…" hint
        Label hint = new Label("Enter a sentence from your paper to find supporting citations.");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #5a8adb;");
        hint.setPadding(new Insets(0, 0, 6, 0));

        // TextArea – the main input
        inputArea = new TextArea();
        inputArea.setPromptText(
                "e.g., \"Deep learning has significantly improved medical image diagnosis accuracy in recent years...\"");
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(3);
        inputArea.setMaxHeight(90);
        inputArea.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-text-fill: #1a1a2e; " +
                "-fx-control-inner-background: #ffffff; " +
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #d8d8e0; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-padding: 10 14 10 14;");

        // Ctrl+Enter to search
        inputArea.setOnKeyPressed(e -> {
            KeyCodeCombination ctrlEnter =
                    new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN);
            if (ctrlEnter.match(e)) {
                executeSearch(inputArea.getText().trim());
                e.consume();
            }
        });

        // ── ACTION ROW ────────────────────────────────────────────────────────
        HBox actionRow = new HBox(14);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(12, 0, 14, 0));

        findBtn = new Button("Find Citations");
        findBtn.setStyle(
                "-fx-background-color: #111111; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 10 26; " +
                "-fx-background-radius: 7; " +
                "-fx-cursor: hand;");
        findBtn.setOnMouseEntered(e -> findBtn.setStyle(
                findBtn.getStyle().replace("#111111", "#2a2a2a")));
        findBtn.setOnMouseExited(e -> findBtn.setStyle(
                findBtn.getStyle().replace("#2a2a2a", "#111111")));
        findBtn.setOnAction(e -> executeSearch(inputArea.getText().trim()));

        // Shortcut hint
        Label shortcutLabel = new Label("Ctrl + Enter");
        shortcutLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11px;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Sort by
        Label sortLabel = new Label("Sort by:");
        sortLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Relevance", "Year (newest)", "Citations", "Year (oldest)");
        sortCombo.setValue("Relevance");
        sortCombo.setStyle("-fx-font-size: 12px; -fx-background-color: #ffffff; " +
                "-fx-border-color: #d0d0d8; -fx-border-radius: 6;");
        sortCombo.setOnAction(e -> applySortAndRender());

        // Status label (reuse slot in action row — inline)
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px;");

        actionRow.getChildren().addAll(findBtn, shortcutLabel, statusLabel, spacer, sortLabel, sortCombo);

        strip.getChildren().addAll(modeRow, hint, inputArea, actionRow);
        return strip;
    }

    /** Switches between Web Search and My Library modes. */
    private void setSearchMode(boolean libraryMode) {
        this.libraryMode = libraryMode;
        String activeStyle   = "-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff; " +
                               "-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 7 20; -fx-cursor: hand;";
        String inactiveStyle = "-fx-background-color: #ededf5; -fx-text-fill: #5a5a7a; " +
                               "-fx-font-size: 11.5px; -fx-padding: 7 20; -fx-cursor: hand;";
        if (libraryMode) {
            webModeBtn.setStyle(inactiveStyle + "-fx-background-radius: 7 0 0 7;");
            libraryModeBtn.setStyle(activeStyle + "-fx-background-radius: 0 7 7 0;");
        } else {
            webModeBtn.setStyle(activeStyle + "-fx-background-radius: 7 0 0 7;");
            libraryModeBtn.setStyle(inactiveStyle + "-fx-background-radius: 0 7 7 0;");
        }
        // Reset to hero
        centerArea.getChildren().setAll(buildHero());
        setStatus("", "#8888aa");
    }

    // ── HERO EMPTY STATE ──────────────────────────────────────────────────────

    private VBox buildHero() {
        VBox hero = new VBox(18);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(60, 40, 60, 40));
        hero.setMaxWidth(560);
        StackPane.setAlignment(hero, Pos.CENTER);

        Label mainTitle = new Label("Find citations that back your claims.");
        mainTitle.setStyle(
                "-fx-font-size: 22px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: #1a1a2e;");
        mainTitle.setTextAlignment(TextAlignment.CENTER);
        mainTitle.setWrapText(true);

        Label subText = new Label(
                "Write a sentence from your research paper above.\n" +
                "CiteRight searches 250M+ academic papers to find real,\n" +
                "relevant references — ranked by strength.");
        subText.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #6060a0; -fx-line-spacing: 2;");
        subText.setTextAlignment(TextAlignment.CENTER);
        subText.setWrapText(true);

        // Feature pills row
        HBox pills = new HBox(10);
        pills.setAlignment(Pos.CENTER);
        for (String label : new String[]{
                "· Smart Search", "· Ranked Results", "· One-Click Copy", "· Verified Papers"}) {
            Label pill = new Label(label);
            pill.setStyle(
                    "-fx-font-size: 11.5px; " +
                    "-fx-text-fill: #444466; " +
                    "-fx-background-color: #ededf5; " +
                    "-fx-padding: 6 14; " +
                    "-fx-background-radius: 20;");
            pills.getChildren().add(pill);
        }

        hero.getChildren().addAll(mainTitle, subText, pills);
        return hero;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Search Execution
    // ─────────────────────────────────────────────────────────────────────────

    private void executeSearch(String query) {
        if (query == null || query.isEmpty()) return;

        if (libraryMode) {
            executeLibrarySearch(query);
        } else {
            executeWebSearch(query);
        }
    }

    /** Semantic search over the user's saved library — instant, offline. */
    private void executeLibrarySearch(String query) {
        // Prompt user to enable advanced Local Semantic AI if they haven't configured it yet
        LocalSemanticAIPrompt.showPromptIfNeeded();

        setStatus("Searching your library…", "#8888aa");
        findBtn_setDisabled(true);

        new Thread(() -> {
            try {
                java.util.List<LibraryEntry> allEntries = libraryService.getAllActive();
                SemanticLibrarySearch searcher = new SemanticLibrarySearch();
                java.util.List<SemanticLibrarySearch.ScoredEntry> scored = searcher.search(query, allEntries);

                Platform.runLater(() -> {
                    renderLibraryResults(scored, query);
                    setStatus("Found " + scored.size() + " matches in your library", "#5a5a8a");
                    findBtn_setDisabled(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("❌ Library search failed: " + ex.getMessage(), "#cc3333");
                    findBtn_setDisabled(false);
                });
            }
        }).start();
    }

    /** Renders semantic library search results as clean cards. */
    private void renderLibraryResults(java.util.List<SemanticLibrarySearch.ScoredEntry> results, String query) {
        resultsContainer.getChildren().clear();

        if (results.isEmpty()) {
            Label empty = new Label("No matches found in your library. Try adding more papers or use Web Search.");
            empty.setStyle("-fx-text-fill: #888; -fx-font-size: 12px; -fx-padding: 40 24 0 24;");
            empty.setWrapText(true);
            resultsContainer.getChildren().add(empty);
        } else {
            HBox countRow = new HBox();
            countRow.setPadding(new Insets(12, 24, 8, 24));
            Label countLabel = new Label("📚 " + results.size() + " papers from your library match this query");
            countLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #5a3fbf; -fx-font-weight: bold;");
            countRow.getChildren().add(countLabel);
            resultsContainer.getChildren().add(countRow);

            for (int i = 0; i < results.size(); i++) {
                resultsContainer.getChildren().add(buildLibraryResultCard(results.get(i), i + 1));
            }
        }
        centerArea.getChildren().setAll(resultsScroll);
    }

    /** A compact result card for library semantic search results. */
    private VBox buildLibraryResultCard(SemanticLibrarySearch.ScoredEntry scored, int index) {
        LibraryEntry entry = scored.entry();
        Publication pub = entry.getPublication();

        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 24, 12, 24));
        card.setStyle("-fx-border-color: #e8e8ee; -fx-border-width: 0 0 1 0;");

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: #f5f3ff; -fx-border-color: #e8e8ee; -fx-border-width: 0 0 1 0; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-border-color: #e8e8ee; -fx-border-width: 0 0 1 0;"));

        // Click to open in detail panel
        card.setOnMouseClicked(e -> {
            if (onSelect != null) onSelect.accept(entry);
        });

        // Header: index + match score badge
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label numLabel = new Label(index + ".");
        numLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px; -fx-min-width: 20;");

        String pct = scored.scorePercent();
        Label matchBadge = new Label("📚 " + pct + " match");
        matchBadge.setStyle(
                "-fx-background-color: #f0edff; -fx-text-fill: #5a3fbf; " +
                "-fx-font-size: 9.5px; -fx-font-weight: bold; " +
                "-fx-padding: 2 8; -fx-background-radius: 12;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Year chip
        if (pub != null && pub.getYear() > 0) {
            Label yearChip = new Label(String.valueOf(pub.getYear()));
            yearChip.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
            header.getChildren().addAll(numLabel, matchBadge, spacer, yearChip);
        } else {
            header.getChildren().addAll(numLabel, matchBadge, spacer);
        }

        // Title
        String title = pub != null && pub.getTitle() != null ? pub.getTitle() : "Untitled";
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        titleLabel.setWrapText(true);

        // Authors
        String authors = pub != null ? pub.getAuthorsFormatted() : "";
        Label authorsLabel = new Label(authors.isEmpty() ? "—" : authors);
        authorsLabel.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #4a6cf7;");
        authorsLabel.setWrapText(true);

        card.getChildren().addAll(header, titleLabel, authorsLabel);
        return card;
    }

    /** Original web search execution. */
    private void executeWebSearch(String query) {
        setStatus("Searching…", "#8888aa");
        findBtn_setDisabled(true);

        new Thread(() -> {
            try {
                List<CitationResult> results = searchService.search(query, 40);
                Platform.runLater(() -> {
                    lastResults = results;
                    applySortAndRender();
                    setStatus("Showing " + results.size() + " results", "#5a5a8a");
                    findBtn_setDisabled(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("❌ Search failed: " + ex.getMessage(), "#cc3333");
                    findBtn_setDisabled(false);
                });
            }
        }).start();
    }

    /** Applies the selected sort, then renders the cards. */
    private void applySortAndRender() {
        if (lastResults == null || lastResults.isEmpty()) return;

        List<CitationResult> sorted = new java.util.ArrayList<>(lastResults);
        switch (sortCombo.getValue()) {
            case "Year (newest)" -> sorted.sort((a, b) ->
                    Integer.compare(
                            b.getPublication().getYear(),
                            a.getPublication().getYear()));
            case "Year (oldest)" -> sorted.sort((a, b) ->
                    Integer.compare(
                            a.getPublication().getYear(),
                            b.getPublication().getYear()));
            case "Citations" -> sorted.sort((a, b) ->
                    Integer.compare(
                            b.getPublication().getCitationCount(),
                            a.getPublication().getCitationCount()));
            default -> sorted.sort(CitationResult::compareTo); // Relevance
        }
        renderResults(sorted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Result Card Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private void renderResults(List<CitationResult> results) {
        resultsContainer.getChildren().clear();

        if (results.isEmpty()) {
            Label empty = new Label("No results found. Try a different sentence.");
            empty.setStyle("-fx-text-fill: #888; -fx-font-size: 12px; -fx-padding: 40 0 0 24;");
            resultsContainer.getChildren().add(empty);
        } else {
            // "Showing N results" row
            HBox countRow = new HBox();
            countRow.setPadding(new Insets(12, 24, 8, 24));
            Label countLabel = new Label("Showing " + results.size() + " results");
            countLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #8888aa;");
            countRow.getChildren().add(countLabel);
            resultsContainer.getChildren().add(countRow);

            for (int i = 0; i < results.size(); i++) {
                resultsContainer.getChildren().add(buildResultCard(results.get(i), i + 1));
            }
        }

        // Switch to results view
        centerArea.getChildren().setAll(resultsScroll);
    }

    /**
     * Builds one result card matching the screenshot:
     *   [N]  Title                                   [XX% match]
     *        Authors
     *        Year · Citations · Venue
     *        ─────────────────────────────────────────────────
     *        Cite as:  [APA] [MLA] [IEEE] [Harvard]     [Open →]
     */
    private VBox buildResultCard(CitationResult result, int number) {
        Publication pub = result.getPublication();

        VBox card = new VBox(0);
        card.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #eeeef4; " +
                "-fx-border-width: 0 0 1 0;");
        card.setPadding(new Insets(16, 24, 16, 24));

        // ── ROW 1: number + title + match badge ───────────────────────────────
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.TOP_LEFT);

        // Number
        Label numLabel = new Label(number + ".");
        numLabel.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-text-fill: #555; " +
                "-fx-min-width: 26; " +
                "-fx-font-weight: bold;");

        // Title
        Label titleLabel = new Label(pub.getTitle() != null ? pub.getTitle() : "Untitled");
        titleLabel.setStyle(
                "-fx-font-size: 13.5px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: #1a1a2e;");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Match badge (only if score > 0)
        if (result.getRelevanceScore() > 0) {
            Label badge = buildMatchBadge(result.getRelevanceScore());
            titleRow.getChildren().addAll(numLabel, titleLabel, badge);
        } else {
            titleRow.getChildren().addAll(numLabel, titleLabel);
        }

        // ── ROW 2: Authors ─────────────────────────────────────────────────────
        Label authorsLabel = new Label(pub.getAuthorsFormatted());
        authorsLabel.setStyle(
                "-fx-font-size: 11.5px; " +
                "-fx-text-fill: #5a6cf5;");  // blue like the screenshot
        authorsLabel.setWrapText(true);
        authorsLabel.setPadding(new Insets(4, 0, 0, 36));

        // ── ROW 3: Year · Citations · Venue ───────────────────────────────────
        HBox metaRow = new HBox(10);
        metaRow.setPadding(new Insets(2, 0, 0, 36));
        metaRow.setAlignment(Pos.CENTER_LEFT);

        if (pub.getYear() > 0) {
            metaRow.getChildren().add(metaChip(String.valueOf(pub.getYear()), "#555555"));
        }
        if (pub.getCitationCount() > 0) {
            metaRow.getChildren().add(
                    metaChip(pub.getCitationCount() + " citations", "#555555"));
        }
        if (pub.getVenue() != null && !pub.getVenue().isEmpty()) {
            metaRow.getChildren().add(metaChip(pub.getVenue(), "#555555"));
        }

        // ── ACTION ROW: Cite as + Open ─────────────────────────────────────────
        HBox actionRow = buildActionRow(result);
        actionRow.setPadding(new Insets(10, 0, 0, 36));

        // ── EVIDENCE BOX ───────────────────────────────────────────────────────
        VBox evidenceBox = new VBox(5);
        evidenceBox.setPadding(new Insets(10, 0, 0, 36));
        evidenceBox.setVisible(false);
        evidenceBox.setManaged(false);

        Button extractBtn = new Button("✨ Extract Evidence (AI)");
        extractBtn.setStyle(
                "-fx-background-color: #f0edff; " +
                "-fx-text-fill: #5a6cf5; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 10; " +
                "-fx-background-radius: 12; " +
                "-fx-cursor: hand;");
        extractBtn.setOnAction(e -> {
            extractBtn.setDisable(true);
            extractBtn.setText("⏳ Extracting...");
            new Thread(() -> {
                String claim = inputArea.getText().trim();
                com.citeright.ai.GeminiAIService ai = new com.citeright.ai.GeminiAIService();
                String evidence = ai.extractEvidence(claim, pub);
                javafx.application.Platform.runLater(() -> {
                    extractBtn.setVisible(false);
                    extractBtn.setManaged(false);
                    evidenceBox.setVisible(true);
                    evidenceBox.setManaged(true);
                    Label evTitle = new Label("✨ AI Extracted Evidence:");
                    evTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #5a6cf5;");
                    Label evText = new Label(evidence != null ? evidence : "No evidence found.");
                    evText.setStyle("-fx-font-size: 12px; -fx-text-fill: #333333; -fx-background-color: #f9f9fc; -fx-padding: 8; -fx-border-color: #eeeef4; -fx-border-radius: 4; -fx-background-radius: 4;");
                    evText.setWrapText(true);
                    evidenceBox.getChildren().addAll(evTitle, evText);
                });
            }).start();
        });
        actionRow.getChildren().add(1, extractBtn); // Add after "Cite as:"

        card.getChildren().addAll(titleRow, authorsLabel, metaRow, actionRow, evidenceBox);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: #f5f5ff; " +
                "-fx-border-color: #eeeef4; " +
                "-fx-border-width: 0 0 1 0;"));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #eeeef4; " +
                "-fx-border-width: 0 0 1 0;"));

        return card;
    }

    /** Builds the colored match percentage badge. */
    private Label buildMatchBadge(double score) {
        String pct = String.format("%.0f%% match", score);
        String bg;
        String fg;
        if (score >= 70) {
            bg = "#fff3e0"; fg = "#e65100";  // orange (strong)
        } else if (score >= 50) {
            bg = "#e8f5e9"; fg = "#2e7d32";  // green (good)
        } else {
            bg = "#f3f3f3"; fg = "#666666";  // grey (weak)
        }
        Label badge = new Label(pct);
        badge.setStyle(
                "-fx-background-color: " + bg + "; " +
                "-fx-text-fill: " + fg + "; " +
                "-fx-font-size: 10.5px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 3 8; " +
                "-fx-background-radius: 10;");
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    /** Small inline metadata chip (no background). */
    private Label metaChip(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: " + color + ";");
        return l;
    }

    /**
     * Builds the bottom action row: "Cite as: [APA] [MLA] [IEEE] [Harvard]  [Open →]"
     */
    private HBox buildActionRow(CitationResult result) {
        Publication pub = result.getPublication();

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label citeAsLabel = new Label("Cite as:");
        citeAsLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #888888;");

        row.getChildren().add(citeAsLabel);

        String[] formats = {"APA", "MLA", "IEEE", "Harvard"};
        for (String fmt : formats) {
            row.getChildren().add(buildCiteButton(fmt, pub));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        Button openBtn = new Button("Open \u2192");
        openBtn.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #1a1a2e; " +
                "-fx-font-size: 11.5px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 5 14; " +
                "-fx-border-color: #ccccdd; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;");
        openBtn.setOnMouseEntered(e -> openBtn.setStyle(openBtn.getStyle().replace("#ffffff", "#f0f0ff").replace("#ccccdd", "#8888cc")));
        openBtn.setOnMouseExited(e -> openBtn.setStyle(openBtn.getStyle().replace("#f0f0ff", "#ffffff").replace("#8888cc", "#ccccdd")));
        openBtn.setOnAction(e -> handleOpen(result));
        row.getChildren().add(openBtn);

        return row;
    }

    /** One-click citation copy button (APA / MLA / IEEE / Harvard). */
    private Button buildCiteButton(String fmt, Publication pub) {
        Button btn = new Button(fmt);
        String normal =
                "-fx-background-color: #f2f2f8; " +
                "-fx-text-fill: #333355; " +
                "-fx-font-size: 11px; " +
                "-fx-padding: 4 11; " +
                "-fx-background-radius: 12; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: transparent;";
        String hover =
                "-fx-background-color: #e4e4f8; " +
                "-fx-text-fill: #222244; " +
                "-fx-font-size: 11px; " +
                "-fx-padding: 4 11; " +
                "-fx-background-radius: 12; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: transparent;";
        String copied =
                "-fx-background-color: #d4edda; " +
                "-fx-text-fill: #155724; " +
                "-fx-font-size: 11px; " +
                "-fx-padding: 4 11; " +
                "-fx-background-radius: 12; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: transparent;";

        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getText().equals("✓") ? copied : normal));

        btn.setOnAction(e -> {
            String citation = generateCitation(fmt, pub);
            copyToClipboard(citation);
            String original = btn.getText();
            btn.setText("✓");
            btn.setStyle(copied);
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    btn.setText(original);
                    btn.setStyle(normal);
                });
            }).start();
        });

        return btn;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the detail panel for the search result without saving it automatically. */
    private void handleOpen(CitationResult result) {
        Publication pub = result.getPublication();
        if (pub == null) return;

        LibraryEntry entry = new LibraryEntry(pub);
        if (onSelect != null) {
            onSelect.accept(entry);
        }
    }

    private String generateCitation(String format, Publication pub) {
        CitationFormatter formatter = switch (format) {
            case "MLA"     -> new MLAFormatter();
            case "IEEE"    -> new IEEEFormatter();
            case "Harvard" -> new HarvardFormatter();
            default        -> new APAFormatter();
        };
        return formatter.format(pub);
    }

    private void copyToClipboard(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + color + ";");
    }

    /** Disables / re-enables the Find Citations button. */
    private void findBtn_setDisabled(boolean disabled) {
        Platform.runLater(() -> {
            if (findBtn != null) findBtn.setDisable(disabled);
        });
    }
}
