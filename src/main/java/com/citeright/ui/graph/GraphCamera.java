package com.citeright.ui.graph;

import javafx.animation.AnimationTimer;

/**
 * World↔screen transform for the graph canvas.
 *
 * <p>Convention: the world origin sits at the canvas centre plus the pan
 * offset. World units are the layout's own units (roughly pixels at zoom 1).
 *
 * <p>{@code screen = canvasCenter + panOffset + world * zoom}
 *
 * <p>Zoom is cursor-anchored: the world point under the pointer stays fixed
 * as zoom changes, which is the intuitive "zoom to mouse" behaviour.
 *
 * <p>{@link #animateTo} smoothly lerps offset/zoom to a target — used by the
 * "centre on node" double-click and (in a later phase) search navigation.
 */
public final class GraphCamera {

    public static final double MIN_ZOOM = 0.2;
    public static final double MAX_ZOOM = 4.0;

    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double zoom = 1.0;

    private double viewWidth = 0.0;
    private double viewHeight = 0.0;

    // Animation state
    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) { tick(now); }
    };
    private boolean animating = false;
    private long animStartNanos = 0;
    private double animDurationMs;
    private double fromX, fromY, fromZoom;
    private double toX, toY, toZoom;
    private Runnable onDone;

    public void setViewport(double w, double h) {
        this.viewWidth = w;
        this.viewHeight = h;
    }

    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getZoom()     { return zoom; }

    public void setOffset(double x, double y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public void setZoom(double z) {
        this.zoom = clampZoom(z);
    }

    public double centerX() { return viewWidth / 2.0 + offsetX; }
    public double centerY() { return viewHeight / 2.0 + offsetY; }

    // ── Transforms ─────────────────────────────────────────────────────────

    public double worldToScreenX(double wx) { return centerX() + wx * zoom; }
    public double worldToScreenY(double wy) { return centerY() + wy * zoom; }

    public double screenToWorldX(double sx) { return (sx - centerX()) / zoom; }
    public double screenToWorldY(double sy) { return (sy - centerY()) / zoom; }

    // ── Pan ────────────────────────────────────────────────────────────────

    /** Pan by the given screen-space delta. */
    public void pan(double dx, double dy) {
        stopAnimation();
        offsetX += dx;
        offsetY += dy;
    }

    // ── Zoom (cursor-anchored) ─────────────────────────────────────────────

    /**
     * Zoom so that the world point under {@code (screenX, screenY)} stays put.
     */
    public void zoomAt(double screenX, double screenY, double factor) {
        stopAnimation();
        double newZoom = clampZoom(zoom * factor);
        if (newZoom == zoom) return;

        double worldX = screenToWorldX(screenX);
        double worldY = screenToWorldY(screenY);

        zoom = newZoom;

        // Recompute offset so the same world point maps to the same screen point.
        offsetX = screenX - viewWidth / 2.0 - worldX * zoom;
        offsetY = screenY - viewHeight / 2.0 - worldY * zoom;
    }

    /** Zoom around the viewport centre. */
    public void zoomCentered(double factor) {
        zoomAt(viewWidth / 2.0, viewHeight / 2.0, factor);
    }

    // ── Centre the camera on a world point ─────────────────────────────────

    public void centreOnWorld(double wx, double wy) {
        offsetX = -wx * zoom;
        offsetY = -wy * zoom;
    }

    // ── Smooth animation ───────────────────────────────────────────────────

    /**
     * Animate the camera to the given world point and zoom over
     * {@code durationMs}. Optional {@code onDone} fires on the FX thread.
     */
    public void animateTo(double targetWorldX, double targetWorldY,
                          double targetZoom, double durationMs, Runnable onDone) {
        this.fromX = offsetX; this.fromY = offsetY; this.fromZoom = zoom;
        this.toZoom = clampZoom(targetZoom);
        this.toX = -targetWorldX * toZoom;
        this.toY = -targetWorldY * toZoom;
        this.animDurationMs = durationMs;
        this.onDone = onDone;
        this.animStartNanos = System.nanoTime();
        this.animating = true;
        timer.start();
    }

    /** Cancel any in-flight animation immediately. */
    public void stopAnimation() {
        if (animating) {
            animating = false;
            timer.stop();
        }
    }

    public boolean isAnimating() { return animating; }

    private void tick(long now) {
        if (!animating) return;
        double elapsedMs = (now - animStartNanos) / 1_000_000.0;
        double t = Math.min(1.0, elapsedMs / animDurationMs);
        double e = easeInOutCubic(t);

        offsetX = fromX + (toX - fromX) * e;
        offsetY = fromY + (toY - fromY) * e;
        zoom    = fromZoom + (toZoom - fromZoom) * e;

        if (t >= 1.0) {
            animating = false;
            timer.stop();
            if (onDone != null) {
                Runnable r = onDone;
                onDone = null;
                r.run();
            }
        }
    }

    private static double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private static double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }
}
