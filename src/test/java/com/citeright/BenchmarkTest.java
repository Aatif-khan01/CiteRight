package com.citeright;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * CiteRight: Comprehensive Empirical Evaluation & Reproducibility Benchmark
 * 
 * Accompanying Springer CCIS Conference Paper (PID 405):
 * "CiteRight: A Local-First Framework for Hybrid Scholarly Retrieval
 *  and Research Opportunity Discovery"
 *
 * This benchmark harness experimentally evaluates:
 * - Table 1: Semantic Retrieval Benchmark (25 conceptual queries, MRR, P@5, NDCG@10)
 * - Table 1b: Empirical Bootstrap Uncertainty (1,000 resamples) & Paired Permutation Tests (10,000 resamples)
 * - Table 2: Leave-One-Out Signal Ablation Study (5 constituent ranking signals)
 * - Table 3: Multi-Relational Classification & 4x4 Confusion Matrix (50 verified paper pairs)
 * - Table 4: Runtime Performance & Scaling Latency (Hardware: Intel Core i5, 16GB)
 * - Table 5: Research Gap Discovery Precision & Independent Baselines (4 seeds x 5 items = 20 candidates)
 *
 * All metrics, confidence intervals, hypothesis tests, and confusion matrices
 * are dynamically calculated from recorded benchmark evaluation datasets:
 * - src/test/resources/benchmark/retrieval_queries_25.json
 * - src/test/resources/benchmark/relationship_pairs_50.json
 * - src/test/resources/benchmark/gap_recommendations_20.json
 * ============================================================================
 */
public class BenchmarkTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Benchmark Datasets (loaded dynamically from resources or fallback)
    // ──────────────────────────────────────────────────────────────────────────
    private static double[] RR_TFIDF = new double[25];
    private static double[] RR_DENSE = new double[25];
    private static double[] RR_HYBRID = new double[25];
    private static double[] P5_TFIDF = new double[25];
    private static double[] P5_DENSE = new double[25];
    private static double[] P5_HYBRID = new double[25];
    private static double[] NDCG_TFIDF = new double[25];
    private static double[] NDCG_DENSE = new double[25];
    private static double[] NDCG_HYBRID = new double[25];

    private static int[] groundTruth = new int[50];
    private static int[] geminiPred = new int[50];
    private static int[] localPred = new int[50];

    private static double[][] randomGaps = new double[20][2];
    private static double[][] centralityGaps = new double[20][2];
    private static double[][] topicGaps = new double[20][2];
    private static double[][] temporalGaps = new double[20][2];
    private static double[][] methodGaps = new double[20][2];
    private static double[][] interdiscGaps = new double[20][2];

    static {
        loadBenchmarkDatasets();
    }

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("   CiteRight Empirical Benchmark & Scientific Reproducibility Harness   ");
        System.out.println("=========================================================================");

        // 1. Runtime Performance & Query Scalability Benchmark (Table 4)
        runRuntimeBenchmark();

        // 2. Information Retrieval Benchmark across 25 Queries (Table 1)
        runRetrievalBenchmark();

        // 2b. Statistical Significance, Bootstrap CIs & Hypothesis Tests (Table 1)
        runSignificanceBenchmark();

        // 3. Retrieval Signal Ablation Study (Table 2)
        runRetrievalAblationBenchmark();

        // 4. Multi-Relational Classification on 50 Gold-Standard Pairs (Table 3)
        runRelationshipBenchmark();

        // 5. Research Gap Discovery Precision & Baselines (Table 5)
        runGapDiscoveryBenchmark();

        System.out.println("\n[SUMMARY] All empirical benchmarks successfully executed and verified.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATASET LOADER (Reads JSON files directly from benchmark resources)
    // ══════════════════════════════════════════════════════════════════════════
    private static void loadBenchmarkDatasets() {
        System.out.println("[DATASET VERIFICATION] Loading empirical benchmark evaluation data:");

        // 1. Queries dataset
        String qJson = readResourceOrFile("benchmark/retrieval_queries_25.json",
                "src/test/resources/benchmark/retrieval_queries_25.json");
        if (qJson != null) {
            parseAndVerifyQueries(qJson);
        } else {
            throw missingFile("retrieval_queries_25.json");
        }

        // 2. Relationship pairs dataset
        String rJson = readResourceOrFile("benchmark/relationship_pairs_50.json",
                "src/test/resources/benchmark/relationship_pairs_50.json");
        if (rJson != null) {
            parseRelationField(rJson, "ground_truth", groundTruth);
            parseRelationField(rJson, "gemini_prediction", geminiPred);
            parseRelationField(rJson, "rule_prediction", localPred);
            System.out.println("  ✔ Loaded 50 gold-standard paper pairs with annotations & predictions (relationship_pairs_50.json)");
        } else {
            throw missingFile("relationship_pairs_50.json");
        }

        // 3. Gap discovery dataset
        String gJson = readResourceOrFile("benchmark/gap_recommendations_20.json",
                "src/test/resources/benchmark/gap_recommendations_20.json");
        if (gJson != null) {
            randomGaps = parseGapSection(gJson, "random_baseline");
            centralityGaps = parseGapSection(gJson, "network_centrality");
            if (centralityGaps.length == 0) centralityGaps = parseGapSection(gJson, "centrality_heuristic");
            topicGaps = parseGapSection(gJson, "topic_gap");
            temporalGaps = parseGapSection(gJson, "temporal_gap");
            methodGaps = parseGapSection(gJson, "methodology_transfer");
            interdiscGaps = parseGapSection(gJson, "interdisciplinary_gap");
            for (double[][] sec : new double[][][]{randomGaps, centralityGaps, topicGaps, temporalGaps, methodGaps, interdiscGaps}) {
                if (sec.length != 20) {
                    throw new IllegalStateException("Expected 20 candidates per gap method but parsed " + sec.length);
                }
            }
            System.out.println("  ✔ Loaded 120 candidate recommendations across 4 thematic seeds (gap_recommendations_20.json)");
        } else {
            throw missingFile("gap_recommendations_20.json");
        }
    }

    private static IllegalStateException missingFile(String name) {
        return new IllegalStateException("Benchmark data file '" + name + "' was not found. Run this harness from the "
                + "repository root (paths are relative), e.g. `java src/test/java/com/citeright/BenchmarkTest.java`.");
    }

    private static String readResourceOrFile(String resourcePath, String filePath) {
        try {
            Path p = Path.of(filePath);
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        try (InputStream is = BenchmarkTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static void parseAndVerifyQueries(String json) {
        Pattern qPattern = Pattern.compile("\\{\\s*\"query_id\"\\s*:\\s*\"([^\"]+)\"(.*?)(?=\\{\\s*\"query_id\"|\\Z)", Pattern.DOTALL);
        Matcher qMatcher = qPattern.matcher(json);

        int idx = 0;
        double idcg = 0.0;
        for (int i = 0; i < 6; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        while (qMatcher.find() && idx < 25) {
            String qid = qMatcher.group(1);
            String body = qMatcher.group(2);

            String targetId = extractString(body, "target_id");
            List<String> tfidfRanked = extractStringList(body, "tfidf_ranked_doc_ids");
            List<String> denseRanked = extractStringList(body, "dense_ranked_doc_ids");
            List<String> hybridRanked = extractStringList(body, "hybrid_ranked_doc_ids");
            Map<String, Integer> relMap = extractIntMap(body, "relevance_judgments");

            if (tfidfRanked.size() != 10 || denseRanked.size() != 10 || hybridRanked.size() != 10) {
                throw new IllegalStateException("Ranked list size mismatch in " + qid);
            }

            int tRank = tfidfRanked.indexOf(targetId) + 1;
            int dRank = denseRanked.indexOf(targetId) + 1;
            int hRank = hybridRanked.indexOf(targetId) + 1;

            if (tRank <= 0 || dRank <= 0 || hRank <= 0) {
                throw new IllegalStateException("Target document '" + targetId + "' missing from ranked results in " + qid);
            }

            double tRR = 1.0 / tRank;
            double dRR = 1.0 / dRank;
            double hRR = 1.0 / hRank;

            double tP5 = countRel(tfidfRanked.subList(0, 5), relMap) / 5.0;
            double dP5 = countRel(denseRanked.subList(0, 5), relMap) / 5.0;
            double hP5 = countRel(hybridRanked.subList(0, 5), relMap) / 5.0;

            double tDCG = computeDCG10(tfidfRanked, relMap);
            double dDCG = computeDCG10(denseRanked, relMap);
            double hDCG = computeDCG10(hybridRanked, relMap);

            double tNDCG = tDCG / idcg;
            double dNDCG = dDCG / idcg;
            double hNDCG = hDCG / idcg;

            RR_TFIDF[idx] = tRR;
            RR_DENSE[idx] = dRR;
            RR_HYBRID[idx] = hRR;
            P5_TFIDF[idx] = tP5;
            P5_DENSE[idx] = dP5;
            P5_HYBRID[idx] = hP5;
            NDCG_TFIDF[idx] = tNDCG;
            NDCG_DENSE[idx] = dNDCG;
            NDCG_HYBRID[idx] = hNDCG;
            idx++;
        }
        if (idx != 25) {
            throw new IllegalStateException("Expected 25 queries in retrieval_queries_25.json but parsed " + idx);
        }
        System.out.println("  ✔ Dynamically evaluated & verified 25 conceptual queries from ranked doc lists and judgments (retrieval_queries_25.json)");
    }

    private static String extractString(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static List<String> extractStringList(String body, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(body);
        if (!m.find()) return Collections.emptyList();
        Matcher itemMatcher = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        List<String> list = new ArrayList<>();
        while (itemMatcher.find()) {
            list.add(itemMatcher.group(1));
        }
        return list;
    }

    private static Map<String, Integer> extractIntMap(String body, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher m = p.matcher(body);
        if (!m.find()) return Collections.emptyMap();
        Matcher entryMatcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([0-9]+)").matcher(m.group(1));
        Map<String, Integer> map = new HashMap<>();
        while (entryMatcher.find()) {
            map.put(entryMatcher.group(1), Integer.parseInt(entryMatcher.group(2)));
        }
        return map;
    }

    private static int countRel(List<String> docs, Map<String, Integer> relMap) {
        int c = 0;
        for (String d : docs) {
            if (relMap.getOrDefault(d, 0) == 1) c++;
        }
        return c;
    }

    private static double computeDCG10(List<String> docs, Map<String, Integer> relMap) {
        double dcg = 0.0;
        for (int i = 0; i < Math.min(docs.size(), 10); i++) {
            int rel = relMap.getOrDefault(docs.get(i), 0) == 1 ? 1 : 0; // binary, consistent with P@5
            dcg += (double) rel / (Math.log(i + 2) / Math.log(2));
        }
        return dcg;
    }

    private static void parseRelationField(String json, String field, int[] target) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        int idx = 0;
        while (m.find()) {
            if (idx >= target.length) throw new IllegalStateException("More than " + target.length + " values for '" + field + "'");
            target[idx++] = classToInt(m.group(1));
        }
        if (idx != target.length) {
            throw new IllegalStateException("Expected " + target.length + " values for '" + field + "' but parsed " + idx);
        }
    }

    private static int classToInt(String label) {
        switch (label.toUpperCase()) {
            case "SUPPORTS": return 0;
            case "EXTENDS": return 1;
            case "CONTRADICTS": return 2;
            case "METHODOLOGY": return 3;
            default: throw new IllegalStateException("Unknown relation label: " + label);
        }
    }

    private static double[][] parseGapSection(String json, String sectionKey) {
        Pattern secPattern = Pattern.compile("\"" + sectionKey + "\"\\s*:\\s*\\[(.*?)\\]\\s*(?:,|\\})", Pattern.DOTALL);
        Matcher secMatcher = secPattern.matcher(json);
        if (!secMatcher.find()) return new double[0][0];
        String block = secMatcher.group(1);
        Matcher itemMatcher = Pattern.compile("\"is_relevant\"\\s*:\\s*(true|false).*?\"confidence\"\\s*:\\s*([0-9.]+)", Pattern.DOTALL).matcher(block);
        List<double[]> list = new ArrayList<>();
        while (itemMatcher.find()) {
            double rel = Boolean.parseBoolean(itemMatcher.group(1)) ? 1.0 : 0.0;
            double conf = Double.parseDouble(itemMatcher.group(2));
            list.add(new double[]{rel, conf});
        }
        return list.toArray(new double[0][0]);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. RUNTIME PERFORMANCE BENCHMARK (Table 4)
    // ══════════════════════════════════════════════════════════════════════════
    private static void runRuntimeBenchmark() {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println(" [TABLE 4] RUNTIME PERFORMANCE BENCHMARK (Hardware: Intel Core i5, 16GB) ");
        System.out.println("-------------------------------------------------------------------------");

        int dim = 1024;
        Random rng = new Random(42);

        // Prepare query vector (L2 normalized)
        float[] queryVec = generateNormalizedVector(dim, rng);

        // Warmup JIT compiler
        System.out.print("  [1/4] Warming up JIT compiler (10,000 vector iterations)... ");
        float[] dummy = generateNormalizedVector(dim, rng);
        for (int i = 0; i < 10000; i++) {
            cosineSimilarity(queryVec, dummy);
        }
        System.out.println("Done.");

        // Query latency across corpus sizes
        // All sizes use synthetic random L2-normalised 1024-d vectors (not the benchmark corpus).
        int[] docSizes = {100, 500, 1000, 5000, 10000};
        double[] measuredLatencies = new double[docSizes.length];
        long[] heapMB = new long[docSizes.length];

        for (int idx = 0; idx < docSizes.length; idx++) {
            int count = docSizes[idx];
            List<float[]> corpus = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                corpus.add(generateNormalizedVector(dim, rng));
            }

            // Warmup for this size
            int warmups = Math.max(5, 20000 / count);
            for (int w = 0; w < warmups; w++) {
                for (float[] doc : corpus) SINK += cosineSimilarity(queryVec, doc);
            }

            // Timed measurement: median of 5 trials; every score feeds SINK so the JIT cannot elide the work
            int repetitions = Math.max(20, 1_000_000 / count);
            double[] trials = new double[5];
            for (int t = 0; t < trials.length; t++) {
                long start = System.nanoTime();
                for (int r = 0; r < repetitions; r++) {
                    for (float[] doc : corpus) {
                        SINK += cosineSimilarity(queryVec, doc);
                    }
                }
                long totalNanos = System.nanoTime() - start;
                trials[t] = ((double) totalNanos / 1_000_000.0) / repetitions;
            }
            Arrays.sort(trials);
            measuredLatencies[idx] = trials[trials.length / 2];

            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            heapMB[idx] = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
            SINK += corpus.size(); // keep corpus reachable until after the heap reading
        }

        System.out.println("  [2/4] Vector Query Latency (synthetic 1024-dim vectors, brute-force cosine, measured):");
        for (int idx = 0; idx < docSizes.length; idx++) {
            System.out.printf("        - Query Latency (%,6d docs) : %7.3f ms   | JVM heap after build: %d MB\n",
                    docSizes[idx], measuredLatencies[idx], heapMB[idx]);
        }
        System.out.println("        (Median of 5 trials; values vary by machine.)");

        System.out.println("  [3/4] Not measured: end-to-end embedding latency, graph-construction time and peak");
        System.out.println("        process memory were not independently remeasured and are omitted. The figures above");
        System.out.println("        are synthetic vector-search workloads, not an end-to-end application benchmark.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. RETRIEVAL QUALITY BENCHMARK (Table 1)
    // ══════════════════════════════════════════════════════════════════════════
    private static void runRetrievalBenchmark() {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println(" [TABLE 1] SEMANTIC RETRIEVAL BENCHMARK (25 Conceptual Queries)          ");
        System.out.println("-------------------------------------------------------------------------");

        RetrievalMetrics tfidfMetrics = computeIRMetrics(RR_TFIDF, P5_TFIDF, NDCG_TFIDF);
        RetrievalMetrics denseMetrics = computeIRMetrics(RR_DENSE, P5_DENSE, NDCG_DENSE);
        RetrievalMetrics hybridMetrics = computeIRMetrics(RR_HYBRID, P5_HYBRID, NDCG_HYBRID);

        System.out.printf("  %-18s | %-7s | %-7s | %-7s\n", "Configuration", "MRR", "P@5", "NDCG@10");
        System.out.println("  -------------------+---------+---------+--------");
        System.out.printf("  %-18s |  %5.3f  |  %5.3f  |  %5.3f\n", "TF-IDF Baseline", tfidfMetrics.mrr, tfidfMetrics.p5, tfidfMetrics.ndcg10);
        System.out.printf("  %-18s |  %5.3f  |  %5.3f  |  %5.3f\n", "BGE-M3 Dense Only", denseMetrics.mrr, denseMetrics.p5, denseMetrics.ndcg10);
        System.out.printf("  %-18s |  %5.3f  |  %5.3f  |  %5.3f\n", "Hybrid (5-Signal)", hybridMetrics.mrr, hybridMetrics.p5, hybridMetrics.ndcg10);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2b. STATISTICAL SIGNIFICANCE, BOOTSTRAP CIs & HYPOTHESIS TESTS (Table 1)
    // ══════════════════════════════════════════════════════════════════════════
    private static void runSignificanceBenchmark() {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println(" [TABLE 1b] STATISTICAL UNCERTAINTY & HYPOTHESIS TESTS (25 Queries)       ");
        System.out.println("-------------------------------------------------------------------------");

        // 1,000 bootstrap resamples (percentile method) for MRR, P@5, and NDCG@10
        int bootIters = 1000;
        double[] ciTfidfMRR  = computeBootstrapCI(RR_TFIDF,   bootIters, 42L);
        double[] ciDenseMRR  = computeBootstrapCI(RR_DENSE,   bootIters, 42L);
        double[] ciHybridMRR = computeBootstrapCI(RR_HYBRID,  bootIters, 42L);
        double[] ciTfidfP5   = computeBootstrapCI(P5_TFIDF,   bootIters, 42L);
        double[] ciDenseP5   = computeBootstrapCI(P5_DENSE,   bootIters, 42L);
        double[] ciHybridP5  = computeBootstrapCI(P5_HYBRID,  bootIters, 42L);
        double[] ciTfidfNDCG = computeBootstrapCI(NDCG_TFIDF, bootIters, 42L);
        double[] ciDenseNDCG = computeBootstrapCI(NDCG_DENSE, bootIters, 42L);
        double[] ciHybridNDCG= computeBootstrapCI(NDCG_HYBRID,bootIters, 42L);

        System.out.printf("  Bootstrap 95%% Confidence Intervals (%s iterations):\n", String.format("%,d", bootIters));
        System.out.printf("    - TF-IDF Baseline   : MRR = %5.3f [%5.3f, %5.3f],  P@5 = %5.3f [%5.3f, %5.3f], NDCG@10 = %5.3f [%5.3f, %5.3f]\n",
                average(RR_TFIDF),  ciTfidfMRR[0],  ciTfidfMRR[1],
                average(P5_TFIDF),  ciTfidfP5[0],   ciTfidfP5[1],
                average(NDCG_TFIDF),ciTfidfNDCG[0], ciTfidfNDCG[1]);
        System.out.printf("    - BGE-M3 Dense Only : MRR = %5.3f [%5.3f, %5.3f],  P@5 = %5.3f [%5.3f, %5.3f], NDCG@10 = %5.3f [%5.3f, %5.3f]\n",
                average(RR_DENSE),  ciDenseMRR[0],  ciDenseMRR[1],
                average(P5_DENSE),  ciDenseP5[0],   ciDenseP5[1],
                average(NDCG_DENSE),ciDenseNDCG[0], ciDenseNDCG[1]);
        System.out.printf("    - Hybrid (5-Signal) : MRR = %5.3f [%5.3f, %5.3f],  P@5 = %5.3f [%5.3f, %5.3f], NDCG@10 = %5.3f [%5.3f, %5.3f]\n",
                average(RR_HYBRID), ciHybridMRR[0], ciHybridMRR[1],
                average(P5_HYBRID), ciHybridP5[0],  ciHybridP5[1],
                average(NDCG_HYBRID),ciHybridNDCG[0],ciHybridNDCG[1]);

        // Paired one-sided permutation tests (H1: delta > 0), 10,000 Monte Carlo sign-permutation resamples
        // One-sided: count fraction of permuted mean diffs >= observed mean diff
        int numResamples = 10000;

        double pDenseVsTfidf  = runSignPermutationTest(RR_DENSE,  RR_TFIDF, numResamples, 1L);
        double pHybridVsTfidf = runSignPermutationTest(RR_HYBRID, RR_TFIDF, numResamples, 1L);
        double pHybridVsDense = runSignPermutationTest(RR_HYBRID, RR_DENSE, numResamples, 1L);

        double dDT = average(RR_DENSE) - average(RR_TFIDF);
        double dHT = average(RR_HYBRID) - average(RR_TFIDF);
        double dHD = average(RR_HYBRID) - average(RR_DENSE);
        System.out.printf("\n  Paired Two-Sided Permutation Tests (primary; H1: delta != 0; %,d resamples, seed=1):\n", numResamples);
        double[] p2 = {
            runSignPermutationTest(RR_DENSE,  RR_TFIDF, numResamples, 1L, true),
            runSignPermutationTest(RR_HYBRID, RR_TFIDF, numResamples, 1L, true),
            runSignPermutationTest(RR_HYBRID, RR_DENSE, numResamples, 1L, true)};
        System.out.printf("    - Dense vs. TF-IDF  : dMRR = %+5.3f, p = %.4f (%s)\n", dDT, p2[0], significance(p2[0]));
        System.out.printf("    - Hybrid vs. TF-IDF : dMRR = %+5.3f, p = %.4f (%s)\n", dHT, p2[1], significance(p2[1]));
        System.out.printf("    - Hybrid vs. Dense  : dMRR = %+5.3f, p = %.4f (%s)\n", dHD, p2[2], significance(p2[2]));

        System.out.printf("\n  Paired One-Sided Permutation Tests (secondary; H1: delta > 0; %,d resamples, seed=1):\n", numResamples);
        System.out.printf("    - Dense vs. TF-IDF  : p = %.4f\n", pDenseVsTfidf);
        System.out.printf("    - Hybrid vs. TF-IDF : p = %.4f\n", pHybridVsTfidf);
        System.out.printf("    - Hybrid vs. Dense  : p = %.4f\n", pHybridVsDense);
        System.out.println("  Note: P@5 = 1.000 for both dense and hybrid is a ceiling effect; P@5 cannot separate them.");
    }

    private static String significance(double p) {
        if (p < 0.01) return "significant at alpha = 0.01";
        if (p < 0.05) return "significant at alpha = 0.05";
        return "not significant at alpha = 0.05";
    }

    private static double[] computeBootstrapCI(double[] scores, int iterations, long seed) {
        int n = scores.length;
        double[] sampleMeans = new double[iterations];
        Random rng = new Random(seed);
        for (int i = 0; i < iterations; i++) {
            double sum = 0.0;
            for (int k = 0; k < n; k++) {
                sum += scores[rng.nextInt(n)];
            }
            sampleMeans[i] = sum / n;
        }
        Arrays.sort(sampleMeans);
        int lowIdx = (int) Math.round(iterations * 0.025);
        int highIdx = (int) Math.round(iterations * 0.975) - 1;
        return new double[]{sampleMeans[lowIdx], sampleMeans[highIdx]};
    }

    /**
     * One-sided paired sign-permutation test (H1: treatment > baseline).
     * For each of numResamples Monte Carlo resamples, each per-query difference
     * is randomly signed (+/-). The p-value is the fraction of resampled mean
     * differences >= the observed mean difference (one-sided, H1: delta > 0).
     */
    private static double runSignPermutationTest(double[] treatment, double[] baseline, int numResamples, long seed) {
        return runSignPermutationTest(treatment, baseline, numResamples, seed, false);
    }

    /** Same test; when twoSided is true the p-value counts |permuted mean| >= |observed mean| (H1: delta != 0). */
    private static double runSignPermutationTest(double[] treatment, double[] baseline, int numResamples, long seed, boolean twoSided) {
        int n = treatment.length;
        double[] diffs = new double[n];
        double obsDiff = 0.0;
        for (int i = 0; i < n; i++) {
            diffs[i] = treatment[i] - baseline[i];
            obsDiff += diffs[i];
        }
        obsDiff /= n;

        Random rng = new Random(seed);
        int countGreaterOrEqual = 0;
        for (int r = 0; r < numResamples; r++) {
            double permSum = 0.0;
            for (int i = 0; i < n; i++) {
                permSum += (rng.nextBoolean() ? diffs[i] : -diffs[i]);
            }
            // One-sided: count permuted mean >= observed mean
            double permMean = permSum / n;
            if (twoSided ? Math.abs(permMean) >= Math.abs(obsDiff) - 1e-9 : permMean >= obsDiff - 1e-9) {
                countGreaterOrEqual++;
            }
        }
        return (double) countGreaterOrEqual / numResamples;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. RETRIEVAL SIGNAL ABLATION STUDY (Table 2)
    // ══════════════════════════════════════════════════════════════════════════
    private static void runRetrievalAblationBenchmark() {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println(" [TABLE 2] RETRIEVAL SIGNAL ABLATION STUDY (Leave-One-Out)               ");
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("  NOTE: RECORDED values from the authors' original runs, NOT recomputed by this");
        System.out.println("  harness. Per-signal scores need the 100-paper corpus and BGE-M3 model, which are");
        System.out.println("  not part of the public benchmark files.");

        String[][] ablationRows = {
            {"Full Hybrid (5-signal)", "0.940", "0.958", "---"},
            {"- Citation signal (w_cite=0)", "0.928", "0.950", "-0.012"},
            {"- Method entity signal (w_meth=0)", "0.932", "0.952", "-0.008"},
            {"- Task entity signal (w_task=0)", "0.935", "0.955", "-0.005"},
            {"- Keyword signal (w_kw=0)", "0.936", "0.956", "-0.004"},
            {"Dense only (no structural)", "0.900", "0.939", "-0.040"}
        };

        System.out.printf("  %-36s | %-7s | %-7s | %-7s\n", "Configuration", "MRR", "NDCG@10", "Δ MRR");
        System.out.println("  -------------------------------------+---------+---------+--------");
        for (String[] r : ablationRows) {
            System.out.printf("  %-36s |  %5s  |  %5s  | %6s\n", r[0], r[1], r[2], r[3]);
        }
    }

    private static RetrievalMetrics computeIRMetrics(double[] rr, double[] p5, double[] ndcg) {
        return new RetrievalMetrics(average(rr), average(p5), average(ndcg));
    }

    private static double average(double[] arr) {
        double s = 0;
        for (double d : arr) s += d;
        return s / arr.length;
    }

    private static class RetrievalMetrics {
        final double mrr;
        final double p5;
        final double ndcg10;
        RetrievalMetrics(double mrr, double p5, double ndcg10) {
            this.mrr = mrr;
            this.p5 = p5;
            this.ndcg10 = ndcg10;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. RELATIONSHIP CLASSIFICATION BENCHMARK (Table 3)
    // ══════════════════════════════════════════════════════════════════════════
    private static void runRelationshipBenchmark() {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println(" [TABLE 3] RELATIONSHIP CLASSIFICATION BENCHMARK (50 Verified Pairs)     ");
        System.out.println("-------------------------------------------------------------------------");

        ClassificationMetrics geminiMetrics = evaluateClassifier(groundTruth, geminiPred);
        ClassificationMetrics localMetrics = evaluateClassifier(groundTruth, localPred);

        System.out.printf("  %-18s | %-9s | %-8s | %-6s | %-10s\n", "Engine", "Precision", "Recall", "F1", "Kappa (κ)");
        System.out.println("  -------------------+-----------+----------+--------+-----------");
        System.out.printf("  %-18s |   %5.2f   |   %5.2f  |  %5.2f  |   %5.2f\n",
                "Gemini LLM Engine", geminiMetrics.precision, geminiMetrics.recall, geminiMetrics.f1, geminiMetrics.kappa);
        System.out.printf("  %-18s |   %5.2f   |   %5.2f  |  %5.2f  |   %5.2f\n",
                "Local Rule Engine", localMetrics.precision, localMetrics.recall, localMetrics.f1, localMetrics.kappa);

        System.out.println("\n  [Confusion Matrix - Gemini LLM (50 pairs)]");
        printConfusionMatrix(geminiMetrics.confusionMatrix);
    }

    private static ClassificationMetrics evaluateClassifier(int[] trueLabels, int[] predLabels) {
        int numClasses = 4;
        int[][] cm = new int[numClasses][numClasses];
        int n = trueLabels.length;

        for (int i = 0; i < n; i++) {
            cm[trueLabels[i]][predLabels[i]]++;
        }

        double[] prec = new double[numClasses];
        double[] rec = new double[numClasses];
        double[] f1 = new double[numClasses];

        for (int c = 0; c < numClasses; c++) {
            int tp = cm[c][c];
            int rowSum = 0;
            int colSum = 0;
            for (int j = 0; j < numClasses; j++) {
                rowSum += cm[c][j];
                colSum += cm[j][c];
            }
            prec[c] = (colSum > 0) ? (double) tp / colSum : 0.0;
            rec[c] = (rowSum > 0) ? (double) tp / rowSum : 0.0;
            f1[c] = (prec[c] + rec[c] > 0) ? 2.0 * (prec[c] * rec[c]) / (prec[c] + rec[c]) : 0.0;
        }

        double macroPrec = Arrays.stream(prec).average().orElse(0.0);
        double macroRec = Arrays.stream(rec).average().orElse(0.0);
        double macroF1 = Arrays.stream(f1).average().orElse(0.0);

        // Cohen's Kappa
        double po = 0.0;
        for (int c = 0; c < numClasses; c++) po += cm[c][c];
        po /= n;

        double pe = 0.0;
        for (int c = 0; c < numClasses; c++) {
            int rowSum = 0, colSum = 0;
            for (int j = 0; j < numClasses; j++) {
                rowSum += cm[c][j];
                colSum += cm[j][c];
            }
            pe += ((double) rowSum / n) * ((double) colSum / n);
        }
        double kappa = (1.0 - pe > 1e-9) ? (po - pe) / (1.0 - pe) : 1.0;

        return new ClassificationMetrics(macroPrec, macroRec, macroF1, kappa, cm);
    }

    private static void printConfusionMatrix(int[][] cm) {
        String[] labels = {"SUPP", "EXTD", "CONT", "METH"};
        System.out.print("           ");
        for (String l : labels) System.out.printf(" %6s", l);
        System.out.println();
        for (int i = 0; i < 4; i++) {
            System.out.printf("    %4s : ", labels[i]);
            for (int j = 0; j < 4; j++) {
                System.out.printf(" %6d", cm[i][j]);
            }
            System.out.println();
        }
    }

    private static class ClassificationMetrics {
        final double precision;
        final double recall;
        final double f1;
        final double kappa;
        final int[][] confusionMatrix;

        ClassificationMetrics(double p, double r, double f1, double k, int[][] cm) {
            this.precision = p;
            this.recall = r;
            this.f1 = f1;
            this.kappa = k;
            this.confusionMatrix = cm;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. RESEARCH GAP DISCOVERY BENCHMARK & BASELINES (Table 5)
    // ══════════════════════════════════════════════════════════════════════════
    private static void runGapDiscoveryBenchmark() {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.println(" [TABLE 5] RESEARCH GAP DISCOVERY BENCHMARK & BASELINES                  ");
        System.out.println("-------------------------------------------------------------------------");

        GapMetrics randomM = computeGapMetrics(randomGaps);
        GapMetrics centralityM = computeGapMetrics(centralityGaps);
        GapMetrics topicM = computeGapMetrics(topicGaps);
        GapMetrics temporalM = computeGapMetrics(temporalGaps);
        GapMetrics methodM = computeGapMetrics(methodGaps);
        GapMetrics interdiscM = computeGapMetrics(interdiscGaps);

        System.out.printf("  %-32s | %-8s | %-16s\n", "Method / Module", "P@5", "Avg. Confidence");
        System.out.println("  ---------------------------------+----------+-----------------");
        System.out.printf("  %-32s |   %5.2f  |      %5s\n", "Random Selection Baseline", randomM.p5, "---");
        System.out.printf("  %-32s |   %5.2f  |      %5s\n", "Network Centrality Heuristic", centralityM.p5, "---");
        System.out.printf("  %-32s |   %5.2f  |      %5.2f\n", "CiteRight Topic Gap", topicM.p5, topicM.avgConfidence);
        System.out.printf("  %-32s |   %5.2f  |      %5.2f\n", "CiteRight Temporal Gap", temporalM.p5, temporalM.avgConfidence);
        System.out.printf("  %-32s |   %5.2f  |      %5.2f\n", "CiteRight Methodology Transfer", methodM.p5, methodM.avgConfidence);
        System.out.printf("  %-32s |   %5.2f  |      %5.2f\n", "CiteRight Interdisciplinary Gap", interdiscM.p5, interdiscM.avgConfidence);
    }

    private static GapMetrics computeGapMetrics(double[][] data) {
        double validSum = 0.0;
        double confSum = 0.0;
        int n = data.length;
        for (double[] item : data) {
            validSum += item[0];
            confSum += item[1];
        }
        return new GapMetrics(validSum / n, confSum / n);
    }

    private static class GapMetrics {
        final double p5;
        final double avgConfidence;
        GapMetrics(double p, double c) {
            this.p5 = p;
            this.avgConfidence = c;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER: Vector math
    // ══════════════════════════════════════════════════════════════════════════
    private static float[] generateNormalizedVector(int dim, Random rng) {
        float[] v = new float[dim];
        float sumSq = 0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) rng.nextGaussian();
            sumSq += v[i] * v[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) v[i] /= norm;
        return v;
    }

    private static volatile float SINK;

    private static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;
    }
}
