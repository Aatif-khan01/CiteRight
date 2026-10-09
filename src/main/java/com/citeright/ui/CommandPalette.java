package com.citeright.ui;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.formatter.APAFormatter;
import com.citeright.formatter.IEEEFormatter;
import com.citeright.formatter.BibTeXFormatter;
import com.citeright.service.LibraryService;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * VS Code / Superhuman-style Command Palette for CiteRight.
 *
 * Activated via Ctrl+K — gives researchers instant keyboard-first access to
 * every feature: searching papers, copying citations, navigating views,
 * and opening collections, all without touching the mouse.
 *
 * 100% local. Zero dependencies.
 */
public class CommandPalette extends StackPane {

    // ── Callbacks ────────────────────────────────────────────────────────────
    private Consumer<String> onNavigate;
    private Consumer<LibraryEntry> onSelectEntry;
    private Runnable onDismiss;

    private final LibraryService libraryService;
    private final TextField searchField;
    private final VBox resultsContainer;
    private final VBox paletteCard;

    // All available commands (built once, filtered on each keystroke)
    private final List<PaletteItem> allItems = new ArrayList<>();
    private int selectedIndex = 0;

    public CommandPalette(LibraryService libraryService) {
        this.libraryService = libraryService;

        // ── Full-screen overlay ──────────────────────────────────────────────
        setStyle("-fx-background-color: rgba(10, 10, 20, 0.55);");
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(80, 0, 0, 0));
        setVisible(false);
        setManaged(false);

        // Clicking the backdrop dismisses the palette
        setOnMouseClicked(e -> {
            if (e.getTarget() == this) dismiss();
        });

        // ── Palette card ─────────────────────────────────────────────────────
        paletteCard = new VBox(0);
        paletteCard.setMaxWidth(560);
        paletteCard.setMinWidth(480);
        paletteCard.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 14; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 30, 0.1, 0, 6);");

        // ── Search field ─────────────────────────────────────────────────────
        searchField = new TextField();
        searchField.setPromptText("Type a command, paper title, or action…");
        searchField.setStyle(
                "-fx-font-size: 15px; " +
                "-fx-padding: 16 20; " +
                "-fx-background-color: transparent; " +
                "-fx-border-color: #e0e0e8; " +
                "-fx-border-width: 0 0 1 0; " +
                "-fx-text-fill: #1a1a2e; " +
                "-fx-prompt-text-fill: #aaaacc;");

        searchField.textProperty().addListener((obs, o, n) -> filterAndRender(n));

        // Keyboard navigation inside the palette
        searchField.setOnKeyPressed(this::handleKeyPress);

        // ── Results container ────────────────────────────────────────────────
        resultsContainer = new VBox(0);
        resultsContainer.setStyle("-fx-padding: 6 0;");

        ScrollPane scrollPane = new ScrollPane(resultsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMaxHeight(400);
        scrollPane.setStyle("-fx-background: #ffffff; -fx-background-color: #ffffff; " +
                            "-fx-border-color: transparent;");

        // ── Footer hint ──────────────────────────────────────────────────────
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 16, 10, 16));
        footer.setStyle("-fx-border-color: #e8e8ee; -fx-border-width: 1 0 0 0;");

        Label hintNav = createHintLabel("↑↓ Navigate");
        Label hintEnter = createHintLabel("⏎ Select");
        Label hintEsc = createHintLabel("Esc Close");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label brand = new Label("⌘K  CiteRight");
        brand.setStyle("-fx-text-fill: #c0c0d0; -fx-font-size: 10px;");

        footer.getChildren().addAll(hintNav, hintEnter, hintEsc, spacer, brand);

        paletteCard.getChildren().addAll(searchField, scrollPane, footer);
        getChildren().add(paletteCard);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void setOnNavigate(Consumer<String> handler) { this.onNavigate = handler; }
    public void setOnSelectEntry(Consumer<LibraryEntry> handler) { this.onSelectEntry = handler; }
    public void setOnDismiss(Runnable handler) { this.onDismiss = handler; }

    /** Opens the palette with a fade-in animation. */
    public void show() {
        rebuildAllItems();
        setVisible(true);
        setManaged(true);
        setOpacity(0);
        searchField.clear();
        filterAndRender("");

        FadeTransition fade = new FadeTransition(Duration.millis(120), this);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        // Animate card sliding down
        paletteCard.setTranslateY(-18);
        TranslateTransition slide = new TranslateTransition(Duration.millis(150), paletteCard);
        slide.setFromY(-18);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        slide.play();

        searchField.requestFocus();
    }

    /** Closes the palette. */
    public void dismiss() {
        FadeTransition fade = new FadeTransition(Duration.millis(100), this);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            setVisible(false);
            setManaged(false);
        });
        fade.play();
        if (onDismiss != null) onDismiss.run();
    }

    public boolean isShowing() { return isVisible(); }

    // ── Build all items ──────────────────────────────────────────────────────

    private void rebuildAllItems() {
        allItems.clear();

        // Navigation commands
        allItems.add(new PaletteItem("🔍", "Search Papers (Web)", "Navigate to web search", () -> doNavigate("search")));
        allItems.add(new PaletteItem("📄", "My Library", "View all saved references", () -> doNavigate("all_references")));
        allItems.add(new PaletteItem("🕐", "Recently Added", "Papers added in the last 7 days", () -> doNavigate("recently_added")));
        allItems.add(new PaletteItem("⭐", "Favorites", "View starred papers", () -> doNavigate("favorites")));
        allItems.add(new PaletteItem("📂", "Unsorted", "Papers without a collection", () -> doNavigate("unsorted")));
        allItems.add(new PaletteItem("🗑", "Trash", "View deleted papers", () -> doNavigate("trash")));
        allItems.add(new PaletteItem("🕸", "Paper Graph", "Visualize connections between your papers", () -> doNavigate("paper_graph")));
        allItems.add(new PaletteItem("🤖", "AI Chat", "Chat with your library using AI", () -> doNavigate("ai_chat")));
        allItems.add(new PaletteItem("＋", "Add Reference", "Add a paper by DOI, ArXiv ID, or manually", () -> doNavigate("add_entry")));
        allItems.add(new PaletteItem("📥", "Import File", "Import BibTeX or RIS", () -> doNavigate("import")));
        allItems.add(new PaletteItem("📤", "Export Library", "Export as BibTeX, RIS, or CSV", () -> doNavigate("export")));

        // Library papers — dynamic
        try {
            List<LibraryEntry> entries = libraryService.getAllActive();
            for (LibraryEntry entry : entries) {
                Publication pub = entry.getPublication();
                if (pub == null) continue;
                String title = pub.getTitle() != null ? pub.getTitle() : "Untitled";
                String authors = pub.getAuthorsShort();
                String year = pub.getYear() > 0 ? " (" + pub.getYear() + ")" : "";

                // Open paper
                allItems.add(new PaletteItem("📝", title, authors + year, () -> doSelectEntry(entry)));

                // Copy APA citation
                allItems.add(new PaletteItem("📋", "Copy APA — " + truncate(title, 40),
                        "Copy APA citation to clipboard",
                        () -> copyToClipboard(new APAFormatter().format(pub))));

                // Copy IEEE citation
                allItems.add(new PaletteItem("📋", "Copy IEEE — " + truncate(title, 40),
                        "Copy IEEE citation to clipboard",
                        () -> copyToClipboard(new IEEEFormatter().format(pub))));

                // Copy BibTeX citation
                allItems.add(new PaletteItem("📋", "Copy BibTeX — " + truncate(title, 40),
                        "Copy BibTeX entry to clipboard",
                        () -> copyToClipboard(new BibTeXFormatter().format(pub))));
            }
        } catch (Exception ignored) { }
    }

    // ── Filter & Render ──────────────────────────────────────────────────────

    private void filterAndRender(String query) {
        resultsContainer.getChildren().clear();
        selectedIndex = 0;

        List<PaletteItem> filtered;
        if (query == null || query.isBlank()) {
            // Show navigation commands only when empty
            filtered = allItems.stream()
                    .filter(i -> !i.label.startsWith("Copy"))
                    .limit(12)
                    .collect(Collectors.toList());
        } else {
            String q = query.toLowerCase();
            filtered = allItems.stream()
                    .filter(i -> i.label.toLowerCase().contains(q)
                            || i.subtitle.toLowerCase().contains(q))
                    .limit(15)
                    .collect(Collectors.toList());
        }

        for (int i = 0; i < filtered.size(); i++) {
            HBox row = buildRow(filtered.get(i), i);
            resultsContainer.getChildren().add(row);
        }

        if (filtered.isEmpty()) {
            Label empty = new Label("No results found for \"" + query + "\"");
            empty.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px; -fx-padding: 20 24;");
            resultsContainer.getChildren().add(empty);
        }

        highlightSelected();
    }

    private HBox buildRow(PaletteItem item, int index) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 20, 8, 20));
        row.setStyle("-fx-cursor: hand;");

        Label icon = new Label(item.icon);
        icon.setStyle("-fx-font-size: 14px; -fx-min-width: 22;");

        VBox textBox = new VBox(1);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label label = new Label(item.label);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: #1a1a2e; -fx-font-weight: bold;");
        label.setMaxWidth(440);
        label.setEllipsisString("…");

        Label subtitle = new Label(item.subtitle);
        subtitle.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #8888aa;");
        subtitle.setMaxWidth(440);
        subtitle.setEllipsisString("…");

        textBox.getChildren().addAll(label, subtitle);
        row.getChildren().addAll(icon, textBox);

        row.setOnMouseClicked(e -> {
            item.action.run();
            dismiss();
        });

        row.setOnMouseEntered(e -> {
            selectedIndex = index;
            highlightSelected();
        });

        return row;
    }

    private void highlightSelected() {
        var children = resultsContainer.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof HBox row) {
                if (i == selectedIndex) {
                    row.setStyle("-fx-background-color: #f0edff; -fx-cursor: hand; -fx-background-radius: 6;");
                } else {
                    row.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                }
            }
        }
    }

    // ── Keyboard Navigation ──────────────────────────────────────────────────

    private void handleKeyPress(KeyEvent e) {
        int count = (int) resultsContainer.getChildren().stream()
                .filter(n -> n instanceof HBox).count();

        switch (e.getCode()) {
            case UP -> {
                selectedIndex = Math.max(0, selectedIndex - 1);
                highlightSelected();
                e.consume();
            }
            case DOWN -> {
                selectedIndex = Math.min(count - 1, selectedIndex + 1);
                highlightSelected();
                e.consume();
            }
            case ENTER -> {
                if (selectedIndex >= 0 && selectedIndex < count
                        && resultsContainer.getChildren().get(selectedIndex) instanceof HBox row) {
                    row.getOnMouseClicked().handle(
                            new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                                    MouseButton.PRIMARY, 1, false, false, false, false,
                                    true, false, false, false, false, false, null));
                }
                e.consume();
            }
            case ESCAPE -> {
                dismiss();
                e.consume();
            }
            default -> {}
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void doNavigate(String view) {
        if (onNavigate != null) onNavigate.accept(view);
    }

    private void doSelectEntry(LibraryEntry entry) {
        if (onSelectEntry != null) onSelectEntry.accept(entry);
    }

    private void copyToClipboard(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private Label createHintLabel(String text) {
        Label l = new Label(text);
        l.setStyle(
                "-fx-text-fill: #9090b0; -fx-font-size: 10px; " +
                "-fx-background-color: #f0f0f5; -fx-padding: 3 8; " +
                "-fx-background-radius: 4;");
        return l;
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    private record PaletteItem(String icon, String label, String subtitle, Runnable action) {}
}
