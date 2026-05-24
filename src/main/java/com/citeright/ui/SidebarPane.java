package com.citeright.ui;

import com.citeright.service.LibraryService;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
import javafx.stage.FileChooser;
import java.io.File;
import com.citeright.service.ZoteroImportService;

/**
 * Clean, minimal sidebar — grouped into just 3 clear sections.
 */
public class SidebarPane extends VBox {

    private final LibraryService libraryService;
    private Consumer<String> onNavigate;
    private Runnable onImport;
    private Runnable onExport;
    private Button activeNavButton;
    private Label statsLabel;

    public SidebarPane(LibraryService libraryService) {
        this.libraryService = libraryService;
        buildUI();
    }

    public void setOnNavigate(Consumer<String> handler) { this.onNavigate = handler; }
    public void setOnImport(Runnable handler) { this.onImport = handler; }
    public void setOnExport(Runnable handler) { this.onExport = handler; }

    private void buildUI() {
        setPrefWidth(200);
        setMinWidth(180);
        setMaxWidth(210);
        setStyle("-fx-background-color: #1e1e2e;");

        // === Fixed Header ===
        VBox header = new VBox(4);
        header.setPadding(new Insets(16, 14, 10, 14));
        header.setStyle("-fx-background-color: #1e1e2e;");

        Label brand = new Label("📚 CiteRight");
        brand.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 17px; -fx-font-weight: bold;");

        Button addNewBtn = new Button("＋  Add Reference");
        addNewBtn.setMaxWidth(Double.MAX_VALUE);
        addNewBtn.setAlignment(Pos.CENTER);
        addNewBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 12px; " +
                "-fx-padding: 8 10; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold;");
        addNewBtn.setOnMouseEntered(e -> addNewBtn.setStyle(addNewBtn.getStyle().replace("#4a6cf7", "#5b7df8")));
        addNewBtn.setOnMouseExited(e -> addNewBtn.setStyle(addNewBtn.getStyle().replace("#5b7df8", "#4a6cf7")));
        addNewBtn.setOnAction(e -> navigate("add_entry", null));
        addNewBtn.setTooltip(new Tooltip("Add a paper by DOI, ArXiv ID, or manually"));

        header.getChildren().addAll(brand, addNewBtn);

        // === Scrollable Content ===
        VBox navContent = new VBox(1);
        navContent.setPadding(new Insets(4, 8, 14, 8));

        // ---- Main Nav ----
        Button searchBtn = createNavButton("🔍  Search Papers", false);
        searchBtn.setOnAction(e -> navigate("search", searchBtn));
        searchBtn.setTooltip(new Tooltip("Search millions of papers across multiple databases"));

        Button allRefsBtn = createNavButton("📄  My Library", false);
        allRefsBtn.setOnAction(e -> navigate("all_references", allRefsBtn));
        allRefsBtn.setTooltip(new Tooltip("View all saved references"));
        activeNavButton = allRefsBtn;
        setButtonStyle(allRefsBtn, true);

        Separator sep1 = createSeparator();
        Label filtersLabel = createSectionLabel("QUICK FILTERS");

        Button recentBtn = createNavButton("🕐  Recently Added", false);
        recentBtn.setOnAction(e -> navigate("recently_added", recentBtn));

        Button favoritesBtn = createNavButton("⭐  Favorites", false);
        favoritesBtn.setOnAction(e -> navigate("favorites", favoritesBtn));

        Button unsortedBtn = createNavButton("📂  Unsorted", false);
        unsortedBtn.setOnAction(e -> navigate("unsorted", unsortedBtn));

        Button trashBtn = createNavButton("🗑  Trash", false);
        trashBtn.setOnAction(e -> navigate("trash", trashBtn));

        Separator sep2 = createSeparator();
        Label visualizeLabel = createSectionLabel("VISUALIZE");

        Button graphBtn = createNavButton("🕸  Paper Graph", false);
        graphBtn.setOnAction(e -> navigate("paper_graph", graphBtn));
        graphBtn.setTooltip(new Tooltip("See how your papers connect"));

        Separator sepAI = createSeparator();
        Label aiLabel = createSectionLabel("AI ASSISTANT");

        Button aiChatBtn = createNavButton("🤖  AI Chat", false);
        aiChatBtn.setOnAction(e -> navigate("ai_chat", aiChatBtn));
        aiChatBtn.setTooltip(new Tooltip("Chat with your library using AI"));

        Button statsBtn = createNavButton("📊  My Stats", false);
        statsBtn.setOnAction(e -> navigate("stats", statsBtn));
        statsBtn.setTooltip(new Tooltip("View reading statistics and insights"));

        Separator sep3 = createSeparator();
        Label organizeLabel = createSectionLabel("ORGANIZE");

        Button newCollBtn = createSmallButton("＋  New Collection");
        newCollBtn.setOnAction(e -> navigate("create_collection", null));
        newCollBtn.setTooltip(new Tooltip("Create a folder to organize your papers"));

        Button newSmartCollBtn = createSmallButton("💡  New Smart Collection");
        newSmartCollBtn.setOnAction(e -> navigate("create_smart_collection", null));
        newSmartCollBtn.setTooltip(new Tooltip("Create a dynamic folder based on rules"));

        Separator sep4 = createSeparator();
        Label toolsLabel = createSectionLabel("IMPORT / EXPORT");

        Button importBtn = createNavButton("📥  Import File", false);
        importBtn.setOnAction(e -> {
            flashButton(importBtn);
            if (onImport != null) onImport.run();
        });
        importBtn.setTooltip(new Tooltip("Import references from BibTeX (.bib) or RIS (.ris) files"));

        Button zoteroBtn = createNavButton("🗄  Import Zotero DB", false);
        zoteroBtn.setOnAction(e -> {
            flashButton(zoteroBtn);
            FileChooser fc = new FileChooser();
            fc.setTitle("Select zotero.sqlite");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zotero DB", "*.sqlite"));
            File file = fc.showOpenDialog(getScene().getWindow());
            if (file != null) {
                ZoteroImportService zoteroService = new ZoteroImportService(libraryService);
                javafx.concurrent.Task<Integer> task = zoteroService.importZoteroDatabase(file.getAbsolutePath());
                task.setOnSucceeded(evt -> {
                    System.out.println("Imported " + task.getValue() + " papers from Zotero");
                    refreshStats();
                    if (onNavigate != null) onNavigate.accept("LIBRARY"); // refresh view
                });
                task.setOnFailed(evt -> {
                    System.err.println("Zotero import failed.");
                });
                new Thread(task).start();
            }
        });
        zoteroBtn.setTooltip(new Tooltip("Directly import from your local zotero.sqlite file"));

        Button exportBtn = createNavButton("📤  Export Library", false);
        exportBtn.setOnAction(e -> {
            flashButton(exportBtn);
            if (onExport != null) onExport.run();
        });
        exportBtn.setTooltip(new Tooltip("Export your library as BibTeX, RIS, or CSV"));

        // Stats
        statsLabel = new Label("");
        statsLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 9px; -fx-padding: 16 6 4 6;");
        refreshStats();

        // Shortcut hint
        Label shortcutHint = new Label("⌘  Ctrl+K  Command Palette");
        shortcutHint.setStyle("-fx-text-fill: #444466; -fx-font-size: 9px; -fx-padding: 4 6;");

        navContent.getChildren().addAll(
                searchBtn, allRefsBtn,
                sep1, filtersLabel, recentBtn, favoritesBtn, unsortedBtn, trashBtn,
                sep2, visualizeLabel, graphBtn,
                sepAI, aiLabel, aiChatBtn,
                sep3, organizeLabel, newCollBtn, newSmartCollBtn,
                sep4, toolsLabel, importBtn, zoteroBtn, exportBtn,
                statsBtn, statsLabel, shortcutHint
        );

        // ScrollPane
        ScrollPane scrollPane = new ScrollPane(navContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Thin scrollbar styling
        scrollPane.skinProperty().addListener((obs, o, n) -> {
            if (n != null) {
                scrollPane.lookupAll(".scroll-bar").forEach(nd -> nd.setStyle("-fx-background-color: #1e1e2e; -fx-pref-width: 5;"));
                scrollPane.lookupAll(".thumb").forEach(nd -> nd.setStyle("-fx-background-color: #444; -fx-background-radius: 3;"));
                scrollPane.lookupAll(".track").forEach(nd -> nd.setStyle("-fx-background-color: #1e1e2e;"));
                scrollPane.lookupAll(".increment-button, .decrement-button").forEach(nd -> nd.setStyle("-fx-padding: 0; -fx-pref-height: 0;"));
            }
        });

        getChildren().addAll(header, scrollPane);
    }

    private void navigate(String view, Button btn) {
        if (btn != null) {
            if (activeNavButton != null) setButtonStyle(activeNavButton, false);
            setButtonStyle(btn, true);
            activeNavButton = btn;
        }
        if (onNavigate != null) onNavigate.accept(view);
    }

    private void setButtonStyle(Button btn, boolean active) {
        String base = "-fx-font-size: 12px; -fx-padding: 7 12; -fx-background-radius: 6; -fx-cursor: hand;";
        if (active) {
            btn.setStyle(base + "-fx-background-color: #333350; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        } else {
            btn.setStyle(base + "-fx-background-color: transparent; -fx-text-fill: #a0a0b8; -fx-font-weight: normal;");
        }
    }

    private Button createNavButton(String text, boolean active) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        setButtonStyle(btn, active);
        btn.setOnMouseEntered(e -> { if (btn != activeNavButton) btn.setStyle(
                "-fx-font-size: 12px; -fx-padding: 7 12; -fx-background-radius: 6; -fx-cursor: hand;" +
                "-fx-background-color: #2a2a40; -fx-text-fill: #d0d0e0; -fx-font-weight: normal;"); });
        btn.setOnMouseExited(e -> { if (btn != activeNavButton) setButtonStyle(btn, false); });
        return btn;
    }

    private Button createSmallButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6a6a8a; -fx-font-size: 11px; " +
                "-fx-padding: 5 12; -fx-background-radius: 5; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #2a2a40; -fx-text-fill: #b0b0c8; -fx-font-size: 11px; " +
                "-fx-padding: 5 12; -fx-background-radius: 5; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6a6a8a; -fx-font-size: 11px; " +
                "-fx-padding: 5 12; -fx-background-radius: 5; -fx-cursor: hand;"));
        return btn;
    }

    private Label createSectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 12 8 3 8; -fx-letter-spacing: 1;");
        return l;
    }

    private Separator createSeparator() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #2a2a3e;");
        VBox.setMargin(s, new Insets(3, 4, 1, 4));
        return s;
    }

    public void refreshStats() {
        Platform.runLater(() -> statsLabel.setText("📄  " + libraryService.getLibraryCount() + " papers saved"));
    }

    private void flashButton(Button btn) {
        setButtonStyle(btn, true);
        new Thread(() -> {
            try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> { if (btn != activeNavButton) setButtonStyle(btn, false); });
        }).start();
    }
}
