package com.citeright.model;

/**
 * Types of research gaps detected by the Research Gap Discovery Engine.
 *
 * Each type corresponds to a specific GapAnalyzerModule implementation.
 */
public enum GapType {

    /** Two topics exist independently but rarely appear together */
    TOPIC("Topic Gap", "Underexplored topic combination"),

    /** A method proven in one domain has never been applied to another */
    METHODOLOGY_TRANSFER("Methodology Transfer", "Method not yet applied to a related domain"),

    /** A research area where publication activity has stopped entirely */
    TEMPORAL_DORMANT("Temporal Gap (Dormant)", "Research topic with no recent publications"),

    /** A research area where publication rate is dropping significantly */
    TEMPORAL_DECLINING("Temporal Gap (Declining)", "Research topic with declining publication rate"),

    /** Two mature clusters with weak connections despite shared concepts */
    INTERDISCIPLINARY("Interdisciplinary Gap", "Mature fields with little cross-pollination");

    private final String displayName;
    private final String description;

    GapType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
