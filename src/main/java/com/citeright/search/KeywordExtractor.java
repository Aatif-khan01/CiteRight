package com.citeright.search;

import java.util.*;

/**
 * Extracts meaningful keywords from a user's sentence.
 * Removes stop words and returns search-optimized terms.
 * 
 * Demonstrates: ENCAPSULATION — internal stop word list and logic are hidden.
 */
public class KeywordExtractor {

    // Common English stop words to filter out
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "a", "an", "the", "and", "or", "but", "is", "are", "was", "were",
        "be", "been", "being", "have", "has", "had", "having", "do", "does",
        "did", "doing", "will", "would", "could", "should", "may", "might",
        "shall", "can", "need", "dare", "ought", "used", "to", "of", "in",
        "for", "on", "with", "at", "by", "from", "as", "into", "through",
        "during", "before", "after", "above", "below", "between", "out",
        "off", "over", "under", "again", "further", "then", "once", "here",
        "there", "when", "where", "why", "how", "all", "both", "each",
        "few", "more", "most", "other", "some", "such", "no", "nor", "not",
        "only", "own", "same", "so", "than", "too", "very", "just", "about",
        "also", "it", "its", "this", "that", "these", "those", "i", "me",
        "my", "myself", "we", "our", "ours", "ourselves", "you", "your",
        "yours", "yourself", "yourselves", "he", "him", "his", "himself",
        "she", "her", "hers", "herself", "they", "them", "their", "theirs",
        "themselves", "what", "which", "who", "whom", "if", "while", "up",
        "because", "until", "against", "don", "doesn", "didn", "won",
        "wouldn", "couldn", "shouldn", "isn", "aren", "wasn", "weren",
        "hasn", "haven", "hadn", "mustn", "needn", "shan", "will",
        "significantly", "recently", "particularly", "especially",
        "however", "therefore", "furthermore", "moreover", "although",
        "nevertheless", "consequently", "meanwhile", "indeed", "thus",
        "hence", "thereby", "wherein", "whereas", "nevertheless",
        "shown", "show", "shows", "showed", "suggest", "suggests",
        "suggested", "indicate", "indicates", "indicated", "demonstrate",
        "demonstrates", "demonstrated", "found", "find", "finds",
        "provide", "provides", "provided", "reveal", "reveals", "revealed",
        "report", "reports", "reported", "propose", "proposes", "proposed",
        "present", "presents", "presented", "describe", "describes",
        "described", "discuss", "discusses", "discussed", "examine",
        "examines", "examined", "investigate", "investigates", "investigated",
        "study", "studies", "studied", "analyze", "analyzes", "analyzed",
        "consider", "considers", "considered", "argue", "argues", "argued",
        "claim", "claims", "claimed", "note", "notes", "noted",
        "according", "based", "due", "given", "well", "known",
        "many", "much", "several", "various", "different",
        "important", "significant", "recent", "current", "previous",
        "second", "last", "next", "following", "existing",
        "potential", "possible", "likely", "effective", "key", "major",
        "main", "primary", "critical", "essential", "necessary",
        "increased", "improved", "reduced", "enhanced", "achieved",
        "used", "using", "use", "make", "made", "making"
    ));

    // Important short scientific terms that should NOT be filtered out despite being ≤2 chars
    private static final Set<String> IMPORTANT_SHORT_TERMS = new HashSet<>(Arrays.asList(
        "2d", "3d", "ai", "ml", "dl", "ir", "uv", "rf", "dc", "ac",
        "ph", "nm", "ev", "qd", "gw", "dft", "soc", "vb", "cb"
    ));

    /**
     * Extracts keywords from a sentence for academic paper search.
     *
     * @param sentence The researcher's input sentence
     * @return Space-separated keywords optimized for search
     */
    public String extract(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) {
            return "";
        }

        // Step 1: Normalize unicode characters (e.g., − to -, × to x)
        String normalized = sentence
                .replace('\u2212', '-')  // unicode minus → hyphen
                .replace('\u2010', '-')  // hyphen → hyphen
                .replace('\u2011', '-')  // non-breaking hyphen
                .replace('\u2013', '-')  // en dash
                .replace('\u2014', '-')  // em dash
                .replace('\u00d7', 'x')  // × → x
                .replace("\u03b1", "alpha").replace("\u03b2", "beta")
                .replace("\u03b3", "gamma").replace("\u03b4", "delta");

        // Step 2: Convert to lowercase and remove punctuation
        // Keep letters, digits, hyphens, and spaces
        String cleaned = normalized.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Step 3: Tokenize
        String[] tokens = cleaned.split("\\s+");

        // Step 4: Remove stop words and filter — keep short words if they are
        // scientifically important or contain digits (like "2d", "3d", chemical identifiers)
        List<String> keywords = new ArrayList<>();
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;

            // Keep important short scientific terms
            if (IMPORTANT_SHORT_TERMS.contains(token)) {
                keywords.add(token);
            }
            // Keep tokens containing digits (likely chemical formulas, measurements)
            else if (token.matches(".*\\d.*") && token.length() >= 2 && !STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
            // Standard filter: length > 2 and not a stop word
            else if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
        }

        // Step 5: Remove duplicates while preserving order
        List<String> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String keyword : keywords) {
            if (seen.add(keyword)) {
                unique.add(keyword);
            }
        }

        // Step 6: Limit to top 12 keywords (increased for more specific searches)
        if (unique.size() > 12) {
            unique = unique.subList(0, 12);
        }

        return String.join(" ", unique);
    }

    /**
     * Extracts keywords and returns them as a list.
     */
    public List<String> extractAsList(String sentence) {
        String result = extract(sentence);
        if (result.isEmpty()) return new ArrayList<>();
        return Arrays.asList(result.split("\\s+"));
    }

    /**
     * Checks if a word is a stop word.
     */
    public boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }
}
