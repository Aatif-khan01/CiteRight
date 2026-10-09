package com.citeright.model;

/**
 * Structured uncertainty explanation for a research gap.
 *
 * Each factor describes something that either strengthens or weakens
 * the confidence in the gap detection, with a quantified impact.
 *
 * Displayed in UI as:
 *   ✔ Large evidence base (52 papers)          → strengthens
 *   ✔ Strong semantic similarity (0.87)        → strengthens
 *   ⚠ Few bridge papers (only 2)              → weakens
 *   ⚠ Sparse cross-cluster citations          → weakens
 */
public class UncertaintyFactor {

    public enum Direction {
        STRENGTHENS,
        WEAKENS
    }

    private final String factor;
    private final Direction direction;
    private final double impact; // 0.0–1.0

    public UncertaintyFactor(String factor, Direction direction, double impact) {
        this.factor = factor;
        this.direction = direction;
        this.impact = Math.max(0.0, Math.min(1.0, impact));
    }

    public String getFactor() { return factor; }
    public Direction getDirection() { return direction; }
    public double getImpact() { return impact; }

    /** Returns a UI-friendly display string, e.g. "✔ Large evidence base" or "⚠ Sparse citations" */
    public String toDisplayString() {
        String icon = direction == Direction.STRENGTHENS ? "✔" : "⚠";
        return icon + " " + factor;
    }

    @Override
    public String toString() {
        return toDisplayString() + " (impact: " + String.format("%.2f", impact) + ")";
    }
}
