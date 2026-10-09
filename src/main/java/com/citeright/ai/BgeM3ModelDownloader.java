package com.citeright.ai;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles dynamic, pauseable, and resumeable HTTP downloading of BGE-M3 model files.
 * Custom-built to handle network drops and mobile hotspots with a tiny visual footprint.
 * Downloads to .tmp files first, then renames to finalize.
 */
public class BgeM3ModelDownloader {

    public enum State {
        IDLE,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        ERROR
    }

    public interface DownloadListener {
        void onProgress(double percent, double speedMBs, String activeFile, long downloadedBytes, long totalBytes);
        void onComplete();
        void onError(String errorMessage);
        void onStateChanged(State newState);
    }

    private static final String MODEL_URL = "https://huggingface.co/Xenova/bge-m3/resolve/main/onnx/model_quantized.onnx";
    private static final String TOKENIZER_URL = "https://huggingface.co/Xenova/bge-m3/resolve/main/tokenizer.json";

    private final Path targetDirectory;
    private final Path modelPath;
    private final Path modelTmpPath;
    private final Path tokenizerPath;
    private final Path tokenizerTmpPath;

    private State state = State.IDLE;
    private final java.util.List<DownloadListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Thread downloadThread;

    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    private final HttpClient httpClient;

    public BgeM3ModelDownloader() {
        String homeDir = System.getProperty("user.home");
        this.targetDirectory = Paths.get(homeDir, ".citeright", "models", "bge-m3");
        this.modelPath = targetDirectory.resolve("model.onnx");
        this.modelTmpPath = targetDirectory.resolve("model.onnx.tmp");
        this.tokenizerPath = targetDirectory.resolve("tokenizer.json");
        this.tokenizerTmpPath = targetDirectory.resolve("tokenizer.json.tmp");

        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public void setListener(DownloadListener listener) {
        if (listener == null) {
            listeners.clear();
        } else if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void addListener(DownloadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    public State getState() { return state; }
    public boolean isDownloaded() {
        return Files.exists(modelPath) && Files.exists(tokenizerPath);
    }

    private void changeState(State newState) {
        this.state = newState;
        for (DownloadListener l : listeners) {
            try {
                l.onStateChanged(newState);
            } catch (Exception e) {
                System.err.println("[Downloader] Error notifying listener: " + e.getMessage());
            }
        }
    }

    /**
     * Start the download process. Resumes automatically if partial files exist on disk.
     */
    public synchronized void start() {
        if (state == State.DOWNLOADING) return;
        if (isDownloaded()) {
            changeState(State.COMPLETED);
            for (DownloadListener l : listeners) {
                try { l.onComplete(); } catch (Exception e) { System.err.println("[Downloader] Error: " + e.getMessage()); }
            }
            return;
        }

        isPaused.set(false);
        isCancelled.set(false);
        changeState(State.DOWNLOADING);

        downloadThread = new Thread(this::runDownload, "SemanticAI-Downloader");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    public synchronized void pause() {
        if (state != State.DOWNLOADING) return;
        isPaused.set(true);
        changeState(State.PAUSED);
        System.out.println("[Downloader] Paused by user request.");
    }

    public synchronized void resume() {
        if (state != State.PAUSED) return;
        start();
    }

    public synchronized void cancel() {
        isCancelled.set(true);
        if (downloadThread != null) {
            downloadThread.interrupt();
        }
        cleanupTempFiles();
        changeState(State.IDLE);
        System.out.println("[Downloader] Cancelled and temporary files cleaned up.");
    }

    private void cleanupTempFiles() {
        try {
            Files.deleteIfExists(modelTmpPath);
            Files.deleteIfExists(tokenizerTmpPath);
        } catch (IOException e) {
            System.err.println("[Downloader] Error cleaning up temporary files: " + e.getMessage());
        }
    }

    private void runDownload() {
        try {
            Files.createDirectories(targetDirectory);

            // Step 1: Download Tokenizer first if missing
            if (!Files.exists(tokenizerPath)) {
                downloadFile(TOKENIZER_URL, tokenizerPath, tokenizerTmpPath, "tokenizer.json");
            }

            if (isPaused.get() || isCancelled.get()) return;

            // Step 2: Download ONNX Model if missing
            if (!Files.exists(modelPath)) {
                downloadFile(MODEL_URL, modelPath, modelTmpPath, "model.onnx");
            }

            if (!isPaused.get() && !isCancelled.get()) {
                changeState(State.COMPLETED);
                for (DownloadListener l : listeners) {
                    try { l.onComplete(); } catch (Exception e) { System.err.println("[Downloader] Error: " + e.getMessage()); }
                }
            }

        } catch (Exception e) {
            if (isCancelled.get()) {
                System.out.println("[Downloader] Thread interrupted due to cancellation.");
            } else if (isPaused.get()) {
                System.out.println("[Downloader] Thread exited gracefully due to pause.");
            } else {
                System.err.println("[Downloader] Download failed: " + e.getMessage());
                e.printStackTrace();
                changeState(State.ERROR);
                for (DownloadListener l : listeners) {
                    try { l.onError(e.getMessage()); } catch (Exception ex) { System.err.println("[Downloader] Error: " + ex.getMessage()); }
                }
            }
        }
    }

    private void downloadFile(String url, Path finalPath, Path tmpPath, String label) throws Exception {
        long existingSize = 0;
        if (Files.exists(tmpPath)) {
            existingSize = Files.size(tmpPath);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

        // Support resuming if a partial file exists on disk
        if (existingSize > 0) {
            System.out.println("[Downloader] Resuming " + label + " from byte index " + existingSize);
            reqBuilder.header("Range", "bytes=" + existingSize + "-");
        }

        HttpRequest request = reqBuilder.build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int statusCode = response.statusCode();
        // 200 means OK (fresh download), 206 means Partial Content (successful range request)
        if (statusCode != 200 && statusCode != 206) {
            throw new IOException("HTTP " + statusCode + " returned from Hugging Face server");
        }

        // Get total size of download
        long remoteSize = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        long totalSize = existingSize + remoteSize;

        // If resuming, Content-Range gives actual total file size
        String contentRange = response.headers().firstValue("Content-Range").orElse("");
        if (!contentRange.isEmpty()) {
            try {
                String[] parts = contentRange.split("/");
                if (parts.length > 1) {
                    totalSize = Long.parseLong(parts[1].trim());
                }
            } catch (Exception ignored) {}
        }

        System.out.println("[Downloader] Staring download for " + label + ". Total expected size: " + (totalSize / (1024 * 1024)) + " MB");

        // Write stream to temp file
        try (InputStream in = response.body();
             RandomAccessFile out = new RandomAccessFile(tmpPath.toFile(), "rw")) {
            
            if (existingSize > 0) {
                out.seek(existingSize);
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            long downloadedBytes = existingSize;

            Instant startInstant = Instant.now();
            long bytesSinceSpeedSample = 0;
            double currentSpeedMBs = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (isCancelled.get()) {
                    throw new InterruptedException("Download cancelled");
                }
                if (isPaused.get()) {
                    return; // exit thread
                }

                out.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;
                bytesSinceSpeedSample += bytesRead;

                // Speed calculation every 500ms
                Duration elapsed = Duration.between(startInstant, Instant.now());
                if (elapsed.toMillis() >= 500) {
                    double seconds = elapsed.toMillis() / 1000.0;
                    currentSpeedMBs = (bytesSinceSpeedSample / (1024.0 * 1024.0)) / seconds;
                    
                    // Reset samples
                    startInstant = Instant.now();
                    bytesSinceSpeedSample = 0;
                }

                if (totalSize > 0) {
                    double percent = (double) downloadedBytes / totalSize;
                    for (DownloadListener l : listeners) {
                        try { l.onProgress(percent, currentSpeedMBs, label, downloadedBytes, totalSize); } catch (Exception e) { System.err.println("[Downloader] Error: " + e.getMessage()); }
                    }
                }
            }
        }

        // Finalize by renaming temporary file to the final path
        if (!isPaused.get() && !isCancelled.get()) {
            Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Downloader] Download completed and finalized: " + finalPath.getFileName());
        }
    }
}
