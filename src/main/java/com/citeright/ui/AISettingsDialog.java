package com.citeright.ui;

import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/**
 * Settings dialog for AI provider configuration.
 * Supports Google Gemini (cloud, free tier) and Ollama (local, private).
 */
public class AISettingsDialog extends Dialog<Void> {

    private final ToggleGroup providerGroup;
    private final RadioButton geminiRadio;
    private final RadioButton ollamaRadio;
    private final TextField apiKeyField;
    private final TextField ollamaModelField;
    private final TextField ollamaUrlField;
    private final Label statusLabel;
    private Runnable onSaved;

    public AISettingsDialog() {
        providerGroup = new ToggleGroup();
        geminiRadio = new RadioButton("☁️  Google Gemini (Free Cloud AI)");
        ollamaRadio = new RadioButton("🖥️  Ollama (Local & Private)");
        apiKeyField = new TextField();
        ollamaModelField = new TextField();
        ollamaUrlField = new TextField();
        statusLabel = new Label();
        buildUI();
        loadCurrentSettings();
    }

    public void setOnSaved(Runnable handler) { this.onSaved = handler; }

    private void buildUI() {
        setTitle("AI Settings");
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(null);

        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.setPrefWidth(480);
        pane.setPrefHeight(520);
        pane.setMinHeight(480);
        pane.setStyle("-fx-background-color: #f8f8fc;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 10, 24));

        // ── Header ───────────────────────────────────────────────────────────
        Label header = new Label("🤖 AI Provider Settings");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label desc = new Label("Choose your AI provider. Gemini is free and cloud-based.\nOllama runs locally for complete privacy.");
        desc.setStyle("-fx-text-fill: #6a6a8a; -fx-font-size: 11px;");
        desc.setWrapText(true);

        // ── Provider Selection ───────────────────────────────────────────────
        geminiRadio.setToggleGroup(providerGroup);
        geminiRadio.setStyle("-fx-font-size: 13px; -fx-text-fill: #2a2a3e;");
        ollamaRadio.setToggleGroup(providerGroup);
        ollamaRadio.setStyle("-fx-font-size: 13px; -fx-text-fill: #2a2a3e;");

        // ── Gemini Section ───────────────────────────────────────────────────
        VBox geminiSection = new VBox(8);
        geminiSection.setPadding(new Insets(10, 0, 0, 24));

        Label apiKeyLabel = new Label("API Key:");
        apiKeyLabel.setStyle("-fx-text-fill: #4a4a6a; -fx-font-size: 11px; -fx-font-weight: bold;");

        apiKeyField.setPromptText("Paste your Gemini API key here...");
        apiKeyField.setStyle("-fx-font-size: 12px; -fx-padding: 8 12; -fx-background-radius: 6; " +
                "-fx-border-radius: 6; -fx-border-color: #d4d4e0;");

        Hyperlink getKeyLink = new Hyperlink("🔗 Get free API key from Google AI Studio");
        getKeyLink.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #4a6cf7;");
        getKeyLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://aistudio.google.com/apikey"));
            } catch (Exception ex) {
                statusLabel.setText("Open: https://aistudio.google.com/apikey");
            }
        });

        Label geminiNote = new Label("💡 Free tier: 15 requests/min, no credit card required.");
        geminiNote.setStyle("-fx-text-fill: #28a745; -fx-font-size: 10px;");

        geminiSection.getChildren().addAll(apiKeyLabel, apiKeyField, getKeyLink, geminiNote);

        // ── Ollama Section ───────────────────────────────────────────────────
        VBox ollamaSection = new VBox(8);
        ollamaSection.setPadding(new Insets(10, 0, 0, 24));

        Label modelLabel = new Label("Model:");
        modelLabel.setStyle("-fx-text-fill: #4a4a6a; -fx-font-size: 11px; -fx-font-weight: bold;");
        ollamaModelField.setPromptText("e.g. llama3.2, mistral, gemma2");
        ollamaModelField.setStyle("-fx-font-size: 12px; -fx-padding: 8 12; -fx-background-radius: 6; " +
                "-fx-border-radius: 6; -fx-border-color: #d4d4e0;");

        Label urlLabel = new Label("Server URL:");
        urlLabel.setStyle("-fx-text-fill: #4a4a6a; -fx-font-size: 11px; -fx-font-weight: bold;");
        ollamaUrlField.setPromptText("http://localhost:11434");
        ollamaUrlField.setStyle("-fx-font-size: 12px; -fx-padding: 8 12; -fx-background-radius: 6; " +
                "-fx-border-radius: 6; -fx-border-color: #d4d4e0;");

        Button testBtn = new Button("🔍 Test Connection");
        testBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 11px; " +
                "-fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
        testBtn.setOnAction(e -> testOllamaConnection());

        Label ollamaNote = new Label("🔒 100% private — your data never leaves your computer.");
        ollamaNote.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 10px;");

        ollamaSection.getChildren().addAll(modelLabel, ollamaModelField, urlLabel, ollamaUrlField, testBtn, ollamaNote);

        // ── Toggle visibility ────────────────────────────────────────────────
        providerGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            boolean isGemini = sel == geminiRadio;
            geminiSection.setVisible(isGemini);
            geminiSection.setManaged(isGemini);
            ollamaSection.setVisible(!isGemini);
            ollamaSection.setManaged(!isGemini);
        });

        // ── Status ───────────────────────────────────────────────────────────
        statusLabel.setStyle("-fx-font-size: 11px; -fx-padding: 8 0 0 0;");
        statusLabel.setWrapText(true);

        // ── Separator ────────────────────────────────────────────────────────
        Separator sep = new Separator();

        content.getChildren().addAll(header, desc, sep,
                geminiRadio, geminiSection,
                ollamaRadio, ollamaSection,
                statusLabel);

        pane.setContent(content);

        // ── Buttons ──────────────────────────────────────────────────────────
        ButtonType saveType = new ButtonType("💾 Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveType, cancelType);

        // Style save button
        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; " +
                "-fx-font-size: 12px; -fx-padding: 8 20; -fx-background-radius: 6; -fx-font-weight: bold;");

        setResultConverter(bt -> {
            if (bt == saveType) {
                saveSettings();
            }
            return null;
        });
    }

    private void loadCurrentSettings() {
        // Provider
        if (GeminiConfig.isOllama()) {
            ollamaRadio.setSelected(true);
        } else {
            geminiRadio.setSelected(true);
        }

        // Gemini
        String key = GeminiConfig.getApiKey();
        if (key != null) apiKeyField.setText(key);

        // Ollama
        ollamaModelField.setText(GeminiConfig.getOllamaModel());
        ollamaUrlField.setText(GeminiConfig.getOllamaUrl());
    }

    private void saveSettings() {
        // Save provider
        String provider = geminiRadio.isSelected() ? "gemini" : "ollama";
        GeminiConfig.setProvider(provider);

        // Save Gemini key
        GeminiConfig.saveApiKey(apiKeyField.getText());

        // Save Ollama settings
        String model = ollamaModelField.getText().trim();
        if (!model.isEmpty()) GeminiConfig.setOllamaModel(model);

        String url = ollamaUrlField.getText().trim();
        if (!url.isEmpty()) GeminiConfig.setOllamaUrl(url);

        statusLabel.setText("✅ Settings saved!");
        statusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");

        if (onSaved != null) onSaved.run();
    }

    private void testOllamaConnection() {
        statusLabel.setText("Testing connection...");
        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");

        // Save URL temporarily for the test
        String testUrl = ollamaUrlField.getText().trim();
        if (testUrl.isEmpty()) testUrl = "http://localhost:11434";
        GeminiConfig.setOllamaUrl(testUrl);

        new Thread(() -> {
            GeminiAIService service = new GeminiAIService();
            boolean ok = service.testOllamaConnection();
            Platform.runLater(() -> {
                if (ok) {
                    statusLabel.setText("✅ Ollama is running and reachable!");
                    statusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
                } else {
                    statusLabel.setText("❌ Cannot reach Ollama. Make sure it's running: ollama serve");
                    statusLabel.setStyle("-fx-text-fill: #cc3333; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
                }
            });
        }).start();
    }
}
