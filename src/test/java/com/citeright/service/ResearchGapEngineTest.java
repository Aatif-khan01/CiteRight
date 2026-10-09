package com.citeright.service;

import com.citeright.model.*;
import com.citeright.nlp.EntityExtractor;
import com.citeright.nlp.EntityExtractor.EntityType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class ResearchGapEngineTest {

    @Test
    public void testEntityExtractor() {
        EntityExtractor extractor = new EntityExtractor();
        String title = "Deep Learning with Janus TMDC for Spintronics and Rashba Effect";
        String abs = "We apply Density Functional Theory (DFT) on Janus materials to measure spin-orbit coupling.";

        Map<EntityType, Set<String>> entities = extractor.extractFromPaper(title, abs);

        assertNotNull(entities);
        assertTrue(entities.containsKey(EntityType.METHOD));
        assertTrue(entities.get(EntityType.METHOD).contains("deep learning") || entities.get(EntityType.METHOD).contains("dft"));
    }

    @Test
    public void testHybridSimilarityCalculator() {
        HybridSimilarityCalculator calc = new HybridSimilarityCalculator();

        float[] v1 = new float[]{1.0f, 0.0f, 0.5f};
        float[] v2 = new float[]{1.0f, 0.0f, 0.5f};

        double sim = calc.computeSimilarity(
            v1, v2,
            Set.of(1, 2), Set.of(1, 2),
            Set.of("dft"), Set.of("dft"),
            Set.of("classification"), Set.of("classification"),
            Set.of("physics"), Set.of("physics")
        );

        assertTrue(sim > 0.9, "Identical inputs should yield similarity near 1.0");
    }

    @Test
    public void testOpportunityScoreCalculator() {
        OpportunityScoreCalculator calc = new OpportunityScoreCalculator();
        KnowledgeContext ctx = new KnowledgeContext();

        ResearchGap gap = new ResearchGap()
            .gapId("TEST_GAP_1")
            .type(GapType.INTERDISCIPLINARY)
            .title("Test Interdisciplinary Gap")
            .explanation("Testing score computation")
            .confidence(0.85);

        double score = calc.computeScore(gap, ctx);

        assertTrue(score >= 0.0 && score <= 100.0, "Score should be in 0-100 range");
    }
}
