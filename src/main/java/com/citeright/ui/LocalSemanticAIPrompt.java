package com.citeright.ui;

import com.citeright.ai.EmbeddingService;
import com.citeright.ai.GeminiConfig;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/**
 * Implements the on-demand Hybrid Smart Activation modal dialog.
 * Prompts the user exactly once when they first interact with a semantic feature.
 * Preserves bandwidth and user trust while presenting premium offline AI capabilities.
 */
public class LocalSemanticAIPrompt {

    /**
     * Triggers the "Enable Local Semantic AI?" prompt if the user hasn't configured it
     * and hasn't been prompted yet. Runs thread-safely on the FX application thread.
     */
    public static void showPromptIfNeeded() {
        if (GeminiConfig.isBgeM3() || GeminiConfig.isEmbeddingPrompted()) {
            return; // already enabled BGE-M3 or already prompted once
        }

        Platform.runLater(() -> {
            Dialog<Boolean> dialog = new Dialog<>();
            dialog.setTitle("Enable Local Semantic AI?");
            dialog.initModality(Modality.APPLICATION_MODAL);

            DialogPane pane = dialog.getDialogPane();
            pane.setPrefWidth(420);
            pane.setStyle("-fx-background-color: #ffffff;");

            VBox layout = new VBox(14);
            layout.setPadding(new Insets(18));
            layout.setAlignment(Pos.TOP_LEFT);

            Label title = new Label("Enable Local Semantic AI?");
            title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

            Label desc = new Label("Unlock premium offline semantic features directly inside CiteRight. All computations run 100% privately on your local CPU.");
            desc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #5a5a7a; -fx-line-spacing: 1.5;");
            desc.setWrapText(true);

            VBox bulletList = new VBox(6);
            String[] features = {
                "• Semantic paper search (conceptual matching)",
                "• Topic clustering (convex hull boundaries)",
                "• Similar paper discovery & graph edge calculation",
                "• Faster, private research navigation"
            };
            for (String f : features) {
                Label bullet = new Label(f);
                bullet.setStyle("-fx-font-size: 11px; -fx-text-fill: #2f4ba9; -fx-font-weight: bold;");
                bulletList.getChildren().add(bullet);
            }

            Label dlSize = new Label("Download Size: ~580 MB");
            dlSize.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #28a745; -fx-padding: 4 0 0 0;");

            layout.getChildren().addAll(title, desc, bulletList, dlSize);
            pane.setContent(layout);

            ButtonType downloadType = new ButtonType("Download & Enable", ButtonBar.ButtonData.OK_DONE);
            ButtonType notNowType = new ButtonType("Not Now", ButtonBar.ButtonData.CANCEL_CLOSE);
            pane.getButtonTypes().addAll(downloadType, notNowType);

            // Style buttons to look premium
            Button dlBtn = (Button) pane.lookupButton(downloadType);
            dlBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 11px; " +
                    "-fx-font-weight: bold; -fx-padding: 7 18; -fx-background-radius: 6; -fx-cursor: hand;");

            Button notNowBtn = (Button) pane.lookupButton(notNowType);
            notNowBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 11px; " +
                    "-fx-padding: 7 18; -fx-background-radius: 6; -fx-cursor: hand;");

            dialog.setResultConverter(bt -> {
                if (bt == downloadType) {
                    return true;
                }
                return false;
            });

            dialog.showAndWait().ifPresent(accepted -> {
                // Record that we prompted them so we never prompt again unexpectedly
                GeminiConfig.setEmbeddingPrompted(true);
                if (accepted) {
                    // Activate BGE-M3 and begin dynamic download asynchronously
                    GeminiConfig.setEmbeddingProvider("bgem3");
                    EmbeddingService.getInstance().getDownloader().start();
                    System.out.println("[AI Prompt] User activated BGE-M3. Downloader started in background.");
                }
            });
        });
    }
}
