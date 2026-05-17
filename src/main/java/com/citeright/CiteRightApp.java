package com.citeright;

import com.citeright.database.SQLiteDatabaseManager;
import com.citeright.service.SearchService;
import com.citeright.service.LibraryService;
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

        primaryStage.setTitle("CiteRight — Reference Manager");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(550);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("[App] Shutting down...");
        if (dbManager != null) dbManager.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
