package com.citeright.ui;

import com.citeright.model.*;
import com.citeright.service.LibraryService;
import com.citeright.formatter.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Clean 4-column table: Authors | Year | Title | Source.
 * Right-click context menu for all actions.
 */
public class ReferenceTableView extends VBox {

    private final TableView<LibraryEntry> table;
    private final ObservableList<LibraryEntry> data = FXCollections.observableArrayList();
    private Consumer<LibraryEntry> onSelect;
    private Consumer<LibraryEntry> onDoubleClick;
    private Consumer<LibraryEntry> onEdit;    // called when user picks Edit from context menu
    private Consumer<LibraryEntry> onDelete;  // called when user deletes from context menu
    private final LibraryService libraryService;
    private boolean trashMode = false;

    public ReferenceTableView(LibraryService libraryService) {
        this.libraryService = libraryService;
        this.table = new TableView<>();
        buildUI();
    }

    public void setOnSelect(Consumer<LibraryEntry> handler)     { this.onSelect = handler; }
    public void setOnDoubleClick(Consumer<LibraryEntry> handler) { this.onDoubleClick = handler; }
    public void setOnEdit(Consumer<LibraryEntry> handler)       { this.onEdit = handler; }
    public void setOnDelete(Consumer<LibraryEntry> handler)     { this.onDelete = handler; }
    public void setTrashMode(boolean trashMode)                 { this.trashMode = trashMode; }

    private void buildUI() {
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setStyle("-fx-background-color: #ffffff; -fx-font-size: 12px;");
        table.setPlaceholder(new Label("No references yet — use Search or Import to get started"));
        VBox.setVgrow(table, Priority.ALWAYS);

        // Row factory for hover styling, double-click and context menu
        table.setRowFactory(tv -> {
            TableRow<LibraryEntry> row = new TableRow<>() {
                @Override
                protected void updateItem(LibraryEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                    } else {
                        if (getIndex() % 2 == 0) {
                            setStyle("-fx-background-color: #ffffff;");
                        } else {
                            setStyle("-fx-background-color: #f8f9fc;");
                        }
                    }
                }
            };
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) row.setStyle("-fx-background-color: #eef0ff;");
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) {
                    int idx = row.getIndex();
                    row.setStyle(idx % 2 == 0 ? "-fx-background-color: #ffffff;" : "-fx-background-color: #f8f9fc;");
                }
            });
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && onDoubleClick != null)
                    onDoubleClick.accept(row.getItem());
            });
            row.itemProperty().addListener((obs, old, item) ->
                    row.setContextMenu(item == null ? null : buildContextMenu(row)));
            return row;
        });

        // S.No (serial number)
        TableColumn<LibraryEntry, String> snoCol = new TableColumn<>("S.No");
        snoCol.setCellValueFactory(c -> {
            int index = data.indexOf(c.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(index));
        });
        snoCol.setPrefWidth(45);
        snoCol.setMinWidth(45);
        snoCol.setMaxWidth(60);
        snoCol.setStyle("-fx-alignment: CENTER;");

        // Title (widest)
        TableColumn<LibraryEntry, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPublication() != null ? sanitizeTitle(c.getValue().getPublication().getTitle()) : ""));
        titleCol.setPrefWidth(340);
        titleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                    setWrapText(false);
                    setStyle("-fx-padding: 4 8;");
                }
            }
        });

        // Authors
        TableColumn<LibraryEntry, String> authorsCol = new TableColumn<>("Authors");
        authorsCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPublication() != null ? c.getValue().getPublication().getAuthorsShort() : ""));
        authorsCol.setPrefWidth(160);

        // Year
        TableColumn<LibraryEntry, String> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPublication() != null && c.getValue().getPublication().getYear() > 0
                        ? String.valueOf(c.getValue().getPublication().getYear()) : ""));
        yearCol.setPrefWidth(55);

        // Source
        TableColumn<LibraryEntry, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPublication() != null && c.getValue().getPublication().getVenue() != null
                        ? c.getValue().getPublication().getVenue() : ""));
        sourceCol.setPrefWidth(130);

        table.getColumns().add(snoCol);
        table.getColumns().add(titleCol);
        table.getColumns().add(authorsCol);
        table.getColumns().add(yearCol);
        table.getColumns().add(sourceCol);

        // Selection
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && onSelect != null) onSelect.accept(sel);
        });

        getChildren().add(table);
    }

    private ContextMenu buildContextMenu(TableRow<LibraryEntry> row) {
        ContextMenu ctx = new ContextMenu();

        if (trashMode) {
            MenuItem restoreItem = new MenuItem("♻ Restore to Library");
            restoreItem.setOnAction(e -> {
                int dbId = libraryService.getDbPaperId(row.getItem().getPublication().getPaperId());
                libraryService.restore(dbId);
                data.remove(row.getItem());
            });
            MenuItem deleteItem = new MenuItem("✕ Delete Permanently");
            deleteItem.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Permanently delete? This cannot be undone.", ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.YES) {
                        int dbId = libraryService.getDbPaperId(row.getItem().getPublication().getPaperId());
                        libraryService.permanentDelete(dbId);
                        data.remove(row.getItem());
                        if (onDelete != null) onDelete.accept(row.getItem());
                    }
                });
            });
            ctx.getItems().addAll(restoreItem, deleteItem);
        } else {
            // ⭐ Favorite
            MenuItem favItem = new MenuItem(row.getItem().isFavorite() ? "★ Remove from Favorites" : "☆ Add to Favorites");
            favItem.setAccelerator(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN));
            favItem.setOnAction(e -> {
                int dbId = libraryService.getDbPaperId(row.getItem().getPublication().getPaperId());
                libraryService.toggleFavorite(dbId);
                row.getItem().setFavorite(!row.getItem().isFavorite());
                table.refresh();
            });

            // Copy Citation submenu
            Menu citMenu = new Menu("📋 Copy Citation As...");
            for (String fmt : new String[]{"APA", "IEEE", "MLA", "Harvard", "BibTeX", "LaTeX", "Word XML"}) {
                MenuItem ci = new MenuItem(fmt);
                ci.setOnAction(e -> {
                    CitationFormatter formatter = switch (fmt) {
                        case "IEEE" -> new IEEEFormatter();
                        case "MLA" -> new MLAFormatter();
                        case "Harvard" -> new HarvardFormatter();
                        case "BibTeX" -> new BibTeXFormatter();
                        case "LaTeX" -> new LaTeXCiteFormatter();
                        case "Word XML" -> new WordXMLFormatter();
                        default -> new APAFormatter();
                    };
                    copyText(formatter.format(row.getItem().getPublication()));
                });
                citMenu.getItems().add(ci);
            }

            // External Academic Links
            Publication pub = row.getItem().getPublication();
            String paperDoi = pub.getDoi();
            String paperTitle = pub.getTitle();
            String sQuery = (paperDoi != null && !paperDoi.isBlank()) ? paperDoi : (paperTitle != null ? paperTitle : "");
            String rQuery = (paperTitle != null && !paperTitle.isBlank()) ? paperTitle : (paperDoi != null ? paperDoi : "");

            Menu openMenu = new Menu("🌐 Open In...");
            if (paperDoi != null && !paperDoi.isBlank()) {
                MenuItem doiLink = new MenuItem("🔗 DOI (doi.org)");
                doiLink.setOnAction(e -> openInBrowser("https://doi.org/" + paperDoi));
                openMenu.getItems().add(doiLink);
            }
            if (!sQuery.isBlank()) {
                MenuItem scholarLink = new MenuItem("🎓 Google Scholar");
                scholarLink.setOnAction(e -> {
                    try {
                        openInBrowser("https://scholar.google.com/scholar?q=" + java.net.URLEncoder.encode(sQuery, java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                });
                openMenu.getItems().add(scholarLink);
            }
            if (!rQuery.isBlank()) {
                MenuItem rgLink = new MenuItem("🟢 ResearchGate");
                rgLink.setOnAction(e -> {
                    try {
                        openInBrowser("https://www.researchgate.net/search/publication?q=" + java.net.URLEncoder.encode(rQuery, java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception ignored) {}
                });
                openMenu.getItems().add(rgLink);
            }

            // Copy DOI
            MenuItem doiItem = new MenuItem("🔗 Copy DOI");
            doiItem.setOnAction(e -> {
                String doi = row.getItem().getPublication().getDoi();
                if (doi != null && !doi.isEmpty()) copyText(doi);
            });

            // ✏ Edit
            MenuItem editItem = new MenuItem("✏  Edit Details");
            editItem.setAccelerator(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.E, javafx.scene.input.KeyCombination.CONTROL_DOWN));
            editItem.setOnAction(e -> {
                if (onEdit != null) onEdit.accept(row.getItem());
            });

            // Trash
            MenuItem trashItem = new MenuItem("🗑  Delete (Move to Trash)");
            trashItem.setStyle("-fx-text-fill: #cc0000;");
            trashItem.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Move \"" + row.getItem().getPublication().getTitle() + "\" to Trash?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.YES) {
                        int dbId = libraryService.getDbPaperId(row.getItem().getPublication().getPaperId());
                        libraryService.softDelete(dbId);
                        data.remove(row.getItem());
                        if (onDelete != null) onDelete.accept(row.getItem());
                    }
                });
            });

            ctx.getItems().addAll(favItem, new SeparatorMenuItem(), citMenu, doiItem, openMenu,
                    new SeparatorMenuItem(), editItem, trashItem);
        }
        return ctx;
    }

    private void copyText(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private void openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
        } catch (Exception e) {
            System.err.println("[ReferenceTableView] Cannot open browser: " + e.getMessage());
        }
    }

    public void loadEntries(List<LibraryEntry> entries) { data.setAll(entries); }
    public LibraryEntry getSelected() { return table.getSelectionModel().getSelectedItem(); }

    /**
     * Sanitizes paper titles for clean table rendering:
     * - Strips remaining HTML/JATS XML tags (<sub>, <sup>, <italic>, etc.)
     * - Normalizes Unicode subscript/superscript digits to ASCII
     * - Decodes common HTML entities
     */
    private static String sanitizeTitle(String title) {
        if (title == null || title.isEmpty()) return "";

        // Strip HTML/JATS tags
        String s = title.replaceAll("<[^>]*>", "");

        // Decode HTML entities
        s = s.replace("&lt;", "<").replace("&gt;", ">")
             .replace("&amp;", "&").replace("&quot;", "\"")
             .replace("&#x27;", "'").replace("&apos;", "'");

        // Normalize Unicode subscript digits to ASCII: ₀₁₂₃₄₅₆₇₈₉
        s = s.replace('\u2080', '0').replace('\u2081', '1').replace('\u2082', '2')
             .replace('\u2083', '3').replace('\u2084', '4').replace('\u2085', '5')
             .replace('\u2086', '6').replace('\u2087', '7').replace('\u2088', '8')
             .replace('\u2089', '9');

        // Normalize Unicode superscript digits: ⁰¹²³⁴⁵⁶⁷⁸⁹
        s = s.replace('\u2070', '0').replace('\u00B9', '1').replace('\u00B2', '2')
             .replace('\u00B3', '3').replace('\u2074', '4').replace('\u2075', '5')
             .replace('\u2076', '6').replace('\u2077', '7').replace('\u2078', '8')
             .replace('\u2079', '9');

        // Common subscript/superscript symbols
        s = s.replace('\u208B', '-')  // subscript minus ₋
             .replace('\u207B', '-')  // superscript minus ⁻
             .replace('\u208A', '+')  // subscript plus ₊
             .replace('\u207A', '+')  // superscript plus ⁺
             .replace('\u2093', 'x')  // subscript x ₓ
             .replace('\u2090', 'a')  // subscript a ₐ
             .replace('\u2091', 'e')  // subscript e ₑ
             .replace('\u2092', 'o'); // subscript o ₒ

        // Collapse multiple whitespace
        s = s.replaceAll("\\s+", " ").trim();

        return s;
    }
}
