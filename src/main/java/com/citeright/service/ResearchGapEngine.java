package com.citeright.service;

import com.citeright.model.*;

import java.util.*;

/**
 * Top-level orchestrator for the Research Gap Discovery Engine.
 *
 * Pipeline:
 *   1. Build KnowledgeContext from the user's library
 *   2. Run all registered GapAnalyzerModules in parallel
 *   3. Merge and deduplicate detected gaps
 *   4. Evaluate gaps through OpportunityEvaluator
 *   5. Generate explanations via ResearchGapExplainer
 *   6. Return ranked list of Research Opportunities
 *
 * Thread-safe: all analyzers receive a read-only KnowledgeContext
 * and produce independent results that are merged afterward.
 */
public class ResearchGapEngine {

    private final KnowledgeBuilder knowledgeBuilder;
    private final List<GapAnalyzerModule> analyzerModules;
    private final OpportunityEvaluator opportunityEvaluator;
    private final ResearchGapExplainer explainer;

    /** Cached results from the last analysis run */
    private volatile List<ResearchGap> lastResults;
    private volatile KnowledgeContext lastContext;
    private volatile long lastAnalysisTimestamp;

    public ResearchGapEngine() {
        this.knowledgeBuilder = new KnowledgeBuilder();
        this.opportunityEvaluator = new OpportunityEvaluator();
        this.explainer = new ResearchGapExplainer();

        // Register all V1 analyzers
        this.analyzerModules = List.of(
            new TopicGapAnalyzer(),
            new MethodologyTransferAnalyzer(),
            new TemporalGapAnalyzer(),
            new InterdisciplinaryGapAnalyzer()
        );
    }

    /**
     * Run full gap analysis pipeline.
     *
     * @return ranked list of research gaps/opportunities
     */
    public List<ResearchGap> analyze() {
        long startTime = System.currentTimeMillis();
        System.out.println("[ResearchGapEngine] Starting full gap analysis...");

        // ── Step 1: Build knowledge context ─────────────────────────────
        KnowledgeContext context = knowledgeBuilder.build();
        this.lastContext = context;

        if (context.getPaperCount() < 5) {
            System.out.println("[ResearchGapEngine] Library too small for gap analysis (" +
                context.getPaperCount() + " papers, need ≥ 5)");
            this.lastResults = Collections.emptyList();
            return lastResults;
        }

        // ── Step 2: Run all analyzers ───────────────────────────────────
        List<ResearchGap> allGaps = new ArrayList<>();

        for (GapAnalyzerModule module : analyzerModules) {
            try {
                long moduleStart = System.currentTimeMillis();
                List<ResearchGap> moduleGaps = module.analyze(context);
                long moduleElapsed = System.currentTimeMillis() - moduleStart;

                System.out.printf("[ResearchGapEngine] %s: %d gaps in %dms%n",
                    module.getName(), moduleGaps.size(), moduleElapsed);

                allGaps.addAll(moduleGaps);
            } catch (Exception e) {
                System.err.println("[ResearchGapEngine] " + module.getName() + " failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ── Step 3: Deduplicate ─────────────────────────────────────────
        List<ResearchGap> deduplicated = deduplicateGaps(allGaps);

        // ── Step 4: Evaluate and rank ───────────────────────────────────
        List<ResearchGap> evaluated = opportunityEvaluator.evaluate(deduplicated, context);

        // ── Step 5: Cache results ───────────────────────────────────────
        this.lastResults = evaluated;
        this.lastAnalysisTimestamp = System.currentTimeMillis();

        long totalElapsed = System.currentTimeMillis() - startTime;
        long opportunities = evaluated.stream().filter(ResearchGap::isOpportunity).count();

        System.out.printf("[ResearchGapEngine] Analysis complete in %dms: %d gaps → %d opportunities%n",
            totalElapsed, allGaps.size(), opportunities);

        return evaluated;
    }

    /**
     * Get the last analysis results without re-running.
     * Returns empty list if no analysis has been run.
     */
    public List<ResearchGap> getLastResults() {
        return lastResults != null ? lastResults : Collections.emptyList();
    }

    /**
     * Get the last knowledge context.
     */
    public KnowledgeContext getLastContext() {
        return lastContext;
    }

    /**
     * Get the timestamp of the last analysis.
     */
    public long getLastAnalysisTimestamp() {
        return lastAnalysisTimestamp;
    }

    /**
     * Get only the confirmed opportunities (not all gaps).
     */
    public List<ResearchGap> getOpportunities() {
        if (lastResults == null) return Collections.emptyList();
        List<ResearchGap> opportunities = new ArrayList<>();
        for (ResearchGap gap : lastResults) {
            if (gap.isOpportunity()) {
                opportunities.add(gap);
            }
        }
        return opportunities;
    }

    /**
     * Get gaps grouped by type.
     */
    public Map<GapType, List<ResearchGap>> getGapsByType() {
        Map<GapType, List<ResearchGap>> grouped = new LinkedHashMap<>();
        for (GapType type : GapType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        if (lastResults != null) {
            for (ResearchGap gap : lastResults) {
                if (gap.getType() != null) {
                    grouped.get(gap.getType()).add(gap);
                }
            }
        }
        return grouped;
    }

    /**
     * Get gap type distribution counts.
     */
    public Map<GapType, Integer> getGapTypeCounts() {
        Map<GapType, Integer> counts = new LinkedHashMap<>();
        for (GapType type : GapType.values()) {
            counts.put(type, 0);
        }
        if (lastResults != null) {
            for (ResearchGap gap : lastResults) {
                if (gap.getType() != null) {
                    counts.merge(gap.getType(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Get the explainer instance for generating gap descriptions.
     */
    public ResearchGapExplainer getExplainer() {
        return explainer;
    }

    /**
     * Check if analysis has been run at least once.
     */
    public boolean hasResults() {
        return lastResults != null && !lastResults.isEmpty();
    }

    // ─── Deduplication ──────────────────────────────────────────────────

    /**
     * Remove duplicate gaps (same gapId from different modules).
     * Keeps the gap with the higher confidence.
     */
    private List<ResearchGap> deduplicateGaps(List<ResearchGap> gaps) {
        Map<String, ResearchGap> seen = new LinkedHashMap<>();
        for (ResearchGap gap : gaps) {
            String id = gap.getGapId();
            if (id == null) {
                seen.put("unknown_" + gaps.indexOf(gap), gap);
            } else if (!seen.containsKey(id) || gap.getConfidence() > seen.get(id).getConfidence()) {
                seen.put(id, gap);
            }
        }
        return new ArrayList<>(seen.values());
    }
}
