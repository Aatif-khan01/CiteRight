package com.citeright.ui;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class FloatingPreviewCard extends VBox {
    private final Label titleLabel;
    private final Label authorsLabel;
    private final Label glanceLabel;
    private final Button btnMore;
    private Runnable onMoreClicked;

    public FloatingPreviewCard() {
        setSpacing(8);
        setPadding(new Insets(12));
        setPrefWidth(280);
        setMaxWidth(280);
        setStyle("-fx-background-color: rgba(20, 20, 35, 0.95); -fx-border-color: rgba(74, 108, 247, 0.5); -fx-border-radius: 6; -fx-background-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 4);");

        titleLabel = new Label();
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: white;");
        titleLabel.setWrapText(true);

        authorsLabel = new Label();
        authorsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #88aadd;");
        authorsLabel.setWrapText(true);

        glanceLabel = new Label();
        glanceLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cccccc;");
        glanceLabel.setWrapText(true);

        btnMore = new Button("More Details →");
        btnMore.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9cf7; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 2 0 0 0;");
        btnMore.setOnAction(e -> {
            if (onMoreClicked != null) onMoreClicked.run();
        });

        HBox bottom = new HBox(btnMore);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(titleLabel, authorsLabel, glanceLabel, bottom);
        setVisible(false);
        setManaged(false);
    }

    public void setOnMoreClicked(Runnable onMoreClicked) {
        this.onMoreClicked = onMoreClicked;
    }

    public void show(PaperGraphPane.GraphNode node, double x, double y) {
        LibraryEntry entry = node.entry;
        if (entry == null) return;
        
        Publication pub = entry.getPublication();
        titleLabel.setText(pub != null && pub.getTitle() != null ? pub.getTitle() : "Unknown Title");
        
        if (pub != null && pub.getAuthors() != null && !pub.getAuthors().isEmpty()) {
            String authorName = pub.getAuthors().get(0).getName();
            String authors = pub.getAuthors().size() > 1 ? authorName + " et al." : authorName;
            String year = pub.getYear() > 0 ? " (" + pub.getYear() + ")" : "";
            authorsLabel.setText(authors + year);
        } else {
            authorsLabel.setText("Unknown Author");
        }

        // Generate a quick glance text based on citations
        int citations = pub != null ? pub.getCitationCount() : 0;
        if (citations > 100) {
            glanceLabel.setText("Highly cited foundational paper in this domain.");
        } else if (citations > 20) {
            glanceLabel.setText("Well-cited paper in this cluster.");
        } else {
            glanceLabel.setText("Recent or specialized paper.");
        }

        setLayoutX(x);
        setLayoutY(y);
        setVisible(true);
        setManaged(true);
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }
}
