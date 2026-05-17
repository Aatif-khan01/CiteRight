package com.citeright;

/**
 * A separate Launcher class is strictly required for JavaFX 11+ non-modular applications 
 * when building Fat JARs. If the main class extends Application, the Java launcher 
 * enforces module path checks and fails. Placing a separate launcher avoids this issue.
 */
public class Launcher {
    public static void main(String[] args) {
        CiteRightApp.main(args);
    }
}
