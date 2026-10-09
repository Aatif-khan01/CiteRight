package com.citeright.service;

import com.citeright.model.ResearchGap;

import java.util.List;

/**
 * Interface for pluggable gap detection modules.
 *
 * Each implementation detects one specific type of gap in the research
 * knowledge graph. All modules receive the same pre-computed KnowledgeContext
 * and return a list of ResearchGap objects (not yet evaluated as opportunities).
 *
 * V1 implementations:
 *   - TopicGapAnalyzer: underexplored topic combinations
 *   - MethodologyTransferAnalyzer: methods proven in one domain, untried in another
 *   - TemporalGapAnalyzer: dormant/declining research areas
 *   - InterdisciplinaryGapAnalyzer: mature clusters with weak cross-connections
 *
 * Adding a new analyzer is a single step:
 *   1. Implement this interface
 *   2. Register in ResearchGapEngine.analyzerModules
 */
public interface GapAnalyzerModule {

    /**
     * Run gap analysis on the provided knowledge context.
     *
     * @param context pre-computed knowledge representation of the user's library
     * @return list of detected gaps (not yet evaluated as opportunities)
     */
    List<ResearchGap> analyze(KnowledgeContext context);

    /** Module display name for provenance tracking */
    String getName();

    /** Module version for provenance tracking */
    String getVersion();
}
