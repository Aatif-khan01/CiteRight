package com.citeright.ui;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

/**
 * Lightweight non-blocking toast notification system.
 * Shows a small pill at the bottom of the screen that auto-dismisses.
 *
 * Usage: ToastNotification.show(stackRoot, "Citation copied!", ToastNotification.Type.SUCCESS);
 */
public class ToastNotification extends HBox {

    public enum Type { SUCCESS, INFO, WARNING, ERROR }

    private ToastNotification(String message, Type type) {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(10, 24, 10, 24));
        setMaxWidth(420);
        setPickOnBounds(false);
        setMouseTransparent(true);

        String icon = switch (type) {
            case SUCCESS -> "✓";
            case WARNING -> "⚠";
            case ERROR   -> "✕";
            default      -> "ℹ";
        };

        String bgColor = switch (type) {
            case SUCCESS -> "#1a7a3a";
            case WARNING -> "#b8860b";
            case ERROR   -> "#cc3333";
            default      -> "#1a1a2e";
        };

        setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 24; " +
                 "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0, 0, 4);");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 0 8 0 0;");

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12px; -fx-font-weight: bold;");
        msgLabel.setWrapText(true);

        getChildren().addAll(iconLabel, msgLabel);
    }

    /**
     * Shows a toast at the bottom of the given StackPane.
     * Auto-dismisses after 2.5 seconds with fade animation.
     */
    public static void show(StackPane root, String message, Type type) {
        if (root == null) return;

        ToastNotification toast = new ToastNotification(message, type);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 32, 0));

        toast.setOpacity(0);
        toast.setTranslateY(20);
        root.getChildren().add(toast);

        // Slide in + fade in
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), toast);
        slideIn.setFromY(20);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition enter = new ParallelTransition(slideIn, fadeIn);

        // After 2.5 seconds, fade out + slide down
        PauseTransition wait = new PauseTransition(Duration.millis(2500));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(400), toast);
        slideOut.setFromY(0);
        slideOut.setToY(20);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
        exit.setOnFinished(e -> root.getChildren().remove(toast));

        SequentialTransition seq = new SequentialTransition(enter, wait, exit);
        seq.play();
    }

    /** Convenience: show a success toast. */
    public static void success(StackPane root, String message) { show(root, message, Type.SUCCESS); }

    /** Convenience: show an info toast. */
    public static void info(StackPane root, String message) { show(root, message, Type.INFO); }

    /** Convenience: show an error toast. */
    public static void error(StackPane root, String message) { show(root, message, Type.ERROR); }
}
