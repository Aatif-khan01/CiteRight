package com.citeright.ui;

import com.citeright.ai.GeminiConfig;
import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.service.LibraryChatService;
import com.citeright.service.LibraryService;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Premium chat interface for "Chat with Your Library" (RAG).
 * Beautiful message bubbles, typing indicator, and source references.
 */
public class ChatPane extends VBox {

    private final LibraryChatService chatService;
    private final VBox messagesContainer;
    private final ScrollPane messagesScroll;
    private final TextField inputField;
    private final Button sendBtn;
    private final Label typingIndicator;
    private Runnable onSettingsClick;

    public ChatPane(LibraryService libraryService) {
        this.chatService = new LibraryChatService(libraryService);
        this.messagesContainer = new VBox(12);
        this.messagesScroll = new ScrollPane();
        this.inputField = new TextField();
        this.sendBtn = new Button("Send");
        this.typingIndicator = new Label();
        buildUI();
    }

    public void setOnSettingsClick(Runnable handler) { this.onSettingsClick = handler; }

    private void buildUI() {
        setStyle("-fx-background-color: #f4f4f8;");
        setSpacing(0);

        // ── Top Bar ──────────────────────────────────────────────────────────
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 20, 10, 20));
        topBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e8e8f0; -fx-border-width: 0 0 1 0;");

        Label titleIcon = new Label("🤖");
        titleIcon.setStyle("-fx-font-size: 20px;");

        VBox titleBox = new VBox(1);
        Label title = new Label("AI Research Assistant");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        Label subtitle = new Label(GeminiConfig.isGemini() ? "Powered by Google Gemini" : "Powered by Ollama (Local)");
        subtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #8888aa;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button settingsBtn = new Button("⚙");
        settingsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; " +
                "-fx-font-size: 18px; -fx-cursor: hand; -fx-padding: 4 8;");
        settingsBtn.setTooltip(new Tooltip("AI Settings — Configure API Key & Provider"));
        settingsBtn.setOnAction(e -> { if (onSettingsClick != null) onSettingsClick.run(); });

        Button clearBtn = new Button("🗑 Clear");
        clearBtn.setStyle("-fx-background-color: #f0f0f4; -fx-text-fill: #666; " +
                "-fx-font-size: 11px; -fx-padding: 5 10; -fx-background-radius: 6; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> clearChat());

        topBar.getChildren().addAll(titleIcon, titleBox, spacer, clearBtn, settingsBtn);

        // ── Messages Area ────────────────────────────────────────────────────
        messagesContainer.setPadding(new Insets(20, 20, 20, 20));
        messagesContainer.setAlignment(Pos.TOP_LEFT);

        messagesScroll.setContent(messagesContainer);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messagesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messagesScroll.setStyle("-fx-background: #f4f4f8; -fx-background-color: #f4f4f8; -fx-border-color: transparent;");
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);

        // Show welcome message
        showWelcome();

        // ── Typing Indicator ─────────────────────────────────────────────────
        typingIndicator.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11px; -fx-padding: 0 20;");
        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);

        // ── Input Area ───────────────────────────────────────────────────────
        HBox inputBar = new HBox(10);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(12, 20, 14, 20));
        inputBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e8e8f0; -fx-border-width: 1 0 0 0;");

        inputField.setPromptText("Ask anything about your papers...");
        inputField.setStyle("-fx-font-size: 13px; -fx-padding: 10 16; -fx-background-radius: 20; " +
                "-fx-border-radius: 20; -fx-border-color: #d4d4e0; -fx-background-color: #f8f8fc;");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !inputField.getText().isBlank()) {
                handleSend();
            }
        });

        sendBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 13px; " +
                "-fx-padding: 10 20; -fx-background-radius: 20; -fx-cursor: hand; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> handleSend());

        inputBar.getChildren().addAll(inputField, sendBtn);

        getChildren().addAll(topBar, messagesScroll, typingIndicator, inputBar);
    }

    private void showWelcome() {
        messagesContainer.getChildren().clear();

        VBox welcome = new VBox(12);
        welcome.setAlignment(Pos.CENTER);
        welcome.setPadding(new Insets(60, 40, 40, 40));

        Label emoji = new Label("🧠");
        emoji.setStyle("-fx-font-size: 40px;");

        Label welcomeTitle = new Label("Chat with Your Library");
        welcomeTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label welcomeDesc = new Label("Ask questions about your saved papers.\nI'll find the relevant ones and give you answers with citations.");
        welcomeDesc.setStyle("-fx-text-fill: #6a6a8a; -fx-font-size: 12px;");
        welcomeDesc.setTextAlignment(TextAlignment.CENTER);
        welcomeDesc.setWrapText(true);

        // Example queries
        VBox examples = new VBox(8);
        examples.setAlignment(Pos.CENTER);
        examples.setPadding(new Insets(16, 0, 0, 0));

        Label exTitle = new Label("Try asking:");
        exTitle.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 10px; -fx-font-weight: bold;");

        String[] exampleQueries = {
            "Which papers discuss machine learning?",
            "Summarize the methodologies used in my papers",
            "Compare the approaches in my library",
            "What are the key findings across my papers?"
        };

        for (String q : exampleQueries) {
            Button exBtn = new Button("💬  " + q);
            exBtn.setMaxWidth(400);
            exBtn.setAlignment(Pos.CENTER_LEFT);
            exBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #4a6cf7; -fx-font-size: 11.5px; " +
                    "-fx-padding: 10 16; -fx-background-radius: 10; -fx-border-color: #e8e8f0; " +
                    "-fx-border-radius: 10; -fx-cursor: hand;");
            exBtn.setOnMouseEntered(e -> exBtn.setStyle(exBtn.getStyle().replace("#ffffff", "#f0f0ff")));
            exBtn.setOnMouseExited(e -> exBtn.setStyle(exBtn.getStyle().replace("#f0f0ff", "#ffffff")));
            exBtn.setOnAction(e -> {
                inputField.setText(q);
                handleSend();
            });
            examples.getChildren().add(exBtn);
        }

        examples.getChildren().addFirst(exTitle);
        welcome.getChildren().addAll(emoji, welcomeTitle, welcomeDesc, examples);
        messagesContainer.getChildren().add(welcome);
    }

    private void clearChat() {
        showWelcome();
    }

    // ── Send Message ─────────────────────────────────────────────────────────

    private void handleSend() {
        String question = inputField.getText().trim();
        if (question.isEmpty()) return;

        // Remove welcome if still showing
        if (messagesContainer.getChildren().size() == 1 &&
            messagesContainer.getChildren().get(0) instanceof VBox) {
            messagesContainer.getChildren().clear();
        }

        // Add user message bubble
        addUserBubble(question);
        inputField.clear();
        inputField.setDisable(true);
        sendBtn.setDisable(true);

        // Show typing indicator
        showTyping(true);

        // Run AI query on background thread
        new Thread(() -> {
            LibraryChatService.ChatResponse response = chatService.ask(question);

            Platform.runLater(() -> {
                showTyping(false);
                addAiBubble(response.answer(), response.contextPapers());
                inputField.setDisable(false);
                sendBtn.setDisable(false);
                inputField.requestFocus();

                // Auto-scroll to bottom
                messagesScroll.layout();
                messagesScroll.setVvalue(1.0);
            });
        }).start();
    }

    // ── Message Bubbles ──────────────────────────────────────────────────────

    private void addUserBubble(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(0, 0, 0, 60));

        VBox bubble = new VBox(4);
        bubble.setPadding(new Insets(10, 16, 10, 16));
        bubble.setMaxWidth(500);
        bubble.setStyle("-fx-background-color: #4a6cf7; -fx-background-radius: 16 16 4 16;");

        Label msgLabel = new Label(text);
        msgLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13px;");
        msgLabel.setWrapText(true);

        bubble.getChildren().add(msgLabel);
        row.getChildren().add(bubble);
        messagesContainer.getChildren().add(row);
    }

    private void addAiBubble(String text, List<LibraryEntry> contextPapers) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 60, 0, 0));

        VBox bubble = new VBox(8);
        bubble.setPadding(new Insets(12, 16, 12, 16));
        bubble.setMaxWidth(550);
        bubble.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 16 16 16 4; " +
                "-fx-border-color: #e8e8f0; -fx-border-radius: 16 16 16 4; -fx-border-width: 1;");

        // AI icon + label
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Label aiIcon = new Label("🤖");
        aiIcon.setStyle("-fx-font-size: 12px;");
        Label aiLabel = new Label("CiteRight AI");
        aiLabel.setStyle("-fx-text-fill: #4a6cf7; -fx-font-size: 10px; -fx-font-weight: bold;");
        header.getChildren().addAll(aiIcon, aiLabel);

        // Response text
        Label responseLabel = new Label(text);
        responseLabel.setStyle("-fx-text-fill: #2a2a3e; -fx-font-size: 12.5px; -fx-line-spacing: 3;");
        responseLabel.setWrapText(true);

        bubble.getChildren().addAll(header, responseLabel);

        // Source references
        if (contextPapers != null && !contextPapers.isEmpty()) {
            Separator sep = new Separator();
            sep.setStyle("-fx-background-color: #eeeef4;");

            Label sourcesLabel = new Label("📚 Sources used (" + contextPapers.size() + " papers):");
            sourcesLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 9.5px; -fx-font-weight: bold;");

            VBox sourcesList = new VBox(3);
            for (int i = 0; i < contextPapers.size(); i++) {
                Publication pub = contextPapers.get(i).getPublication();
                String sourceText = "[" + (i + 1) + "] " +
                        (pub.getTitle() != null ? pub.getTitle() : "Untitled") +
                        (pub.getYear() > 0 ? " (" + pub.getYear() + ")" : "");
                Label sourceItem = new Label(sourceText);
                sourceItem.setStyle("-fx-text-fill: #5a5a8a; -fx-font-size: 10px;");
                sourceItem.setWrapText(true);
                sourcesList.getChildren().add(sourceItem);
            }

            bubble.getChildren().addAll(sep, sourcesLabel, sourcesList);
        }

        row.getChildren().add(bubble);
        messagesContainer.getChildren().add(row);
    }

    private void showTyping(boolean show) {
        typingIndicator.setText("🤖 Analyzing your papers...");
        typingIndicator.setVisible(show);
        typingIndicator.setManaged(show);
    }

    /** Refreshes the provider subtitle label (called after settings change) */
    public void refreshProviderLabel() {
        // Walk the top bar to find the subtitle label
        if (!getChildren().isEmpty() && getChildren().get(0) instanceof HBox topBar) {
            for (var node : topBar.getChildren()) {
                if (node instanceof VBox titleBox) {
                    for (var child : titleBox.getChildren()) {
                        if (child instanceof Label label && label.getText() != null &&
                            (label.getText().contains("Gemini") || label.getText().contains("Ollama"))) {
                            label.setText(GeminiConfig.isGemini() ? "Powered by Google Gemini" : "Powered by Ollama (Local)");
                            return;
                        }
                    }
                }
            }
        }
    }
}
