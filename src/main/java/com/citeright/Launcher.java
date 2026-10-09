package com.citeright;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A separate Launcher class is strictly required for JavaFX 11+ non-modular applications 
 * when building Fat JARs. If the main class extends Application, the Java launcher 
 * enforces module path checks and fails. Placing a separate launcher avoids this issue.
 *
 * Also installs a global uncaught exception handler to:
 * 1. Prevent background thread crashes from killing the entire app
 * 2. Log crashes to ~/.citeright/crash.log for remote diagnostics
 */
public class Launcher {
    public static void main(String[] args) {
        // Install global crash handler BEFORE anything else
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[CRASH] Uncaught exception in thread: " + thread.getName());
            throwable.printStackTrace();
            logCrash(thread, throwable);
        });

        CiteRightApp.main(args);
    }

    /**
     * Writes crash details to ~/.citeright/crash.log so users can share
     * the log for remote diagnosis without needing a terminal.
     */
    private static void logCrash(Thread thread, Throwable throwable) {
        try {
            String home = System.getProperty("user.home");
            File logDir = new File(home, ".citeright");
            if (!logDir.exists()) logDir.mkdirs();

            File crashLog = new File(logDir, "crash.log");
            try (PrintWriter pw = new PrintWriter(new FileWriter(crashLog, true))) {
                pw.println("=== CRASH REPORT ===");
                pw.println("Time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                pw.println("Thread: " + thread.getName());
                pw.println("Exception: " + throwable.getClass().getName() + ": " + throwable.getMessage());
                throwable.printStackTrace(pw);
                pw.println();
            }
        } catch (Exception ignored) {
            // If we can't even write the crash log, nothing more we can do
        }
    }
}
