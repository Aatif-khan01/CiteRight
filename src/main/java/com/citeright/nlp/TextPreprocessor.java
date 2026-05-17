package com.citeright.nlp;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Text preprocessor for the custom NLP engine.
 * Handles tokenization, stopword removal, normalization, and basic stemming.
 * 
 * This is the foundation layer — all NLP operations start with clean tokens.
 * 
 * Demonstrates: UTILITY CLASS pattern for text processing.
 */
public class TextPreprocessor {

    // Regex patterns for tokenization
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,;:!?()\\[\\]{}\"']+");
    private static final Pattern NON_ALPHA_NUMERIC = Pattern.compile("[^a-z0-9\\-]");

    /**
     * 400+ English stopwords + 50+ academic-specific stopwords.
     * These are extremely common words that carry no meaning for search.
     */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        // Common English stopwords
        "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "shall", "can", "need",
        "dare", "ought", "used", "it", "its", "this", "that", "these", "those",
        "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you",
        "your", "yours", "yourself", "yourselves", "he", "him", "his", "himself",
        "she", "her", "hers", "herself", "they", "them", "their", "theirs",
        "themselves", "what", "which", "who", "whom", "when", "where", "why",
        "how", "all", "each", "every", "both", "few", "more", "most", "other",
        "some", "such", "no", "nor", "not", "only", "own", "same", "so",
        "than", "too", "very", "just", "because", "about", "into", "through",
        "during", "before", "after", "above", "below", "between", "out", "off",
        "over", "under", "again", "further", "then", "once", "here", "there",
        "also", "any", "many", "much", "if", "up", "down", "while", "until",
        "against", "along", "across", "around", "among", "without", "within",
        "upon", "whether", "since", "although", "though", "yet", "still",
        "already", "even", "ever", "never", "now", "always", "sometimes",
        "often", "usually", "however", "therefore", "thus", "hence",
        "moreover", "furthermore", "nevertheless", "nonetheless", "meanwhile",
        "otherwise", "instead", "rather", "either", "neither", "else",
        "wherever", "whenever", "whoever", "whatever", "whichever", "enough",
        "quite", "rather", "almost", "nearly", "perhaps", "maybe",
        "certainly", "surely", "probably", "possibly", "actually", "really",
        "simply", "truly", "basically", "essentially", "particularly",
        "especially", "specifically", "generally", "typically", "mainly",
        "primarily", "largely", "mostly", "partly", "somewhat",

        // Academic-specific stopwords (carry no discriminative value in papers)
        "study", "paper", "research", "article", "work", "report",
        "results", "result", "show", "shows", "shown", "showed",
        "demonstrate", "demonstrated", "demonstrates",
        "find", "found", "findings", "indicate", "indicates", "indicated",
        "suggest", "suggests", "suggested", "reveal", "reveals", "revealed",
        "present", "presents", "presented", "propose", "proposes", "proposed",
        "describe", "describes", "described",
        "investigate", "investigated", "investigates", "investigation",
        "analyze", "analyzed", "analyzes", "analysis",
        "examine", "examined", "examines", "examination",
        "explore", "explored", "explores",
        "discuss", "discussed", "discusses", "discussion",
        "consider", "considered", "considers",
        "observe", "observed", "observes", "observation", "observations",
        "method", "methods", "approach", "approaches", "technique", "techniques",
        "model", "models", "framework", "system", "systems",
        "experiment", "experiments", "experimental",
        "data", "dataset", "datasets", "sample", "samples",
        "figure", "table", "section", "chapter",
        "provide", "provides", "provided",
        "obtain", "obtained", "obtains",
        "perform", "performed", "performs",
        "apply", "applied", "applies", "application", "applications",
        "use", "used", "uses", "using", "based",
        "include", "includes", "included", "including",
        "compare", "compared", "compares", "comparison",
        "measure", "measured", "measures", "measurement",
        "develop", "developed", "develops", "development",
        "improve", "improved", "improves", "improvement",
        "increase", "increased", "increases",
        "decrease", "decreased", "decreases",
        "significant", "significantly", "important", "importance",
        "effect", "effects", "effective", "effectively",
        "due", "recent", "recently", "previous", "previously",
        "new", "novel", "various", "different", "several",
        "high", "higher", "highest", "low", "lower", "lowest",
        "large", "larger", "largest", "small", "smaller", "smallest",
        "first", "second", "third", "one", "two", "three",
        "well", "known", "good", "better", "best",
        "et", "al", "eg", "ie", "etc", "ref", "refs", "fig", "figs",
        "vol", "pp", "doi", "isbn", "issn", "http", "https", "www"
    ));

    /**
     * Short scientific terms that should NEVER be treated as stopwords.
     * These carry critical domain meaning despite being short.
     */
    private static final Set<String> PROTECTED_TERMS = new HashSet<>(Arrays.asList(
        "2d", "3d", "1d", "0d", "ai", "ml", "dl", "nn", "sot", "dft",
        "dmi", "pma", "mtj", "tmr", "gmr", "afm", "fm", "fe", "co",
        "mn", "cr", "ni", "pt", "pd", "ir", "ta", "hf", "mg", "si",
        "ge", "sn", "te", "se", "bi", "sb", "as", "ga", "al", "in",
        "gete", "mose", "wse", "mote", "wte", "cri", "crbr",
        "gnn", "cnn", "rnn", "lstm", "bert", "gpt",
        "rf", "dc", "ac", "uv", "ir", "xrd", "sem", "tem", "stm", "afm",
        "mbe", "cvd", "pvd", "ald", "mos2", "ws2", "bn", "gan", "aln",
        "soc", "mram", "sram", "dram", "cmos", "vlsi", "fpga"
    ));

    /**
     * Tokenizes, normalizes, and removes stopwords from input text.
     * Returns a list of clean, meaningful tokens.
     *
     * @param text The input text (can be a sentence, title, or abstract)
     * @return List of clean tokens
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 1: Normalize unicode characters
        String normalized = normalizeUnicode(text.toLowerCase());

        // Step 2: Split into tokens
        String[] rawTokens = SPLIT_PATTERN.split(normalized);

        // Step 3: Clean, filter, and stem each token
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            // Clean non-alphanumeric (keep hyphens for compound terms)
            token = NON_ALPHA_NUMERIC.matcher(token).replaceAll("");
            token = token.trim();

            // Skip empty tokens
            if (token.isEmpty()) continue;

            // Skip pure numbers (like "2023", "100") unless they're protected
            if (token.matches("\\d+") && !PROTECTED_TERMS.contains(token)) continue;

            // Skip stopwords (unless it's a protected scientific term)
            if (STOPWORDS.contains(token) && !PROTECTED_TERMS.contains(token)) continue;

            // Skip single-character tokens (unless protected)
            if (token.length() == 1 && !PROTECTED_TERMS.contains(token)) continue;

            // Apply basic stemming
            token = stem(token);

            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    /**
     * Normalizes Unicode characters commonly found in scientific text.
     */
    private static String normalizeUnicode(String text) {
        return text
            .replace('\u2010', '-')  // hyphen
            .replace('\u2011', '-')  // non-breaking hyphen
            .replace('\u2012', '-')  // figure dash
            .replace('\u2013', '-')  // en dash
            .replace('\u2014', '-')  // em dash
            .replace('\u2212', '-')  // minus sign
            .replace('\u00d7', 'x')  // multiplication sign
            .replace('\u03b1', 'a').replace('\u03b2', 'b').replace('\u03b3', 'g')
            .replace('\u03b4', 'd').replace('\u03b5', 'e').replace('\u03b6', 'z')
            .replace('\u03b7', 'h').replace('\u03b8', 'q').replace('\u03b9', 'i')
            .replace('\u03ba', 'k').replace('\u03bb', 'l').replace('\u03bc', 'm')
            .replace('\u03bd', 'n').replace('\u03be', 'x').replace('\u03bf', 'o')
            .replace('\u03c0', 'p').replace('\u03c1', 'r').replace('\u03c3', 's')
            .replace('\u03c4', 't').replace('\u03c5', 'u').replace('\u03c6', 'f')
            .replace('\u03c7', 'c').replace('\u03c8', 'y').replace('\u03c9', 'w')
            .replaceAll("[\\u2018\\u2019\\u201A\\u201B]", "'")   // smart quotes
            .replaceAll("[\\u201C\\u201D\\u201E\\u201F]", "\""); // smart double quotes
    }

    /**
     * Basic suffix-stripping stemmer.
     * Reduces words to approximate root forms without needing a full NLP library.
     * E.g., "magnetization" → "magnet", "ferromagnetic" → "ferromagnet"
     */
    private static String stem(String word) {
        if (word.length() <= 3) return word;

        // Don't stem protected terms
        if (PROTECTED_TERMS.contains(word)) return word;

        // Strip common suffixes (order matters — longest first)
        String[][] suffixRules = {
            {"ational", "ate"}, {"tional", "tion"}, {"ization", "ize"},
            {"iveness", "ive"}, {"fulness", "ful"}, {"ousness", "ous"},
            {"ically", "ic"}, {"ation", ""}, {"ness", ""},
            {"ment", ""}, {"ence", ""}, {"ance", ""},
            {"ible", ""}, {"able", ""},
            {"tion", ""}, {"sion", ""},
            {"ally", "al"}, {"ously", "ous"}, {"ively", "ive"},
            {"ting", ""}, {"ing", ""}, {"ied", "y"},
            {"ies", "y"}, {"ical", "ic"},
            {"ment", ""}, {"ful", ""},
            {"ers", ""}, {"er", ""}, {"ed", ""},
            {"ly", ""}, {"es", ""}, {"s", ""}
        };

        for (String[] rule : suffixRules) {
            if (word.endsWith(rule[0]) && word.length() - rule[0].length() + rule[1].length() >= 3) {
                return word.substring(0, word.length() - rule[0].length()) + rule[1];
            }
        }

        return word;
    }

    /**
     * Computes a term frequency map for a list of tokens.
     * TF(t,d) = count(t in d) / total_words(d)
     */
    public static Map<String, Double> computeTermFrequency(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens.isEmpty()) return tf;

        // Count raw frequencies
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }

        // Normalize by document length
        double totalTokens = tokens.size();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            tf.put(entry.getKey(), entry.getValue() / totalTokens);
        }

        return tf;
    }

    /**
     * Returns the set of stopwords (for testing/debugging).
     */
    public static Set<String> getStopwords() {
        return Collections.unmodifiableSet(STOPWORDS);
    }

    /**
     * Returns the set of protected scientific terms.
     */
    public static Set<String> getProtectedTerms() {
        return Collections.unmodifiableSet(PROTECTED_TERMS);
    }
}
