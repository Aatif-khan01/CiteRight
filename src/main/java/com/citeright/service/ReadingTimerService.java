package com.citeright.service;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.util.Duration;

/**
 * Reading timer that tracks time spent reading.
 * Supports start/pause/reset and exposes elapsed time as observable properties.
 */
public class ReadingTimerService {

    private final Timeline timer;
    private long elapsedSeconds = 0;
    private final LongProperty elapsedProperty = new SimpleLongProperty(0);
    private final BooleanProperty runningProperty = new SimpleBooleanProperty(false);
    private final StringProperty formattedTime = new SimpleStringProperty("00:00");

    public ReadingTimerService() {
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            elapsedProperty.set(elapsedSeconds);
            formattedTime.set(formatTime(elapsedSeconds));
        }));
        timer.setCycleCount(Animation.INDEFINITE);
    }

    public void start() {
        timer.play();
        runningProperty.set(true);
    }

    public void pause() {
        timer.pause();
        runningProperty.set(false);
    }

    public void toggle() {
        if (runningProperty.get()) pause(); else start();
    }

    public void reset() {
        timer.stop();
        elapsedSeconds = 0;
        elapsedProperty.set(0);
        formattedTime.set("00:00");
        runningProperty.set(false);
    }

    public long getElapsedSeconds() { return elapsedSeconds; }
    public LongProperty elapsedProperty() { return elapsedProperty; }
    public BooleanProperty runningProperty() { return runningProperty; }
    public StringProperty formattedTimeProperty() { return formattedTime; }

    public static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
