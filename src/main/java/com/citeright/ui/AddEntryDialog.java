package com.citeright.ui;

import com.citeright.model.*;
import com.citeright.service.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Modal dialog for adding a paper by DOI, ArXiv ID, or PMID with auto-metadata fetch.
 */
public class AddEntryDialog {

    private final MetadataLookupService lookupService;
    private final PdfService pdfService;
    private final LibraryService libraryService;
    private Consumer<Publication> onSaved;

    public AddEntryDialog(MetadataLookupService lookupService, PdfService pdfService, LibraryService libraryService) {
        this.lookupService = lookupService;
        this.pdfService = pdfService;
        this.libraryService = libraryService;
    }

    public void setOnSaved(Consumer<Publication> handler) { this.onSaved = handler; }

    public void show() {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initStyle(javafx.stage.StageStyle.UNDECORATED);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setPrefWidth(500);
        root.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d0d0d0; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 12, 0, 0, 2);");

        Label title = new Label("Add New Reference");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1a1a1a;");

        // Identifier input
        HBox idRow = new HBox(8);
        idRow.setAlignment(Pos.CENTER_LEFT);
        TextField idField = new TextField();
        idField.setPromptText("Enter DOI, ArXiv ID, or PMID");
        idField.setPrefWidth(340);
        idField.setStyle("-fx-padding: 10; -fx-background-radius: 6; -fx-border-radius: 6; -fx-border-color: #d0d0d0;");
        HBox.setHgrow(idField, Priority.ALWAYS);

        Button lookupBtn = new Button("🔍 Lookup");
        lookupBtn.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 18; -fx-background-radius: 6; -fx-cursor: hand;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(20, 20); progress.setVisible(false);
        idRow.getChildren().addAll(idField, lookupBtn, progress);

        // Manual fields
        TextField titleField = new TextField(); titleField.setPromptText("Title");
        TextField authorsField = new TextField(); authorsField.setPromptText("Authors (comma-separated)");
        HBox row2 = new HBox(8);
        TextField yearField = new TextField(); yearField.setPromptText("Year"); yearField.setPrefWidth(80);
        TextField journalField = new TextField(); journalField.setPromptText("Journal/Source"); HBox.setHgrow(journalField, Priority.ALWAYS);
        row2.getChildren().addAll(yearField, journalField);
        TextField doiField = new TextField(); doiField.setPromptText("DOI");
        TextArea abstractArea = new TextArea(); abstractArea.setPromptText("Abstract"); abstractArea.setPrefRowCount(3); abstractArea.setWrapText(true);

        // Status label
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        for (TextField tf : new TextField[]{titleField, authorsField, doiField}) {
            tf.setStyle("-fx-padding: 8; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #e0e0e0;");
        }
        yearField.setStyle("-fx-padding: 8; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #e0e0e0;");
        journalField.setStyle("-fx-padding: 8; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #e0e0e0;");
        abstractArea.setStyle("-fx-padding: 8; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #e0e0e0; -fx-font-size: 12px;");

        // Lookup action
        lookupBtn.setOnAction(e -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) return;
            progress.setVisible(true); statusLabel.setText("Looking up...");
            new Thread(() -> {
                try {
                    Publication pub = lookupService.lookup(id);
                    Platform.runLater(() -> {
                        titleField.setText(pub.getTitle() != null ? pub.getTitle() : "");
                        authorsField.setText(pub.getAuthorsShort());
                        yearField.setText(pub.getYear() > 0 ? String.valueOf(pub.getYear()) : "");
                        journalField.setText(pub.getVenue() != null ? pub.getVenue() : "");
                        doiField.setText(pub.getDoi() != null ? pub.getDoi() : "");
                        abstractArea.setText(pub.getAbstractText() != null ? pub.getAbstractText() : "");
                        progress.setVisible(false);
                        statusLabel.setText("✓ Metadata loaded");
                        statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 11px;");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        statusLabel.setText("✗ " + ex.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #cc0000; -fx-font-size: 11px;");
                    });
                }
            }).start();
        });

        // Buttons
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #555555; -fx-cursor: hand; -fx-padding: 8 16;");
        cancelBtn.setOnAction(e -> dialog.close());

        CheckBox downloadPdf = new CheckBox("Auto-download PDF (open access)");
        downloadPdf.setSelected(true);
        downloadPdf.setStyle("-fx-font-size: 11px; -fx-text-fill: #555555;");

        Button saveBtn = new Button("Save to Library");
        saveBtn.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            JournalArticle paper = new JournalArticle();
            paper.setTitle(titleField.getText().trim());
            paper.setDoi(doiField.getText().trim());
            paper.setAbstractText(abstractArea.getText().trim());
            paper.setVenue(journalField.getText().trim());
            paper.setPaperId(doiField.getText().isEmpty() ? "manual-" + System.currentTimeMillis() : "doi-" + doiField.getText());
            try { paper.setYear(Integer.parseInt(yearField.getText().trim())); } catch (NumberFormatException ignored) {}
            for (String name : authorsField.getText().split(",")) {
                String n = name.trim();
                if (!n.isEmpty()) paper.addAuthor(new Author(n));
            }

            libraryService.saveToDefaultCollection(paper);
            statusLabel.setText("✓ Saved to library!");
            statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 11px;");

            // Auto-download PDF in background
            if (downloadPdf.isSelected() && paper.getDoi() != null && !paper.getDoi().isEmpty()) {
                new Thread(() -> {
                    try {
                        String pdfUrl = lookupService.getOpenAccessPdfUrl(paper.getDoi());
                        if (pdfUrl == null && idField.getText().matches("\\d+\\.\\d+"))
                            pdfUrl = lookupService.getArxivPdfUrl(idField.getText().trim());
                        if (pdfUrl != null) {
                            pdfService.downloadPdf(pdfUrl, paper.getTitle());
                            Platform.runLater(() -> statusLabel.setText("✓ PDF downloaded!"));
                        }
                    } catch (Exception ignored) {}
                }).start();
            }

            if (onSaved != null) onSaved.accept(paper);
            dialog.close();
        });

        buttons.getChildren().addAll(downloadPdf, cancelBtn, saveBtn);

        root.getChildren().addAll(title, idRow, statusLabel, titleField, authorsField, row2, doiField, abstractArea, buttons);
        dialog.setScene(new javafx.scene.Scene(root));
        dialog.showAndWait();
    }
}
