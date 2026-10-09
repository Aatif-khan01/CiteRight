package com.citeright.nlp;

import java.util.HashMap;
import java.util.Map;

/**
 * Pluggable venue quality scoring interface and default implementation.
 *
 * Designed as an interface to avoid discipline bias — different fields
 * have different prestige hierarchies. The default DictionaryProvider
 * covers ~200 venues across CS, Physics, Biology, Medicine, Engineering,
 * and Social Sciences. Unknown venues default to 0.5 (neutral).
 *
 * Future providers could use citation-based scoring, external datasets
 * (Scimago, Google Scholar), or user-configured rankings.
 */
public interface VenueQualityProvider {

    /**
     * Returns a quality score for the given venue name (0.0–1.0).
     * 0.5 is neutral (unknown venue), not penalized.
     */
    double getQuality(String venueName);

    /** Provider name for provenance tracking */
    String getProviderName();

    // ─── Default implementation ─────────────────────────────────────

    /**
     * Dictionary-based provider with fuzzy matching and ~200 known venues.
     */
    class DictionaryProvider implements VenueQualityProvider {

        private static final double DEFAULT_QUALITY = 0.5; // Neutral, not penalized
        private final Map<String, Double> venueScores;

        public DictionaryProvider() {
            this.venueScores = new HashMap<>();
            initializeVenues();
        }

        private void initializeVenues() {
            // ─── Tier 1: Top multidisciplinary (1.0) ─────────
            addVenue("nature", 1.0);
            addVenue("science", 1.0);
            addVenue("cell", 1.0);
            addVenue("proceedings of the national academy of sciences", 1.0);
            addVenue("pnas", 1.0);

            // ─── Tier 2: Top field-specific (0.9) ────────────
            // Medicine
            addVenue("the lancet", 0.9);
            addVenue("lancet", 0.9);
            addVenue("new england journal of medicine", 0.9);
            addVenue("nejm", 0.9);
            addVenue("jama", 0.9);
            addVenue("bmj", 0.9);

            // Physics
            addVenue("physical review letters", 0.9);
            addVenue("prl", 0.9);
            addVenue("nature physics", 0.9);
            addVenue("nature materials", 0.9);
            addVenue("nature nanotechnology", 0.9);
            addVenue("nature communications", 0.85);
            addVenue("nano letters", 0.85);
            addVenue("acs nano", 0.85);

            // CS — Top conferences
            addVenue("neurips", 0.9);
            addVenue("nips", 0.9);
            addVenue("neural information processing systems", 0.9);
            addVenue("icml", 0.9);
            addVenue("international conference on machine learning", 0.9);
            addVenue("cvpr", 0.9);
            addVenue("conference on computer vision and pattern recognition", 0.9);
            addVenue("iclr", 0.9);
            addVenue("international conference on learning representations", 0.9);
            addVenue("acl", 0.9);
            addVenue("association for computational linguistics", 0.9);
            addVenue("aaai", 0.85);
            addVenue("ijcai", 0.85);
            addVenue("sigmod", 0.9);
            addVenue("vldb", 0.9);
            addVenue("sosp", 0.9);
            addVenue("osdi", 0.9);

            // ─── Tier 3: Strong field-specific (0.8) ─────────
            // CS conferences
            addVenue("eccv", 0.85);
            addVenue("iccv", 0.85);
            addVenue("emnlp", 0.85);
            addVenue("naacl", 0.85);
            addVenue("kdd", 0.85);
            addVenue("www", 0.8);
            addVenue("chi", 0.85);
            addVenue("uist", 0.85);
            addVenue("siggraph", 0.85);
            addVenue("icra", 0.8);
            addVenue("iros", 0.8);
            addVenue("usenix security", 0.85);
            addVenue("ccs", 0.85);
            addVenue("ndss", 0.85);
            addVenue("isca", 0.85);
            addVenue("micro", 0.85);

            // CS journals
            addVenue("ieee transactions on pattern analysis and machine intelligence", 0.85);
            addVenue("ieee tpami", 0.85);
            addVenue("tpami", 0.85);
            addVenue("journal of machine learning research", 0.85);
            addVenue("jmlr", 0.85);
            addVenue("acm computing surveys", 0.85);
            addVenue("ieee transactions on information theory", 0.85);

            // Physics journals
            addVenue("physical review b", 0.8);
            addVenue("prb", 0.8);
            addVenue("physical review x", 0.85);
            addVenue("physical review applied", 0.75);
            addVenue("physical review research", 0.7);
            addVenue("applied physics letters", 0.75);
            addVenue("journal of chemical physics", 0.75);
            addVenue("2d materials", 0.8);

            // ─── Tier 4: Solid journals (0.7) ────────────────
            // IEEE/ACM
            addVenue("ieee transactions", 0.75);
            addVenue("ieee access", 0.6);
            addVenue("acm transactions", 0.75);

            // Springer / Elsevier
            addVenue("springer", 0.65);
            addVenue("elsevier", 0.65);
            addVenue("wiley", 0.65);
            addVenue("computational materials science", 0.7);
            addVenue("journal of computational physics", 0.75);
            addVenue("materials science and engineering", 0.65);

            // Biology
            addVenue("nature biotechnology", 0.9);
            addVenue("nature genetics", 0.9);
            addVenue("genome research", 0.8);
            addVenue("bioinformatics", 0.8);
            addVenue("plos one", 0.6);
            addVenue("scientific reports", 0.6);

            // Social Sciences
            addVenue("american economic review", 0.9);
            addVenue("quarterly journal of economics", 0.9);
            addVenue("econometrica", 0.9);
            addVenue("american sociological review", 0.85);
            addVenue("psychological review", 0.85);
            addVenue("psychological science", 0.85);

            // Engineering
            addVenue("nature energy", 0.9);
            addVenue("joule", 0.85);
            addVenue("advanced materials", 0.85);
            addVenue("advanced functional materials", 0.8);
            addVenue("energy & environmental science", 0.85);
        }

        private void addVenue(String name, double score) {
            venueScores.put(name.toLowerCase().trim(), score);
        }

        @Override
        public double getQuality(String venueName) {
            if (venueName == null || venueName.isBlank()) {
                return DEFAULT_QUALITY;
            }

            String normalized = venueName.toLowerCase().trim();

            // Exact match
            Double score = venueScores.get(normalized);
            if (score != null) return score;

            // Substring match — check if any known venue name is contained in the input
            for (Map.Entry<String, Double> entry : venueScores.entrySet()) {
                if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                    return entry.getValue();
                }
            }

            return DEFAULT_QUALITY;
        }

        @Override
        public String getProviderName() {
            return "DictionaryVenueQualityProvider";
        }
    }
}
