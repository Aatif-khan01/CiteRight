package com.citeright.model;

/**
 * User-managed status of a detected research gap/opportunity.
 */
public enum GapStatus {

    /** Newly detected, user hasn't seen it yet */
    NEW("New"),

    /** User has viewed the gap detail */
    VIEWED("Viewed"),

    /** User explicitly dismissed this gap (not interested) */
    DISMISSED("Dismissed"),

    /** User saved this gap for later investigation */
    SAVED("Saved"),

    /** User has investigated this gap */
    INVESTIGATED("Investigated");

    private final String displayName;

    GapStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
