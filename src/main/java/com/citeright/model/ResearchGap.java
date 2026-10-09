package com.citeright.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Core result object for the Research Gap Discovery Engine.
 *
 * A ResearchGap represents a detected gap in the user's research library.
 * It becomes a "Research Opportunity" only after passing through the
 * OpportunityEvaluator — the isOpportunity flag distinguishes confirmed
 * opportunities from unconfirmed gaps.
 *
 * Every gap answers 4 questions for explainability:
 *   1. WHY is this a gap? → explanation
 *   2. HOW was it detected? → detectionMethod
 *   3. EVIDENCE? → evidence (structured GapEvidence list)
 *   4. CONFIDENCE? → confidenceExplanation + uncertaintyFactors
 *
 * Every gap has full provenance traceability:
 *   Analyzer → Reasoning Steps → Evidence → Supporting Papers
 */
public class ResearchGap {

    // ─── Identity ───────────────────────────────────────────────
    private String gapId;
    private GapType type;

    // ─── Opportunity Evaluation ─────────────────────────────────
    /** True only after OpportunityEvaluator confirms this gap is worth pursuing */
    private boolean isOpportunity;
    private double opportunityScore;   // 0–100 composite ranking score
    private double confidence;         // 0–1.0 detection certainty
    private EvidenceStrength evidenceStrength;
    private GapSeverity severity;
    private GapStatus status;

    // ─── Readiness ──────────────────────────────────────────────
    /** What percentage of relevant papers the user already owns (0–1.0) */
    private double researchReadiness;
    /** Number of key papers the user doesn't own but should */
    private int missingPaperCount;

    // ─── Explainability (4 questions) ───────────────────────────
    private String title;
    private String explanation;            // WHY is this a gap?
    private String detectionMethod;        // HOW was it detected?
    private List<GapEvidence> evidence;    // EVIDENCE? (structured)
    private String confidenceExplanation;  // CONFIDENCE? why we are this certain

    // ─── Uncertainty Engine ─────────────────────────────────────
    private List<UncertaintyFactor> uncertaintyFactors;

    // ─── Provenance ─────────────────────────────────────────────
    private GapProvenance provenance;

    // ─── Affected Scope ─────────────────────────────────────────
    private List<Integer> affectedPaperIds;
    private List<String> affectedClusters;

    // ─── Recommendations ────────────────────────────────────────
    private List<Integer> suggestedReadings;    // Paper IDs
    private List<String> recommendedMethods;
    private List<String> recommendedDatasets;

    // ─── Temporal Context ───────────────────────────────────────
    private GapTimeline temporalContext;

    // ─── Metadata ───────────────────────────────────────────────
    private String analyzerVersion;
    private long detectedAt;

    // ─── User Notes ─────────────────────────────────────────────
    private String userNotes;

    public ResearchGap() {
        this.evidence = new ArrayList<>();
        this.uncertaintyFactors = new ArrayList<>();
        this.affectedPaperIds = new ArrayList<>();
        this.affectedClusters = new ArrayList<>();
        this.suggestedReadings = new ArrayList<>();
        this.recommendedMethods = new ArrayList<>();
        this.recommendedDatasets = new ArrayList<>();
        this.status = GapStatus.NEW;
        this.isOpportunity = false;
        this.detectedAt = System.currentTimeMillis();
    }

    // ─── Builder-style setters ──────────────────────────────────

    public ResearchGap gapId(String gapId) { this.gapId = gapId; return this; }
    public ResearchGap type(GapType type) { this.type = type; return this; }
    public ResearchGap isOpportunity(boolean isOpportunity) { this.isOpportunity = isOpportunity; return this; }
    public ResearchGap opportunityScore(double score) { this.opportunityScore = score; return this; }
    public ResearchGap confidence(double confidence) { this.confidence = confidence; return this; }
    public ResearchGap evidenceStrength(EvidenceStrength es) { this.evidenceStrength = es; return this; }
    public ResearchGap severity(GapSeverity severity) { this.severity = severity; return this; }
    public ResearchGap status(GapStatus status) { this.status = status; return this; }
    public ResearchGap researchReadiness(double readiness) { this.researchReadiness = readiness; return this; }
    public ResearchGap missingPaperCount(int count) { this.missingPaperCount = count; return this; }
    public ResearchGap title(String title) { this.title = title; return this; }
    public ResearchGap explanation(String explanation) { this.explanation = explanation; return this; }
    public ResearchGap detectionMethod(String method) { this.detectionMethod = method; return this; }
    public ResearchGap confidenceExplanation(String explanation) { this.confidenceExplanation = explanation; return this; }
    public ResearchGap provenance(GapProvenance provenance) { this.provenance = provenance; return this; }
    public ResearchGap temporalContext(GapTimeline timeline) { this.temporalContext = timeline; return this; }
    public ResearchGap analyzerVersion(String version) { this.analyzerVersion = version; return this; }
    public ResearchGap detectedAt(long timestamp) { this.detectedAt = timestamp; return this; }

    // ─── Standard getters and setters ───────────────────────────

    public String getGapId() { return gapId; }
    public void setGapId(String gapId) { this.gapId = gapId; }

    public GapType getType() { return type; }
    public void setType(GapType type) { this.type = type; }

    public boolean isOpportunity() { return isOpportunity; }
    public void setOpportunity(boolean opportunity) { isOpportunity = opportunity; }

    public double getOpportunityScore() { return opportunityScore; }
    public void setOpportunityScore(double opportunityScore) { this.opportunityScore = opportunityScore; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public EvidenceStrength getEvidenceStrength() { return evidenceStrength; }
    public void setEvidenceStrength(EvidenceStrength evidenceStrength) { this.evidenceStrength = evidenceStrength; }

    public GapSeverity getSeverity() { return severity; }
    public void setSeverity(GapSeverity severity) { this.severity = severity; }

    public GapStatus getStatus() { return status; }
    public void setStatus(GapStatus status) { this.status = status; }

    public double getResearchReadiness() { return researchReadiness; }
    public void setResearchReadiness(double researchReadiness) { this.researchReadiness = researchReadiness; }

    public int getMissingPaperCount() { return missingPaperCount; }
    public void setMissingPaperCount(int missingPaperCount) { this.missingPaperCount = missingPaperCount; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getDetectionMethod() { return detectionMethod; }
    public void setDetectionMethod(String detectionMethod) { this.detectionMethod = detectionMethod; }

    public List<GapEvidence> getEvidence() { return evidence; }
    public void setEvidence(List<GapEvidence> evidence) { this.evidence = evidence; }

    public String getConfidenceExplanation() { return confidenceExplanation; }
    public void setConfidenceExplanation(String confidenceExplanation) { this.confidenceExplanation = confidenceExplanation; }

    public List<UncertaintyFactor> getUncertaintyFactors() { return uncertaintyFactors; }
    public void setUncertaintyFactors(List<UncertaintyFactor> uncertaintyFactors) { this.uncertaintyFactors = uncertaintyFactors; }

    public GapProvenance getProvenance() { return provenance; }
    public void setProvenance(GapProvenance provenance) { this.provenance = provenance; }

    public List<Integer> getAffectedPaperIds() { return affectedPaperIds; }
    public void setAffectedPaperIds(List<Integer> affectedPaperIds) { this.affectedPaperIds = affectedPaperIds; }

    public List<String> getAffectedClusters() { return affectedClusters; }
    public void setAffectedClusters(List<String> affectedClusters) { this.affectedClusters = affectedClusters; }

    public List<Integer> getSuggestedReadings() { return suggestedReadings; }
    public void setSuggestedReadings(List<Integer> suggestedReadings) { this.suggestedReadings = suggestedReadings; }

    public List<String> getRecommendedMethods() { return recommendedMethods; }
    public void setRecommendedMethods(List<String> recommendedMethods) { this.recommendedMethods = recommendedMethods; }

    public List<String> getRecommendedDatasets() { return recommendedDatasets; }
    public void setRecommendedDatasets(List<String> recommendedDatasets) { this.recommendedDatasets = recommendedDatasets; }

    public GapTimeline getTemporalContext() { return temporalContext; }
    public void setTemporalContext(GapTimeline temporalContext) { this.temporalContext = temporalContext; }

    public String getAnalyzerVersion() { return analyzerVersion; }
    public void setAnalyzerVersion(String analyzerVersion) { this.analyzerVersion = analyzerVersion; }

    public long getDetectedAt() { return detectedAt; }
    public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }

    public String getUserNotes() { return userNotes; }
    public void setUserNotes(String userNotes) { this.userNotes = userNotes; }

    // ─── Utility ────────────────────────────────────────────────

    /** Add an uncertainty factor */
    public void addUncertaintyFactor(UncertaintyFactor factor) {
        this.uncertaintyFactors.add(factor);
    }

    /** Add evidence */
    public void addEvidence(GapEvidence ev) {
        this.evidence.add(ev);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResearchGap that = (ResearchGap) o;
        return Objects.equals(gapId, that.gapId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gapId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (score=%.1f, confidence=%.2f, evidence=%s, opportunity=%s)",
            type != null ? type.name() : "?",
            title != null ? title : "Untitled",
            opportunityScore,
            confidence,
            evidenceStrength != null ? evidenceStrength.name() : "?",
            isOpportunity);
    }
}
