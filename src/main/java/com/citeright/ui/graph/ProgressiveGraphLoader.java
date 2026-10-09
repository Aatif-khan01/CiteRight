package com.citeright.ui.graph;

import javafx.application.Platform;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A progressive loading pipeline for the Paper Graph.
 * Breaks the heavy blocking graph generation into stages, allowing the UI to
 * remain responsive and potentially draw partial intermediate states.
 */
public class ProgressiveGraphLoader {

    public interface ProgressCallback {
        void onProgress(String stageName, double progress, boolean canDrawPartial);
    }

    /**
     * Executes the heavy graph build pipeline asynchronously, reporting progress.
     */
    public static void buildGraphAsync(GraphModel model,
                                       HierarchicalClusterLayout layout,
                                       EdgeRouter router,
                                       GraphCamera camera,
                                       double canvasW, double canvasH,
                                       ProgressCallback onProgress,
                                       Runnable onComplete) {
        
        new Thread(() -> {
            try {
                // Stage 1: Load nodes & cluster
                Platform.runLater(() -> onProgress.onProgress("Reading library...", 0.1, false));
                model.rebuild();
                
                // Stage 2: Layout
                Map<Integer, GraphLayoutCache.CachedNodeData> cache = GraphLayoutCache.loadPositions();
                boolean needsLayout = false;

                for (GraphNode node : model.getNodes()) {
                    GraphLayoutCache.CachedNodeData cached = cache.get(node.entry.getId());
                    if (cached != null) {
                        node.x = cached.x;
                        node.y = cached.y;
                        if (cached.clusterLabel != null) node.clusterLabel = cached.clusterLabel;
                        if (cached.clusterColor != null) node.clusterColor = javafx.scene.paint.Color.valueOf(cached.clusterColor);
                    } else {
                        needsLayout = true;
                    }
                }

                if (needsLayout || model.getNodes().isEmpty()) {
                    Platform.runLater(() -> onProgress.onProgress("Calculating layouts...", 0.4, false));
                    layout.layout();
                    GraphLayoutCache.savePositions(model);
                } else {
                    Platform.runLater(() -> onProgress.onProgress("Layout loaded from cache", 0.4, false));
                }

                // Stage 3: Initial positioning (ready to draw nodes without edges)
                Platform.runLater(() -> {
                    // Update camera to fit the nodes now that they are laid out
                    autoFitCamera(camera, model, canvasW, canvasH);
                    onProgress.onProgress("Routing edges...", 0.6, true);
                });

                // Stage 4: Heavy edge routing
                // In the future, this could be broken down per-edge or batched for smoother loading.
                router.computeAll();

                // Stage 5: Finalizing
                Platform.runLater(() -> onProgress.onProgress("Finalizing graph...", 1.0, true));

            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                Platform.runLater(onComplete);
            }
        }, "progressive-graph-build").start();
    }

    private static void autoFitCamera(GraphCamera camera, GraphModel model, double viewW, double viewH) {
        java.util.List<GraphNode> nodes = model.getNodes();
        if (nodes.isEmpty()) {
            camera.centreOnWorld(0, 0);
            camera.setZoom(0.9);
            return;
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GraphNode n : nodes) {
            double r = NodeRenderer.radius(n) + 40; 
            if (n.x - r < minX) minX = n.x - r;
            if (n.x + r > maxX) maxX = n.x + r;
            if (n.y - r < minY) minY = n.y - r;
            if (n.y + r > maxY) maxY = n.y + r;
        }

        double worldW = maxX - minX;
        double worldH = maxY - minY;
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;

        if (viewW < 100 || viewH < 100) {
            camera.centreOnWorld(centerX, centerY);
            camera.setZoom(0.5);
            return;
        }

        double zoomX = (viewW * 0.70) / worldW;
        double zoomY = (viewH * 0.70) / worldH;
        double zoom = Math.max(GraphCamera.MIN_ZOOM, Math.min(1.2, Math.min(zoomX, zoomY)));

        camera.setZoom(zoom);
        camera.centreOnWorld(centerX, centerY);
    }
}
