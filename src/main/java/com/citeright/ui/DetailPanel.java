package com.citeright.ui;

import com.citeright.formatter.*;
import com.citeright.model.*;
import com.citeright.database.PdfDAO;
import com.citeright.nlp.TextRankSummarizer;
import com.citeright.nlp.TfIdfEngine;
import com.citeright.service.LibraryService;
import com.citeright.service.PdfService;
import com.citeright.service.MetadataEnrichmentService;
import com.citeright.service.CitationStyleManager;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import com.citeright.service.PdfChatService;


/**
 * Right-side detail panel with full editing, delete, add-to-library and open-DOI support.
 */
public class DetailPanel extends VBox {

    private final LibraryService libraryService;
    private final PdfDAO pdfDAO;
    private final PdfService pdfService;
    private LibraryEntry currentEntry;

    // Mode flags
    private boolean isEditMode = false;
    private boolean isFromSearch = false;   // true = paper came from search, not saved yet
    private boolean isTrashMode = false;    // true = currently viewing trash

    // Callbacks
    private Runnable onDeleted;             // called after trash/delete so the table refreshes
    private Runnable onAddedToLibrary;      // called after saving from search
    private Consumer<PdfOpenRequest> onOpenPdf; // called to open PDF viewer

    // Reused controls (edit mode)
    private TextField editTitle, editYear, editVenue, editDoi, editUrl;
    private TextArea  editAuthors, editAbstract;

    // Notes tracking for auto-save
    private TextArea pendingNotesArea;

    // Citation area
    private TextArea citationArea;
    private ComboBox<String> citationFormatCombo;

    public DetailPanel(LibraryService libraryService) {
        this.libraryService = libraryService;
        this.pdfDAO = new PdfDAO();
        this.pdfService = new PdfService();
        buildUI();
        showEmpty();
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────
    public void setOnDeleted(Runnable cb)        { this.onDeleted = cb; }
    public void setOnAddedToLibrary(Runnable cb) { this.onAddedToLibrary = cb; }
    public void setOnOpenPdf(Consumer<PdfOpenRequest> cb) { this.onOpenPdf = cb; }

    public void setTrashMode(boolean trashMode)  { this.isTrashMode = trashMode; }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Show a saved library entry (from the library table). */
    public void showEntry(LibraryEntry entry) {
        if (entry == null || entry.getPublication() == null) { showEmpty(); return; }
        this.isFromSearch = false;
        this.isEditMode   = false;
        this.currentEntry = entry;
        renderContent();
    }

    /** Show a paper from search results (may not be in library yet). */
    public void showSearchResult(LibraryEntry entry) {
        if (entry == null || entry.getPublication() == null) { showEmpty(); return; }
        this.isFromSearch = true;
        this.isEditMode   = false;
        this.currentEntry = entry;
        renderContent();
    }

    /** Switches the currently displayed entry directly into edit mode. */
    public void triggerEdit() {
        if (currentEntry == null) return;
        this.isEditMode = true;
        renderContent();
    }

    public void showEmpty() {
        saveCurrentNotes();
        getChildren().clear();
        currentEntry = null;
        isEditMode   = false;
        isFromSearch = false;
        pendingNotesArea = null;
        setVisible(false);
        setManaged(false);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildUI() {
        setPrefWidth(320); setMinWidth(280); setMaxWidth(400);
        setSpacing(0);
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e4e4e4; -fx-border-width: 0 0 0 1;");
        setVisible(false);
        setManaged(false);
    }

    private void renderContent() {
        saveCurrentNotes();
        pendingNotesArea = null;
        getChildren().clear();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #f8f9fa; -fx-background-color: #f8f9fa; -fx-border-color: transparent;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(14, 14, 20, 14));

        Publication pub = currentEntry.getPublication();

        // ── TOP ACTION BAR ─────────────────────────────────────────────────────
        content.getChildren().add(buildTopActionBar(pub));
        content.getChildren().add(new Separator());

        if (isEditMode) {
            // ── EDIT FORM ──────────────────────────────────────────────────────
            content.getChildren().addAll(buildEditForm(pub));
        } else {
            // ── READ VIEW ──────────────────────────────────────────────────────
            // Title
            Label titleLabel = new Label(pub.getTitle() != null ? pub.getTitle() : "Untitled");
            titleLabel.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 14.5px; -fx-font-weight: bold;");
            titleLabel.setWrapText(true);

            // Authors
            Label authorsLabel = new Label(pub.getAuthorsFormatted());
            authorsLabel.setStyle("-fx-text-fill: #4a6cf7; -fx-font-size: 11.5px;");
            authorsLabel.setWrapText(true);

            // Year · Venue
            String yearVenue = (pub.getYear() > 0 ? pub.getYear() + "" : "");
            if (pub.getVenue() != null && !pub.getVenue().isEmpty())
                yearVenue += (yearVenue.isEmpty() ? "" : "  ·  ") + pub.getVenue();
            Label metaLabel = new Label(yearVenue.isEmpty() ? "—" : yearVenue);
            metaLabel.setStyle("-fx-text-fill: #7a7a9a; -fx-font-size: 11px;");
            metaLabel.setWrapText(true);

            // DOI row
            content.getChildren().addAll(titleLabel, authorsLabel, metaLabel);

            String doi = pub.getDoi();
            if (doi != null && !doi.isEmpty()) {
                HBox doiRow = new HBox(6);
                doiRow.setAlignment(Pos.CENTER_LEFT);
                Label doiLabel = new Label("DOI: " + doi);
                doiLabel.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 10px;");
                doiLabel.setWrapText(true);
                HBox.setHgrow(doiLabel, Priority.ALWAYS);

                Button copyDoiBtn = new Button("Copy");
                copyDoiBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; " +
                        "-fx-font-size: 9px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-cursor: hand;");
                copyDoiBtn.setOnAction(e -> copyText(doi));

                Button openDoiBtn = new Button("🔗 Open");
                openDoiBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; " +
                        "-fx-font-size: 9px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-cursor: hand;");
                openDoiBtn.setOnAction(e -> openInBrowser("https://doi.org/" + doi));

                doiRow.getChildren().addAll(doiLabel, copyDoiBtn, openDoiBtn);
                content.getChildren().add(doiRow);
            }

            // ── ENRICH METADATA BUTTON ────────────────────────────────────────
            Button enrichBtn = new Button("🔍 Enrich Metadata via DOI");
            enrichBtn.setStyle(
                    "-fx-background-color: #f0edff; -fx-text-fill: #5a6cf5; -fx-font-size: 11px; " +
                    "-fx-font-weight: bold; -fx-padding: 5 14; -fx-background-radius: 8; -fx-cursor: hand;");
            enrichBtn.setMaxWidth(Double.MAX_VALUE);
            enrichBtn.setOnAction(e -> {
                enrichBtn.setDisable(true);
                enrichBtn.setText("⏳ Enriching...");
                MetadataEnrichmentService enricher = new MetadataEnrichmentService();
                enricher.enrichAsync(pub,
                    enrichedPub -> {
                        libraryService.updateMetadata(currentEntry.getId(), enrichedPub);
                        enrichBtn.setText("✅ Metadata Updated!");
                        renderContent(); // Refresh the panel
                    },
                    error -> {
                        enrichBtn.setText("⚠ " + error);
                        enrichBtn.setDisable(false);
                    });
            });
            content.getChildren().add(enrichBtn);

            content.getChildren().add(new Separator());

            // ── CITATION ────────────────────────────────────────────────────────
            Label citHeader = new Label("COPY CITATION");
            citHeader.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 9px; -fx-font-weight: bold;");

            HBox citRow = new HBox(8);
            citRow.setAlignment(Pos.CENTER_LEFT);

            citationFormatCombo = new ComboBox<>();
            CitationStyleManager csm = CitationStyleManager.getInstance();
            for (String styleId : csm.getAvailableStyles()) {
                citationFormatCombo.getItems().add(csm.getDisplayName(styleId));
            }
            // Also keep legacy export formats
            citationFormatCombo.getItems().addAll("BibTeX", "RIS");
            citationFormatCombo.setValue("APA 7th Edition");
            citationFormatCombo.setStyle("-fx-font-size: 11px;");
            citationFormatCombo.setOnAction(e -> updateCitation());

            Button copyCitBtn = new Button("📋 Copy");
            copyCitBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 11px; " +
                    "-fx-padding: 5 14; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");
            copyCitBtn.setOnAction(e -> {
                if (citationArea != null && !citationArea.getText().isEmpty()) {
                    copyText(citationArea.getText());
                    copyCitBtn.setText("✓ Copied!");
                    new Thread(() -> {
                        try { Thread.sleep(1500); } catch (Exception ignored) {}
                        Platform.runLater(() -> copyCitBtn.setText("📋 Copy"));
                    }).start();
                }
            });
            citRow.getChildren().addAll(citationFormatCombo, copyCitBtn);

            citationArea = new TextArea();
            citationArea.setEditable(false);
            citationArea.setWrapText(true);
            citationArea.setPrefRowCount(3);
            citationArea.setStyle("-fx-font-size: 10.5px; -fx-control-inner-background: #ffffff; " +
                    "-fx-border-color: #d4d4e0; -fx-border-radius: 6; -fx-background-radius: 6;");
            updateCitation();

            content.getChildren().addAll(citHeader, citRow, citationArea, new Separator());

            // ── ABSTRACT ────────────────────────────────────────────────────────
            Label absHeader = new Label("ABSTRACT");
            absHeader.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 9px; -fx-font-weight: bold;");

            Label abstractLabel = new Label(pub.getAbstractText() != null && !pub.getAbstractText().isEmpty()
                    ? pub.getAbstractText() : "No abstract available.");
            abstractLabel.setStyle("-fx-text-fill: #4a4a5a; -fx-font-size: 11px; -fx-line-spacing: 2;");
            abstractLabel.setWrapText(true);

            content.getChildren().addAll(absHeader, abstractLabel);

            // ── AI SUMMARY ──────────────────────────────────────────────────────
            String abstractText = pub.getAbstractText();
            if (abstractText != null && abstractText.length() > 80) {
                content.getChildren().add(new Separator());
                content.getChildren().add(buildAiSummarySection(abstractText));
            }

            // ── RELATED PAPERS ──────────────────────────────────────────────────
            if (!isFromSearch && currentEntry != null) {
                content.getChildren().add(new Separator());
                content.getChildren().add(buildRelatedPapersSection(currentEntry));
            }

            // ── NOTES (only for saved library entries) ───────────────────────────
            if (!isFromSearch) {
                content.getChildren().add(new Separator());
                Label notesHeader = new Label("MY NOTES");
                notesHeader.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 9px; -fx-font-weight: bold;");

                TextArea notesArea = new TextArea();
                notesArea.setPromptText("Add your notes here...");
                notesArea.setWrapText(true);
                notesArea.setPrefRowCount(4);
                notesArea.setStyle("-fx-font-size: 11px; -fx-control-inner-background: #ffffff; " +
                        "-fx-border-color: #d4d4e0; -fx-border-radius: 6; -fx-background-radius: 6;");
                notesArea.setText(currentEntry.getNotes() != null ? currentEntry.getNotes() : "");
                notesArea.focusedProperty().addListener((obs, o, focused) -> {
                    if (!focused) saveCurrentNotes();
                });
                pendingNotesArea = notesArea;
                content.getChildren().addAll(notesHeader, notesArea);

                // ── PDF ATTACHMENT ──────────────────────────────────────────────────
                content.getChildren().add(new Separator());
                content.getChildren().add(buildPdfSection());
            }
        }

        scroll.setContent(content);

        // Wrap in TabPane
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #f8f9fa;");
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        
        Tab detailsTab = new Tab("Details");
        detailsTab.setClosable(false);
        detailsTab.setContent(scroll);
        tabPane.getTabs().add(detailsTab);
        
        com.citeright.database.PdfDAO pdfDAO = new com.citeright.database.PdfDAO();
        com.citeright.model.PdfFile pdfFile = pdfDAO.getByPaperId(currentEntry.getId());
        if (pdfFile != null && pdfFile.getFilePath() != null && !pdfFile.getFilePath().isEmpty()) {
            Tab chatTab = new Tab("💬 Chat with Paper");
            chatTab.setClosable(false);
            chatTab.setContent(buildChatTab(pdfFile.getFilePath()));
            tabPane.getTabs().add(chatTab);
        }
        
        getChildren().add(tabPane);
        setVisible(true);
        setManaged(true);
    }

    // ── Top action bar ────────────────────────────────────────────────────────

    private VBox buildChatTab(String pdfPath) {
        VBox chatBox = new VBox(10);
        chatBox.setPadding(new Insets(14));
        chatBox.setStyle("-fx-background-color: #f8f9fa;");

        Label intro = new Label("Ask questions directly about this paper. The AI will read the PDF text to find the answers.");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 11px;");

        TextArea chatHistory = new TextArea();
        chatHistory.setEditable(false);
        chatHistory.setWrapText(true);
        chatHistory.setStyle("-fx-font-size: 11.5px; -fx-control-inner-background: #ffffff;");
        VBox.setVgrow(chatHistory, Priority.ALWAYS);

        TextField inputField = new TextField();
        inputField.setPromptText("Ask a question about this paper...");
        inputField.setStyle("-fx-font-size: 11.5px; -fx-padding: 8; -fx-background-radius: 6;");
        inputField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                com.citeright.ui.LocalSemanticAIPrompt.showPromptIfNeeded();
            }
        });

        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");

        HBox inputBox = new HBox(8, inputField, sendBtn);
        inputBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        PdfChatService chatService = new PdfChatService();

        Runnable sendMsg = () -> {
            com.citeright.ui.LocalSemanticAIPrompt.showPromptIfNeeded();
            String q = inputField.getText().trim();
            if (q.isEmpty()) return;
            chatHistory.appendText("You: " + q + "\n\n");
            inputField.clear();
            inputField.setDisable(true);
            sendBtn.setDisable(true);
            
            new Thread(() -> {
                String pdfText = pdfService.extractText(pdfPath);
                String ans = chatService.askPdf(q, pdfText);
                Platform.runLater(() -> {
                    chatHistory.appendText("CiteRight AI: " + ans + "\n\n");
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    inputField.requestFocus();
                });
            }).start();
        };

        sendBtn.setOnAction(e -> sendMsg.run());
        inputField.setOnAction(e -> sendMsg.run());

        chatBox.getChildren().addAll(intro, chatHistory, inputBox);
        return chatBox;
    }

    private HBox buildTopActionBar(Publication pub) {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 4, 0));

        // Close button (far left)
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; " +
                "-fx-font-size: 15px; -fx-cursor: hand; -fx-padding: 2 8; -fx-background-radius: 6;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: #ffe0e0; -fx-text-fill: #cc3333; " +
                "-fx-font-size: 15px; -fx-cursor: hand; -fx-padding: 2 8; -fx-background-radius: 6;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; " +
                "-fx-font-size: 15px; -fx-cursor: hand; -fx-padding: 2 8; -fx-background-radius: 6;"));
        closeBtn.setOnAction(e -> showEmpty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (isEditMode) {
            // EDIT MODE: Save + Cancel
            Button saveBtn = new Button("💾 Save");
            saveBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; " +
                    "-fx-font-size: 11px; -fx-padding: 5 14; -fx-background-radius: 6; " +
                    "-fx-cursor: hand; -fx-font-weight: bold;");
            saveBtn.setOnAction(e -> saveEdits());

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #555; " +
                    "-fx-font-size: 11px; -fx-padding: 5 12; -fx-background-radius: 6; -fx-cursor: hand;");
            cancelBtn.setOnAction(e -> {
                isEditMode = false;
                renderContent();
            });

            bar.getChildren().addAll(closeBtn, spacer, saveBtn, cancelBtn);

        } else if (isFromSearch) {
            // SEARCH MODE: Add to Library + Open DOI + Edit
            boolean alreadySaved = pub.getPaperId() != null &&
                    libraryService.isInLibrary(pub.getPaperId());

            Button addBtn;
            if (alreadySaved) {
                addBtn = new Button("✓ In Library");
                addBtn.setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; " +
                        "-fx-font-size: 11px; -fx-padding: 5 12; -fx-background-radius: 6;");
                addBtn.setDisable(true);
            } else {
                addBtn = new Button("+ Save to Library");
                addBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; " +
                        "-fx-font-size: 11px; -fx-padding: 5 12; -fx-background-radius: 6; " +
                        "-fx-cursor: hand; -fx-font-weight: bold;");
                addBtn.setOnAction(e -> {
                    libraryService.saveToDefaultCollection(pub);
                    addBtn.setText("✓ In Library");
                    addBtn.setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; " +
                            "-fx-font-size: 11px; -fx-padding: 5 12; -fx-background-radius: 6;");
                    addBtn.setDisable(true);
                    if (onAddedToLibrary != null) onAddedToLibrary.run();
                });
            }

            Button editBtn = makeIconBtn("✏", "#555");
            editBtn.setTooltip(new Tooltip("Edit details"));
            editBtn.setOnAction(e -> { isEditMode = true; renderContent(); });

            String doi = pub.getDoi();
            if (doi != null && !doi.isEmpty()) {
                Button openDoiBtn = makeIconBtn("🔗", "#4a6cf7");
                openDoiBtn.setTooltip(new Tooltip("Open DOI in browser"));
                openDoiBtn.setOnAction(e -> openInBrowser("https://doi.org/" + doi));
                bar.getChildren().addAll(closeBtn, spacer, addBtn, editBtn, openDoiBtn);
            } else {
                bar.getChildren().addAll(closeBtn, spacer, addBtn, editBtn);
            }

        } else {
            // LIBRARY MODE: Favorite + Edit + Open DOI + Delete
            boolean isFav = currentEntry.isFavorite();
            Button favBtn = makeIconBtn(isFav ? "★" : "☆", isFav ? "#f5a623" : "#888");
            favBtn.setTooltip(new Tooltip(isFav ? "Remove from Favorites" : "Add to Favorites"));
            favBtn.setOnAction(e -> {
                int dbId = libraryService.getDbPaperId(currentEntry.getPublication().getPaperId());
                if (dbId > 0) {
                    libraryService.toggleFavorite(dbId);
                    currentEntry.setFavorite(!currentEntry.isFavorite());
                    renderContent(); // re-render to update the star
                }
            });

            Button editBtn = makeIconBtn("✏ Edit", "#4a6cf7");
            editBtn.setTooltip(new Tooltip("Edit metadata"));
            editBtn.setOnAction(e -> { isEditMode = true; renderContent(); });

            String doi = pub.getDoi();
            if (doi != null && !doi.isEmpty()) {
                Button openDoiBtn = makeIconBtn("🔗 DOI", "#555");
                openDoiBtn.setTooltip(new Tooltip("Open DOI in browser"));
                openDoiBtn.setOnAction(e -> openInBrowser("https://doi.org/" + doi));
                bar.getChildren().addAll(closeBtn, spacer, favBtn, editBtn, openDoiBtn, buildDeleteBtn());
            } else {
                bar.getChildren().addAll(closeBtn, spacer, favBtn, editBtn, buildDeleteBtn());
            }
        }

        return bar;
    }

    private Button buildDeleteBtn() {
        Button deleteBtn = new Button(isTrashMode ? "✕ Delete Forever" : "🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cc3333; " +
                "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 2 6;");
        deleteBtn.setTooltip(new Tooltip(isTrashMode ? "Delete Permanently" : "Move to Trash"));
        deleteBtn.setOnAction(e -> {
            String prompt = isTrashMode ? "Permanently delete? This cannot be undone." : "Move this paper to Trash?";
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, prompt, ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.setTitle("Delete");
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES && currentEntry != null && currentEntry.getPublication() != null) {
                    int dbId = libraryService.getDbPaperId(currentEntry.getPublication().getPaperId());
                    if (dbId > 0) {
                        if (isTrashMode) {
                            libraryService.permanentDelete(dbId);
                        } else {
                            libraryService.softDelete(dbId);
                        }
                    }
                    showEmpty();
                    if (onDeleted != null) onDeleted.run();
                }
            });
        });
        return deleteBtn;
    }

    private Button makeIconBtn(String text, String textColor) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #ececf4; -fx-text-fill: " + textColor + "; " +
                "-fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 6; -fx-cursor: hand;");
        return btn;
    }

    // ── Edit form ─────────────────────────────────────────────────────────────

    private javafx.scene.Node[] buildEditForm(Publication pub) {
        Label header = new Label("Edit Reference Details");
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        // Title
        editTitle = styledField(pub.getTitle());
        // Authors (comma-separated)
        String authStr = pub.getAuthorsFormatted().equals("Unknown Authors") ? "" : pub.getAuthorsFormatted();
        editAuthors = styledTextArea(authStr, "e.g. Smith, J., Doe, A.", 2);
        // Year
        editYear = styledField(pub.getYear() > 0 ? String.valueOf(pub.getYear()) : "");
        // Venue
        editVenue = styledField(pub.getVenue());
        // DOI
        editDoi = styledField(pub.getDoi());
        // URL
        editUrl = styledField(pub.getUrl());
        // Abstract
        editAbstract = styledTextArea(pub.getAbstractText(), "Paste abstract here...", 5);

        return new javafx.scene.Node[]{
                header,
                fieldLabel("Title"), editTitle,
                fieldLabel("Authors (comma-separated)"), editAuthors,
                fieldLabel("Year"), editYear,
                fieldLabel("Journal / Venue"), editVenue,
                fieldLabel("DOI"), editDoi,
                fieldLabel("URL"), editUrl,
                fieldLabel("Abstract"), editAbstract
        };
    }

    private void saveEdits() {
        if (currentEntry == null || currentEntry.getPublication() == null) return;
        Publication pub = currentEntry.getPublication();

        // Apply edits to the publication object
        if (editTitle != null && !editTitle.getText().isBlank())
            pub.setTitle(editTitle.getText().trim());
        if (editYear != null && !editYear.getText().isBlank()) {
            try { pub.setYear(Integer.parseInt(editYear.getText().trim())); } catch (NumberFormatException ignored) {}
        }
        if (editVenue != null) pub.setVenue(editVenue.getText().trim());
        if (editDoi   != null) pub.setDoi(editDoi.getText().trim());
        if (editUrl   != null) pub.setUrl(editUrl.getText().trim());
        if (editAbstract != null) pub.setAbstractText(editAbstract.getText().trim());

        // Authors
        if (editAuthors != null && !editAuthors.getText().isBlank()) {
            pub.getAuthors().clear();
            String[] parts = editAuthors.getText().split(",");
            for (String name : parts) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    pub.addAuthor(new Author(null, trimmed));
                }
            }
        }

        // Persist to DB
        int dbId = libraryService.getDbPaperId(pub.getPaperId());
        if (dbId > 0) {
            libraryService.updateMetadata(dbId, pub);
        } else if (isFromSearch) {
            // Not in library yet — save it first, then update
            libraryService.saveToDefaultCollection(pub);
            if (onAddedToLibrary != null) onAddedToLibrary.run();
        }

        isEditMode = false;
        isFromSearch = false;
        renderContent();
    }

    // ── Citation ──────────────────────────────────────────────────────────────

    private void updateCitation() {
        if (currentEntry == null || currentEntry.getPublication() == null || citationFormatCombo == null) return;
        String displayName = citationFormatCombo.getValue();
        Publication pub = currentEntry.getPublication();

        // Legacy export formats
        if ("BibTeX".equals(displayName)) {
            citationArea.setText(new BibTeXFormatter().format(pub));
            return;
        }
        if ("RIS".equals(displayName)) {
            citationArea.setText(new RISFormatter().format(pub));
            return;
        }

        // CSL-powered formatting
        CitationStyleManager csm = CitationStyleManager.getInstance();
        // Reverse-lookup the style ID from the display name
        String styleId = null;
        for (String id : csm.getAvailableStyles()) {
            if (csm.getDisplayName(id).equals(displayName)) {
                styleId = id;
                break;
            }
        }
        if (styleId == null) {
            // Fallback: try display name directly as style ID
            styleId = displayName.toLowerCase().replace(" ", "-");
        }
        citationArea.setText(csm.formatCitation(pub, styleId));
    }

    /** Persists any pending notes before the view changes. */
    private void saveCurrentNotes() {
        if (pendingNotesArea != null && currentEntry != null && currentEntry.getPublication() != null) {
            int dbId = libraryService.getDbPaperId(currentEntry.getPublication().getPaperId());
            if (dbId > 0) libraryService.updateNotes(dbId, pendingNotesArea.getText());
        }
    }
    // ── PDF Attachment ─────────────────────────────────────────────────────────

    private VBox buildPdfSection() {
        VBox section = new VBox(8);

        Label pdfHeader = new Label("📎 ATTACHED PDF");
        pdfHeader.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 9px; -fx-font-weight: bold;");
        section.getChildren().add(pdfHeader);

        if (currentEntry == null || currentEntry.getPublication() == null) return section;

        int dbId = libraryService.getDbPaperId(currentEntry.getPublication().getPaperId());
        if (dbId <= 0) return section;

        PdfFile existingPdf = pdfDAO.getByPaperId(dbId);

        if (existingPdf != null && existingPdf.getFilePath() != null &&
                new File(existingPdf.getFilePath()).exists()) {
            // ── PDF is attached — show info ──
            VBox infoBox = new VBox(4);
            infoBox.setPadding(new Insets(8));
            infoBox.setStyle("-fx-background-color: #f0f4ff; -fx-background-radius: 8; -fx-border-color: #d4daf0; -fx-border-radius: 8;");

            Label fileName = new Label("📄 " + existingPdf.getFileName());
            fileName.setStyle("-fx-text-fill: #2a2a5a; -fx-font-size: 11px; -fx-font-weight: bold;");
            fileName.setWrapText(true);

            Label fileInfo = new Label(existingPdf.getFileSizeFormatted() +
                    (existingPdf.getPageCount() > 0 ? "  •  " + existingPdf.getPageCount() + " pages" : ""));
            fileInfo.setStyle("-fx-text-fill: #6a6a8a; -fx-font-size: 10px;");

            infoBox.getChildren().addAll(fileName, fileInfo);

            HBox btnRow = new HBox(6);
            btnRow.setAlignment(Pos.CENTER_LEFT);

            Button openBtn = new Button("📖 Open PDF Viewer");
            openBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 11px; " +
                    "-fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");
            final PdfFile finalPdf = existingPdf;
            openBtn.setOnAction(e -> {
                if (onOpenPdf != null) {
                    String title = currentEntry.getPublication().getTitle();
                    onOpenPdf.accept(new PdfOpenRequest(finalPdf.getFilePath(), finalPdf.getId(), title, currentEntry.getPublication()));
                }
            });

            Button removePdfBtn = new Button("🗑 Remove");
            removePdfBtn.setStyle("-fx-background-color: #f8e8e8; -fx-text-fill: #cc3333; -fx-font-size: 10px; " +
                    "-fx-padding: 5 10; -fx-background-radius: 6; -fx-cursor: hand;");
            removePdfBtn.setOnAction(e -> {
                pdfDAO.delete(finalPdf.getId());
                renderContent(); // re-render to show attach button
            });

            btnRow.getChildren().addAll(openBtn, removePdfBtn);
            section.getChildren().addAll(infoBox, btnRow);
        } else {
            // ── No PDF attached — show attach button ──
            Label noFile = new Label("No PDF attached to this paper.");
            noFile.setStyle("-fx-text-fill: #8a8aaa; -fx-font-size: 10px;");

            Button attachBtn = new Button("📎 Attach PDF File");
            attachBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 11px; " +
                    "-fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
            attachBtn.setOnAction(e -> attachPdf(dbId));

            section.getChildren().addAll(noFile, attachBtn);
        }

        return section;
    }

    private void attachPdf(int dbPaperId) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Attach PDF");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;

        new Thread(() -> {
            try {
                // Copy PDF to app storage
                String pdfDir = com.citeright.database.SQLiteDatabaseManager.getInstance().getPdfDirPath();
                String safeName = file.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
                Path dest = Path.of(pdfDir, safeName);
                Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

                // Get page count and file size
                int pageCount = pdfService.getPageCount(dest.toString());
                long fileSize = Files.size(dest);

                // Save to DB
                PdfFile pdfFile = new PdfFile(dbPaperId, dest.toString(), file.getName());
                pdfFile.setPageCount(pageCount);
                pdfFile.setFileSize(fileSize);
                pdfDAO.save(pdfFile);

                Platform.runLater(this::renderContent);
            } catch (Exception ex) {
                System.err.println("[DetailPanel] Attach PDF error: " + ex.getMessage());
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText("Failed to attach PDF: " + ex.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void copyText(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                // Fallback for environments without Desktop support (headless JVM)
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
        } catch (Exception e) {
            System.err.println("[DetailPanel] Cannot open browser: " + e.getMessage());
        }
    }

    private TextField styledField(String value) {
        TextField tf = new TextField(value != null ? value : "");
        tf.setStyle("-fx-font-size: 11.5px; -fx-padding: 6 10; -fx-background-radius: 6; " +
                "-fx-border-color: #d4d4e0; -fx-border-radius: 6; -fx-background-color: #ffffff;");
        return tf;
    }

    private TextArea styledTextArea(String value, String prompt, int rows) {
        TextArea ta = new TextArea(value != null ? value : "");
        ta.setPromptText(prompt);
        ta.setWrapText(true);
        ta.setPrefRowCount(rows);
        ta.setStyle("-fx-font-size: 11.5px; -fx-control-inner-background: #ffffff; " +
                "-fx-border-color: #d4d4e0; -fx-border-radius: 6; -fx-background-radius: 6;");
        return ta;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 4 0 2 0;");
        return l;
    }

    /**
     * Builds a collapsible "✨ AI Summary" section.
     * Clicking the header runs TextRank and reveals the 3-bullet summary instantly.
     */
    private VBox buildAiSummarySection(String abstractText) {
        VBox section = new VBox(6);

        // Header row — acts as toggle
        HBox headerRow = new HBox(6);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setStyle(
                "-fx-background-color: linear-gradient(to right, #f0edff, #e8f4ff); " +
                "-fx-background-radius: 8; -fx-padding: 7 12; -fx-cursor: hand;");

        Label sparkle = new Label("✨");
        sparkle.setStyle("-fx-font-size: 13px;");

        Label aiLabel = new Label("AI Summary");
        aiLabel.setStyle("-fx-text-fill: #5a3fbf; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label badgeLabel = new Label("BETA · Powered by CiteRight AI");
        badgeLabel.setStyle("-fx-text-fill: #9a89cf; -fx-font-size: 9px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label toggleArrow = new Label("▶");
        toggleArrow.setStyle("-fx-text-fill: #9a89cf; -fx-font-size: 9px;");

        headerRow.getChildren().addAll(sparkle, aiLabel, badgeLabel, spacer, toggleArrow);

        // Summary content (hidden by default until clicked)
        VBox summaryContent = new VBox(5);
        summaryContent.setVisible(false);
        summaryContent.setManaged(false);
        summaryContent.setStyle("-fx-padding: 6 0 0 0;");

        // Toggle on click
        headerRow.setOnMouseClicked(e -> {
            boolean expanded = summaryContent.isVisible();
            if (!expanded) {
                // Generate summary lazily on first expand
                if (summaryContent.getChildren().isEmpty()) {
                    java.util.List<String> bullets = TextRankSummarizer.summarize(abstractText, 3);
                    for (String bullet : bullets) {
                        HBox bulletRow = new HBox(8);
                        bulletRow.setAlignment(Pos.TOP_LEFT);
                        Label dot = new Label("•");
                        dot.setStyle("-fx-text-fill: #7a5fbf; -fx-font-size: 13px; -fx-font-weight: bold;");
                        Label text = new Label(bullet);
                        text.setWrapText(true);
                        text.setStyle("-fx-text-fill: #3a3a5a; -fx-font-size: 11px; -fx-line-spacing: 1.5;");
                        HBox.setHgrow(text, Priority.ALWAYS);
                        bulletRow.getChildren().addAll(dot, text);
                        summaryContent.getChildren().add(bulletRow);
                    }
                    Label credit = new Label("Generated from abstract · 100% local · No API used");
                    credit.setStyle("-fx-text-fill: #b0a8d0; -fx-font-size: 8.5px; -fx-padding: 4 0 0 0;");
                    summaryContent.getChildren().add(credit);
                }
                summaryContent.setVisible(true);
                summaryContent.setManaged(true);
                toggleArrow.setText("▼");
                headerRow.setStyle(
                        "-fx-background-color: linear-gradient(to right, #e8e0ff, #d8eeff); " +
                        "-fx-background-radius: 8; -fx-padding: 7 12; -fx-cursor: hand;");
            } else {
                summaryContent.setVisible(false);
                summaryContent.setManaged(false);
                toggleArrow.setText("▶");
                headerRow.setStyle(
                        "-fx-background-color: linear-gradient(to right, #f0edff, #e8f4ff); " +
                        "-fx-background-radius: 8; -fx-padding: 7 12; -fx-cursor: hand;");
            }
        });

        section.getChildren().addAll(headerRow, summaryContent);
        return section;
    }

    private VBox buildRelatedPapersSection(LibraryEntry targetEntry) {
        VBox section = new VBox(6);
        Label header = new Label("RELATED PAPERS (AI MATCHER)");
        header.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 9px; -fx-font-weight: bold;");

        VBox listContainer = new VBox(4);
        Label loadingLabel = new Label("⏳ Finding similar papers...");
        loadingLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px;");
        listContainer.getChildren().add(loadingLabel);
        section.getChildren().addAll(header, listContainer);

        new Thread(() -> {
            try {
                java.util.List<LibraryEntry> allEntries = libraryService.getAllActive();
                if (allEntries.size() < 2) {
                    Platform.runLater(() -> {
                        listContainer.getChildren().clear();
                        Label none = new Label("Not enough papers in library.");
                        none.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px;");
                        listContainer.getChildren().add(none);
                    });
                    return;
                }

                java.util.List<LibraryEntry> topRelated = new java.util.ArrayList<>();
                java.util.Map<LibraryEntry, Double> scores = new java.util.HashMap<>();

                if (com.citeright.ai.NeuralAvailability.isReady()) {
                    // --- Neural Recommendation Path (BGE-M3) ---
                    System.out.println("[Recommendation] Using BGE-M3 neural recommender automatically.");
                    com.citeright.service.PaperRecommendationService recService = new com.citeright.service.PaperRecommendationService();
                    java.util.List<com.citeright.service.PaperRecommendationService.ScoredRecommendation> recs = 
                        recService.findSimilar(targetEntry, allEntries, 3);
                    for (com.citeright.service.PaperRecommendationService.ScoredRecommendation rec : recs) {
                        topRelated.add(rec.entry());
                        scores.put(rec.entry(), rec.similarity());
                    }
                } else {
                    // --- Standard TF-IDF Fallback Path ---
                    System.out.println("[Recommendation] Using TF-IDF fallback recommender automatically.");
                    TfIdfEngine engine = new TfIdfEngine();
                    java.util.List<String> docs = new java.util.ArrayList<>();
                    for (LibraryEntry e : allEntries) {
                        docs.add(e.getPublication().getTitle() + " " + (e.getPublication().getAbstractText() != null ? e.getPublication().getAbstractText() : ""));
                    }
                    engine.buildModel(docs);

                    int targetIndex = -1;
                    for (int i = 0; i < allEntries.size(); i++) {
                        if (allEntries.get(i).getId() == targetEntry.getId()) {
                            targetIndex = i;
                            break;
                        }
                    }
                    if (targetIndex < 0) {
                        Platform.runLater(() -> {
                            listContainer.getChildren().clear();
                            Label err = new Label("Selected paper not found in library.");
                            err.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px;");
                            listContainer.getChildren().add(err);
                        });
                        return;
                    }

                    String targetDoc = docs.get(targetIndex);
                    java.util.Map<String, Double> targetVec = engine.computeTfIdfVector(targetDoc);

                    java.util.List<LibraryEntry> related = new java.util.ArrayList<>();
                    for (int i = 0; i < allEntries.size(); i++) {
                        if (i == targetIndex) continue;
                        java.util.Map<String, Double> vec = engine.computeTfIdfVector(docs.get(i));
                        double sim = TfIdfEngine.cosineSimilarity(targetVec, vec);
                        if (sim > 0.05) {
                            scores.put(allEntries.get(i), sim);
                            related.add(allEntries.get(i));
                        }
                    }

                    related.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
                    topRelated = related.subList(0, Math.min(3, related.size()));
                }

                final java.util.List<LibraryEntry> finalRelated = topRelated;
                Platform.runLater(() -> {
                    listContainer.getChildren().clear();
                    if (finalRelated.isEmpty()) {
                        Label none = new Label("No closely related papers found.");
                        none.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px;");
                        listContainer.getChildren().add(none);
                    } else {
                        for (LibraryEntry rel : finalRelated) {
                            double score = scores.getOrDefault(rel, 0.0);
                            Hyperlink link = new Hyperlink(rel.getPublication().getTitle() + " (" + Math.round(score * 100) + "% match)");
                            link.setStyle("-fx-text-fill: #4a6cf7; -fx-font-size: 11px;");
                            link.setWrapText(true);
                            link.setOnAction(e -> {
                                saveCurrentNotes();
                                this.isFromSearch = false;
                                this.isEditMode = false;
                                this.currentEntry = rel;
                                renderContent();
                            });
                            listContainer.getChildren().add(link);
                        }
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    listContainer.getChildren().clear();
                    Label errLabel = new Label("Error loading related papers.");
                    errLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px;");
                    listContainer.getChildren().add(errLabel);
                });
            }
        }).start();

        return section;
    }
}
