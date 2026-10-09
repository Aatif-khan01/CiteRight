package com.citeright.ui.graph;

import com.citeright.model.Publication;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class FloatingPreviewCard extends VBox {

    private final Label titleLabel;
    private final ProgressBar impactBar;
    private final Label impactScoreLabel;
    private final Label atAGlanceLabel;
    private final Button moreDetailsBtn;

    public FloatingPreviewCard() {
        setMaxSize(300, Region.USE_PREF_SIZE);
        setPadding(new Insets(12));
        setSpacing(8);
        setStyle(Theme.GLASS_CARD);
        setVisible(false);
        setMouseTransparent(false);

        titleLabel = new Label();
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        HBox impactRow = new HBox(6);
        impactRow.setAlignment(Pos.CENTER_LEFT);
        Label impactTitle = new Label("IMPACT:");
        impactTitle.setStyle("-fx-text-fill: " + Theme.TEXT_FAINT_HEX + "; -fx-font-size: 9px; -fx-font-weight: bold;");
        impactBar = new ProgressBar(0);
        impactBar.setPrefWidth(100);
        impactBar.setPrefHeight(4);
        impactScoreLabel = new Label("0/100");
        impactScoreLabel.setStyle("-fx-text-fill: " + Theme.TEXT_DIM_HEX + "; -fx-font-size: 10px;");
        impactRow.getChildren().addAll(impactTitle, impactBar, impactScoreLabel);

        atAGlanceLabel = new Label();
        atAGlanceLabel.setWrapText(true);
        atAGlanceLabel.setStyle("-fx-text-fill: #ccccee; -fx-font-size: 10.5px; -fx-line-spacing: 2;");

        moreDetailsBtn = new Button("More Details →");
        moreDetailsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + Theme.ACCENT_HEX + "; " +
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 0; -fx-cursor: hand;");
        
        moreDetailsBtn.setOnMouseEntered(e -> moreDetailsBtn.setStyle(moreDetailsBtn.getStyle() + "-fx-underline: true;"));
        moreDetailsBtn.setOnMouseExited(e -> moreDetailsBtn.setStyle(moreDetailsBtn.getStyle().replace("-fx-underline: true;", "")));

        getChildren().addAll(titleLabel, impactRow, atAGlanceLabel, moreDetailsBtn);
        
        // Prevent clicks on the card from propagating to the canvas
        setOnMouseClicked(e -> e.consume());
        setOnMousePressed(e -> e.consume());
        setOnMouseDragged(e -> e.consume());
    }

    public void show(GraphNode node, String atAGlanceText, java.util.function.Consumer<GraphNode> onMoreDetails) {
        Publication pub = node.entry != null ? node.entry.getPublication() : null;
        titleLabel.setText(pub != null && pub.getTitle() != null ? pub.getTitle() : node.title);

        int score = (int) Math.round(node.importance * 100);
        impactBar.setProgress(node.importance);
        String barColor = score >= 70 ? "#2ecc71" : score >= 40 ? "#f1c40f" : "#e74c3c";
        impactBar.setStyle("-fx-accent: " + barColor + "; -fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 2;");
        impactScoreLabel.setText(score + "/100");

        atAGlanceLabel.setText(atAGlanceText);

        moreDetailsBtn.setOnAction(e -> {
            setVisible(false);
            if (onMoreDetails != null) onMoreDetails.accept(node);
        });

        setVisible(true);
    }
    
    public void hide() {
        setVisible(false);
    }
}
