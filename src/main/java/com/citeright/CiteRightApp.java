package com.citeright;

import com.citeright.database.SQLiteDatabaseManager;
import com.citeright.service.SearchService;
import com.citeright.service.LibraryService;
import com.citeright.service.BibSyncService;
import com.citeright.ui.MainLayout;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class CiteRightApp extends Application {

    private SQLiteDatabaseManager dbManager;
    private LibraryService libraryService;
    private SearchService searchService;

    @Override
    public void init() throws Exception {
        System.out.println("[App] Initializing services...");
        dbManager = SQLiteDatabaseManager.getInstance();
        libraryService = new LibraryService();
        searchService = new SearchService();

        // Start background BibTeX sync service
        BibSyncService.getInstance().syncNow();
        System.out.println("[App] BibSync service started.");

        // Initialize neural semantic engine in the background
        try {
            com.citeright.ai.EmbeddingService.getInstance().initializeEngine();
            System.out.println("[App] Semantic AI engine initialized.");
        } catch (Exception e) {
            System.err.println("[App] Failed to start Semantic AI engine: " + e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) {
        MainLayout root = new MainLayout(libraryService, searchService);

        // Wrap in StackPane so the Command Palette can overlay on top
        javafx.scene.layout.StackPane stackRoot = new javafx.scene.layout.StackPane();
        stackRoot.getChildren().addAll(root, root.getCommandPalette());

        // Dynamic sizing: use 80% of screen, capped at reasonable max
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = Math.min(screenBounds.getWidth() * 0.80, 1400);
        double height = Math.min(screenBounds.getHeight() * 0.80, 900);

        Scene scene = new Scene(stackRoot, width, height);

        // Install Ctrl+K shortcut
        root.installKeyboardShortcuts();

        primaryStage.setTitle("CiteRight");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(550);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("[App] Shutting down...");

        // 1. Stop background services
        BibSyncService.getInstance().shutdown();

        // 2. Stop AI services (background indexer + neural engine)
        try {
            com.citeright.ai.EmbeddingQueueManager.getInstance().shutdown();
        } catch (Exception e) {
            System.err.println("[App] EmbeddingQueueManager shutdown error: " + e.getMessage());
        }
        try {
            com.citeright.ai.BgeM3EmbeddingEngine.getInstance().close();
        } catch (Exception e) {
            System.err.println("[App] BgeM3EmbeddingEngine close error: " + e.getMessage());
        }

        // 3. Close database (last — other services may still flush data)
        if (dbManager != null) dbManager.close();

        System.out.println("[App] Shutdown complete.");
        // No System.exit(0) — let JavaFX runtime handle clean exit
    }

    public static void main(String[] args) {
        launch(args);
    }
}
