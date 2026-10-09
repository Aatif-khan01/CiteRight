package com.citeright.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Simple filter bar — just a search box to filter papers by title or author.
 * Includes 300ms debounce to avoid excessive reloads.
 */
public class FilterBar extends HBox {

    private final TextField searchField;
    private final Button refreshBtn;
    private Runnable onFilterChange;
    private Runnable onRefresh;
    private final PauseTransition debounce;

    public FilterBar() {
        searchField = new TextField();
        refreshBtn = new Button("↻ Refresh");
        debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> { if (onFilterChange != null) onFilterChange.run(); });
        buildUI();
    }

    public void setOnFilterChange(Runnable handler) { this.onFilterChange = handler; }
    public void setOnRefresh(Runnable handler) { this.onRefresh = handler; }
    public String getSearchText() { return searchField.getText().trim().toLowerCase(); }

    private void buildUI() {
        setSpacing(8);
        setPadding(new Insets(6, 16, 6, 16));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: #f4f4f8;");

        searchField.setPromptText("🔍 Filter by title or author...");
        searchField.setStyle("-fx-font-size: 12px; -fx-padding: 7 12; -fx-background-radius: 6; " +
                "-fx-border-radius: 6; -fx-border-color: #d4d4e0; -fx-background-color: #ffffff;");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, o, n) -> {
            debounce.playFromStart();
        });

        refreshBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 12px; -fx-padding: 7 12; -fx-background-radius: 6; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> {
            if (onRefresh != null) onRefresh.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(searchField, spacer, refreshBtn);
    }
}
