package com.citeright.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Full provenance chain for a research gap — ensures every recommendation is traceable.
 *
 * Tracks: which analyzer → what data it processed → reasoning steps → evidence papers.
 * This makes the platform academically defensible and builds researcher trust.
 */
public class GapProvenance {

    private String analyzerName;
    private String analyzerVersion;
    private String inputDataSummary;       // e.g., "481 papers across 7 clusters"
    private List<String> reasoningSteps;   // Ordered list of how the conclusion was reached
    private List<Integer> evidenceChain;   // Paper IDs forming the evidence chain
    private long timestamp;

    public GapProvenance() {
        this.reasoningSteps = new ArrayList<>();
        this.evidenceChain = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public GapProvenance(String analyzerName, String analyzerVersion, String inputDataSummary) {
        this();
        this.analyzerName = analyzerName;
        this.analyzerVersion = analyzerVersion;
        this.inputDataSummary = inputDataSummary;
    }

    /** Add a reasoning step to the provenance chain */
    public GapProvenance addStep(String step) {
        this.reasoningSteps.add(step);
        return this;
    }

    /** Add a paper to the evidence chain */
    public GapProvenance addEvidence(int paperId) {
        this.evidenceChain.add(paperId);
        return this;
    }

    /** Add multiple papers to the evidence chain */
    public GapProvenance addEvidence(List<Integer> paperIds) {
        this.evidenceChain.addAll(paperIds);
        return this;
    }

    // Getters and setters
    public String getAnalyzerName() { return analyzerName; }
    public void setAnalyzerName(String analyzerName) { this.analyzerName = analyzerName; }

    public String getAnalyzerVersion() { return analyzerVersion; }
    public void setAnalyzerVersion(String analyzerVersion) { this.analyzerVersion = analyzerVersion; }

    public String getInputDataSummary() { return inputDataSummary; }
    public void setInputDataSummary(String inputDataSummary) { this.inputDataSummary = inputDataSummary; }

    public List<String> getReasoningSteps() { return reasoningSteps; }
    public void setReasoningSteps(List<String> reasoningSteps) { this.reasoningSteps = reasoningSteps; }

    public List<Integer> getEvidenceChain() { return evidenceChain; }
    public void setEvidenceChain(List<Integer> evidenceChain) { this.evidenceChain = evidenceChain; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /** Returns a human-readable summary of the provenance chain */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Detected by ").append(analyzerName).append(" v").append(analyzerVersion);
        sb.append(" | ").append(inputDataSummary);
        if (!reasoningSteps.isEmpty()) {
            sb.append("\nReasoning: ");
            for (int i = 0; i < reasoningSteps.size(); i++) {
                sb.append("\n  ").append(i + 1).append(". ").append(reasoningSteps.get(i));
            }
        }
        if (!evidenceChain.isEmpty()) {
            sb.append("\nEvidence papers: ").append(evidenceChain.size()).append(" papers");
        }
        return sb.toString();
    }
}
