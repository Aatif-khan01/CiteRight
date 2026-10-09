package com.citeright.ui;

import com.citeright.ai.BgeM3ModelDownloader;
import com.citeright.ai.BgeM3EmbeddingEngine;
import com.citeright.ai.EmbeddingQueueManager;
import com.citeright.ai.EmbeddingService;
import com.citeright.ai.GeminiAIService;
import com.citeright.ai.GeminiConfig;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/**
 * Settings dialog for AI provider configuration.
 * Configures Google Gemini (cloud), Ollama (local generative), and BGE-M3 (local neural embeddings).
 * Includes the Dynamic Downloader and the AI Resource Manager.
 */
public class AISettingsDialog extends Dialog<Void> {

    private final ToggleGroup providerGroup;
    private final RadioButton geminiRadio;
    private final RadioButton ollamaRadio;
    private final TextField apiKeyField;
    private final TextField ollamaModelField;
    private final TextField ollamaUrlField;
    private final Label statusLabel;
    
    // ── Embedding Section Fields ──
    private final ToggleGroup embeddingGroup;
    private final RadioButton tfidfRadio;
    private final RadioButton bgem3Radio;
    private final VBox bgem3Card;
    
    // Downloader Visuals
    private final Label dlTitleLabel;
    private final ProgressBar dlProgressBar;
    private final Label dlPercentLabel;
    private final Label dlSpeedLabel;
    private final Label dlTimeLabel;
    private final Button dlStartBtn;
    private final Button dlPauseBtn;
    private final Button dlResumeBtn;
    private final Button dlCancelBtn;

    // AI Resource Manager Visuals
    private final VBox resourceManagerCard;
    private final Label resStatusLabel;
    private final Label resIndexedLabel;
    private final Label resQueueLabel;
    private final Button forceIndexBtn;

    private Runnable onSaved;
    private final EmbeddingService embeddingService;
    private final BgeM3ModelDownloader downloader;

    public AISettingsDialog() {
        this.embeddingService = EmbeddingService.getInstance();
        this.downloader = embeddingService.getDownloader();

        providerGroup = new ToggleGroup();
        geminiRadio = new RadioButton("☁  Google Gemini (Free Cloud AI)");
        ollamaRadio = new RadioButton("🖥  Ollama (Local & Private)");
        apiKeyField = new TextField();
        ollamaModelField = new TextField();
        ollamaUrlField = new TextField();
        statusLabel = new Label();

        // Embeddings Provider Controls
        embeddingGroup = new ToggleGroup();
        tfidfRadio = new RadioButton("⚡ Local TF-IDF (Fast & Lightweight)");
        bgem3Radio = new RadioButton("🧠 Local Neural - BGE-M3 (Premium Offline Semantics)");
        bgem3Card = new VBox(10);

        // Downloader Controls
        dlTitleLabel = new Label("Dynamic Downloader");
        dlProgressBar = new ProgressBar(0);
        dlPercentLabel = new Label("0%");
        dlSpeedLabel = new Label("0.0 MB/s");
        dlTimeLabel = new Label("ETA: calculating...");
        dlStartBtn = new Button("📥 Download & Enable Neural AI (~580 MB)");
        dlPauseBtn = new Button("⏸ Pause");
        dlResumeBtn = new Button("▶ Resume");
        dlCancelBtn = new Button("❌ Cancel");

        // Resource Manager Controls
        resourceManagerCard = new VBox(8);
        resStatusLabel = new Label("Status: Loading...");
        resIndexedLabel = new Label("Indexed Papers: 0 / 0");
        resQueueLabel = new Label("Queue Remaining: 0");
        forceIndexBtn = new Button("🔄 Re-index All Papers");

        buildUI();
        loadCurrentSettings();
        setupDownloaderListeners();
        refreshEmbeddingUI();
    }

    public void setOnSaved(Runnable handler) { this.onSaved = handler; }

    private void buildUI() {
        setTitle("AI Settings & Resource Manager");
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(null);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.setPrefWidth(520);
        pane.setPrefHeight(680);
        pane.setMinHeight(600);
        pane.setStyle("-fx-background-color: #f8f8fc;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 10, 24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        // ── Title Header ──
        Label header = new Label("🤖 CiteRight AI Settings");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label desc = new Label("Configure your local and cloud AI providers. Local features run natively and preserve complete privacy.");
        desc.setStyle("-fx-text-fill: #6a6a8a; -fx-font-size: 11px;");
        desc.setWrapText(true);

        // ── PART 1: CHAT LLM PROVIDER ─────────────────────────────────────────
        Label chatSectionTitle = new Label("1. Generative AI & Chat Provider");
        chatSectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4a6cf7;");

        geminiRadio.setToggleGroup(providerGroup);
        geminiRadio.setStyle("-fx-font-size: 12px; -fx-text-fill: #2a2a3e; -fx-font-weight: bold;");
        ollamaRadio.setToggleGroup(providerGroup);
        ollamaRadio.setStyle("-fx-font-size: 12px; -fx-text-fill: #2a2a3e; -fx-font-weight: bold;");

        VBox geminiSection = new VBox(6);
        geminiSection.setPadding(new Insets(6, 0, 6, 24));
        Label apiKeyLabel = new Label("API Key:");
        apiKeyLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        apiKeyField.setPromptText("Paste your Gemini API key here...");
        apiKeyField.setStyle("-fx-font-size: 11px; -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #d4d4e0;");
        Hyperlink getKeyLink = new Hyperlink("🔗 Get free API key from Google AI Studio");
        getKeyLink.setStyle("-fx-font-size: 10px; -fx-text-fill: #4a6cf7;");
        getKeyLink.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://aistudio.google.com/apikey")); } catch (Exception ignored) {}
        });
        geminiSection.getChildren().addAll(apiKeyLabel, apiKeyField, getKeyLink);

        VBox ollamaSection = new VBox(6);
        ollamaSection.setPadding(new Insets(6, 0, 6, 24));
        Label modelLabel = new Label("Model:");
        modelLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        ollamaModelField.setPromptText("e.g. llama3.2, mistral");
        ollamaModelField.setStyle("-fx-font-size: 11px; -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #d4d4e0;");
        Label urlLabel = new Label("Server URL:");
        urlLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        ollamaUrlField.setPromptText("http://localhost:11434");
        ollamaUrlField.setStyle("-fx-font-size: 11px; -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #d4d4e0;");
        Button testBtn = new Button("🔍 Test Connection");
        testBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 10px; -fx-padding: 4 12; -fx-background-radius: 4; -fx-cursor: hand;");
        testBtn.setOnAction(e -> testOllamaConnection());
        ollamaSection.getChildren().addAll(modelLabel, ollamaModelField, urlLabel, ollamaUrlField, testBtn);

        providerGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            boolean isGemini = sel == geminiRadio;
            geminiSection.setVisible(isGemini);
            geminiSection.setManaged(isGemini);
            ollamaSection.setVisible(!isGemini);
            ollamaSection.setManaged(!isGemini);
        });

        // ── PART 2: EMBEDDINGS & SEMANTICS ────────────────────────────────────
        Label embedSectionTitle = new Label("2. Local Semantic & Vector Search Engine");
        embedSectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4a6cf7;");

        tfidfRadio.setToggleGroup(embeddingGroup);
        tfidfRadio.setStyle("-fx-font-size: 12px; -fx-text-fill: #2a2a3e; -fx-font-weight: bold;");
        bgem3Radio.setToggleGroup(embeddingGroup);
        bgem3Radio.setStyle("-fx-font-size: 12px; -fx-text-fill: #2a2a3e; -fx-font-weight: bold;");

        // Styling the BGE-M3 Downloader & Manager Card
        bgem3Card.setPadding(new Insets(10, 14, 10, 14));
        bgem3Card.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #dcdce6; -fx-border-radius: 8; -fx-border-width: 1;");
        bgem3Card.setVisible(false);
        bgem3Card.setManaged(false);

        // Progress bar styling
        dlProgressBar.setPrefWidth(380);
        dlProgressBar.setStyle("-fx-accent: #4a6cf7;");
        dlPercentLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        dlSpeedLabel.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #5a5a7a;");
        dlTimeLabel.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #5a5a7a;");
        dlTitleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2a2a3e;");

        HBox progressInfo = new HBox(8, dlTitleLabel, dlPercentLabel, new Region(), dlSpeedLabel, dlTimeLabel);
        HBox.setHgrow(progressInfo.getChildren().get(2), Priority.ALWAYS);
        progressInfo.setAlignment(Pos.CENTER_LEFT);

        HBox dlButtons = new HBox(8, dlStartBtn, dlPauseBtn, dlResumeBtn, dlCancelBtn);
        dlButtons.setAlignment(Pos.CENTER_LEFT);

        dlStartBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 10.5px; -fx-padding: 6 16; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-weight: bold;");
        dlStartBtn.setOnAction(e -> downloader.start());

        dlPauseBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 10.5px; -fx-padding: 6 16; -fx-background-radius: 5; -fx-cursor: hand;");
        dlPauseBtn.setOnAction(e -> downloader.pause());

        dlResumeBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 10.5px; -fx-padding: 6 16; -fx-background-radius: 5; -fx-cursor: hand;");
        dlResumeBtn.setOnAction(e -> downloader.resume());

        dlCancelBtn.setStyle("-fx-background-color: #fce8e6; -fx-text-fill: #cc3333; -fx-font-size: 10.5px; -fx-padding: 6 16; -fx-background-radius: 5; -fx-cursor: hand;");
        dlCancelBtn.setOnAction(e -> downloader.cancel());

        // AI Resource Manager Dashboard Card
        resourceManagerCard.setPadding(new Insets(10));
        resourceManagerCard.setStyle("-fx-background-color: #f0f4fe; -fx-background-radius: 6;");
        Label resTitle = new Label("🖥 AI Resource Manager Dashboard");
        resTitle.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #2f4ba9;");
        
        resStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #3a3a5e;");
        resIndexedLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #3a3a5e;");
        resQueueLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #3a3a5e;");
        forceIndexBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #4a6cf7; -fx-font-size: 9.5px; -fx-padding: 4 10; -fx-border-color: #4a6cf7; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        forceIndexBtn.setOnAction(e -> {
            EmbeddingQueueManager.getInstance().enqueueAllUnindexedPapers();
            refreshResourceManager();
        });

        resourceManagerCard.getChildren().addAll(resTitle, resStatusLabel, resIndexedLabel, resQueueLabel, forceIndexBtn);

        bgem3Card.getChildren().addAll(progressInfo, dlProgressBar, dlButtons, resourceManagerCard);

        embeddingGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            boolean isBge = sel == bgem3Radio;
            bgem3Card.setVisible(isBge);
            bgem3Card.setManaged(isBge);
            if (isBge) {
                refreshEmbeddingUI();
                refreshResourceManager();
            }
        });

        Separator sep1 = new Separator();
        Separator sep2 = new Separator();

        content.getChildren().addAll(
                header, desc, sep1,
                chatSectionTitle,
                geminiRadio, geminiSection,
                ollamaRadio, ollamaSection,
                sep2,
                embedSectionTitle,
                tfidfRadio,
                bgem3Radio, bgem3Card,
                statusLabel
        );

        pane.setContent(scrollPane);

        // ── Save and Cancel Buttons ──
        ButtonType saveType = new ButtonType("💾 Save Settings", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveType, cancelType);

        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; " +
                "-fx-font-size: 12px; -fx-padding: 8 20; -fx-background-radius: 6; -fx-font-weight: bold; -fx-cursor: hand;");

        setResultConverter(bt -> {
            if (bt == saveType) {
                saveSettings();
            }
            return null;
        });
    }

    private void loadCurrentSettings() {
        // Chat Provider
        if (GeminiConfig.isOllama()) ollamaRadio.setSelected(true);
        else geminiRadio.setSelected(true);

        apiKeyField.setText(GeminiConfig.getApiKey() != null ? GeminiConfig.getApiKey() : "");
        ollamaModelField.setText(GeminiConfig.getOllamaModel());
        ollamaUrlField.setText(GeminiConfig.getOllamaUrl());

        // Embedding Provider
        if (GeminiConfig.isBgeM3()) bgem3Radio.setSelected(true);
        else tfidfRadio.setSelected(true);
    }

    private void saveSettings() {
        String provider = geminiRadio.isSelected() ? "gemini" : "ollama";
        GeminiConfig.setProvider(provider);
        GeminiConfig.saveApiKey(apiKeyField.getText());

        String oModel = ollamaModelField.getText().trim();
        if (!oModel.isEmpty()) GeminiConfig.setOllamaModel(oModel);
        String oUrl = ollamaUrlField.getText().trim();
        if (!oUrl.isEmpty()) GeminiConfig.setOllamaUrl(oUrl);

        // Save Embedding Provider
        String embedProvider = bgem3Radio.isSelected() ? "bgem3" : "tfidf";
        GeminiConfig.setEmbeddingProvider(embedProvider);

        if ("bgem3".equals(embedProvider)) {
            // Asynchronously load the engine if downloaded
            embeddingService.initializeEngine();
        }

        statusLabel.setText("✅ AI Settings successfully saved!");
        statusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");

        if (onSaved != null) onSaved.run();
    }

    private void setupDownloaderListeners() {
        downloader.setListener(new BgeM3ModelDownloader.DownloadListener() {
            @Override
            public void onProgress(double percent, double speedMBs, String activeFile, long downloadedBytes, long totalBytes) {
                Platform.runLater(() -> {
                    dlProgressBar.setProgress(percent);
                    dlPercentLabel.setText(Math.round(percent * 100) + "%");
                    dlSpeedLabel.setText(String.format("%.1f MB/s", speedMBs));
                    dlTitleLabel.setText("Downloading: " + activeFile);

                    long remaining = totalBytes - downloadedBytes;
                    double secRemaining = speedMBs > 0 ? (remaining / (1024.0 * 1024.0)) / speedMBs : 0;
                    if (secRemaining > 0) {
                        dlTimeLabel.setText(String.format("ETA: %d min %d sec", (int)(secRemaining / 60), (int)(secRemaining % 60)));
                    } else {
                        dlTimeLabel.setText("ETA: calculating...");
                    }
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    dlTitleLabel.setText("Local Neural Engine Ready");
                    dlPercentLabel.setText("100%");
                    dlProgressBar.setProgress(1.0);
                    dlSpeedLabel.setText("Idle");
                    dlTimeLabel.setText("BGE-M3 Active");
                    
                    statusLabel.setText("✅ Neural pack downloaded and active!");
                    statusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
                    
                    // Asynchronously load model & trigger indexer
                    embeddingService.initializeEngine();
                    
                    refreshEmbeddingUI();
                    refreshResourceManager();
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    dlTitleLabel.setText("❌ Download failed");
                    dlTimeLabel.setText("Retry download");
                    statusLabel.setText("❌ Download error: " + errorMessage);
                    statusLabel.setStyle("-fx-text-fill: #cc3333; -fx-font-size: 11px;");
                    refreshEmbeddingUI();
                });
            }

            @Override
            public void onStateChanged(BgeM3ModelDownloader.State newState) {
                Platform.runLater(() -> {
                    updateDownloaderButtons(newState);
                    refreshResourceManager();
                });
            }
        });

        // Initialize button visibilities
        updateDownloaderButtons(downloader.getState());
    }

    private void refreshEmbeddingUI() {
        if (downloader.isDownloaded()) {
            dlTitleLabel.setText("✅ Local Semantic AI Pack Installed");
            dlPercentLabel.setText("100%");
            dlProgressBar.setProgress(1.0);
            dlSpeedLabel.setText("In-Process CPU mode");
            dlTimeLabel.setText("Model loaded: " + (BgeM3EmbeddingEngine.getInstance().isLoaded() ? "Yes" : "Waiting for initialize"));
            
            dlStartBtn.setVisible(false);
            dlStartBtn.setManaged(false);
            dlPauseBtn.setVisible(false);
            dlPauseBtn.setManaged(false);
            dlResumeBtn.setVisible(false);
            dlResumeBtn.setManaged(false);
            dlCancelBtn.setVisible(false);
            dlCancelBtn.setManaged(false);
        } else {
            updateDownloaderButtons(downloader.getState());
        }
    }

    private void updateDownloaderButtons(BgeM3ModelDownloader.State state) {
        if (downloader.isDownloaded()) return;

        boolean isDownloading = state == BgeM3ModelDownloader.State.DOWNLOADING;
        boolean isPaused = state == BgeM3ModelDownloader.State.PAUSED;
        boolean isIdle = state == BgeM3ModelDownloader.State.IDLE || state == BgeM3ModelDownloader.State.ERROR;

        dlStartBtn.setVisible(isIdle);
        dlStartBtn.setManaged(isIdle);
        
        dlPauseBtn.setVisible(isDownloading);
        dlPauseBtn.setManaged(isDownloading);
        
        dlResumeBtn.setVisible(isPaused);
        dlResumeBtn.setManaged(isPaused);
        
        dlCancelBtn.setVisible(isDownloading || isPaused);
        dlCancelBtn.setManaged(isDownloading || isPaused);

        if (isIdle) {
            dlTitleLabel.setText("Dynamic Downloader");
            dlPercentLabel.setText("0%");
            dlProgressBar.setProgress(0);
            dlSpeedLabel.setText("Not Running");
            dlTimeLabel.setText("Click to download (~580MB)");
        } else if (isPaused) {
            dlTitleLabel.setText("⏸ Download Paused");
            dlSpeedLabel.setText("Paused");
        }
    }

    private void refreshResourceManager() {
        if (!bgem3Radio.isSelected()) return;

        new Thread(() -> {
            BgeM3EmbeddingEngine engine = BgeM3EmbeddingEngine.getInstance();
            EmbeddingQueueManager queue = EmbeddingQueueManager.getInstance();
            com.citeright.database.PaperDAO paperDAO = new com.citeright.database.PaperDAO();
            com.citeright.database.PaperEmbeddingDAO embedDAO = new com.citeright.database.PaperEmbeddingDAO();

            int totalPapers = paperDAO.getCachedPaperCount();
            int indexedPapers = embedDAO.getAllCachedEmbeddings("bge-m3", "v1").size();
            int queueRemaining = queue.getQueueSize();

            String statusStr = "Status: ";
            if (!downloader.isDownloaded()) {
                statusStr += "❌ Semantic pack not installed.";
            } else if (!engine.isLoaded()) {
                statusStr += "⏳ Model exists on disk. Ready for first semantic query.";
            } else if (queue.isIndexing()) {
                statusStr += "⚙ Background indexing active.";
            } else {
                statusStr += "🟢 Model loaded and idle.";
            }

            final String finalStatus = statusStr;
            final int finalTotal = totalPapers;
            final int finalIndexed = indexedPapers;
            final int finalQueue = queueRemaining;

            Platform.runLater(() -> {
                resStatusLabel.setText(finalStatus);
                resIndexedLabel.setText(String.format("Indexed Papers: %d / %d (%.1f%%)",
                        finalIndexed, finalTotal, finalTotal > 0 ? (finalIndexed * 100.0 / finalTotal) : 0));
                resQueueLabel.setText("Background Queue Remaining: " + finalQueue);
                
                boolean indexingCapable = downloader.isDownloaded() && engine.isLoaded();
                forceIndexBtn.setVisible(indexingCapable);
                forceIndexBtn.setManaged(indexingCapable);
            });
        }).start();
    }

    private void testOllamaConnection() {
        statusLabel.setText("Testing connection...");
        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");

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
