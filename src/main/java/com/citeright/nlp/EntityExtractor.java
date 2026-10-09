package com.citeright.nlp;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex + keyword dictionary-based entity extraction from paper text.
 *
 * Extracts 5 entity types from titles and abstracts:
 *   - METHODS: research methods (e.g., CNN, DFT, Monte Carlo)
 *   - DOMAINS: research domains (e.g., medical imaging, remote sensing)
 *   - TASKS: research tasks (e.g., classification, segmentation)
 *   - DATASETS: known dataset names (e.g., ImageNet, COCO)
 *   - ALGORITHMS: specific algorithms (e.g., Adam, ResNet, BERT)
 *
 * This is the foundation of CiteRight's Entity Graph — the first layer of
 * knowledge extraction that feeds all gap analyzers.
 */
public class EntityExtractor {

    public enum EntityType {
        METHOD, DOMAIN, TASK, DATASET, ALGORITHM
    }

    // ── Method Patterns ──────────────────────────────────────────────────
    private static final List<Pattern> METHOD_PATTERNS = List.of(
        // Imaging / Neuroscience
        Pattern.compile("\\b(fMRI|EEG|MEG|PET\\s*scan|CT\\s*scan|MRI|X-ray)\\b", Pattern.CASE_INSENSITIVE),
        // Biology / Genetics
        Pattern.compile("\\b(CRISPR|PCR|Western\\s*blot|immunohistochemistry|ELISA|flow\\s*cytometry)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(genome-wide\\s*association|GWAS|RNA-seq|proteomics|metabolomics|ChIP-seq)\\b", Pattern.CASE_INSENSITIVE),
        // Statistics
        Pattern.compile("\\b(Monte\\s*Carlo|Bayesian|regression|ANOVA|t-test|chi-square|bootstrapping|Markov\\s*chain)\\b", Pattern.CASE_INSENSITIVE),
        // Deep Learning
        Pattern.compile("\\b(deep\\s*learning|neural\\s*network|CNN|RNN|LSTM|transformer|GAN|VAE|autoencoder)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(attention\\s*mechanism|self-attention|multi-head\\s*attention|cross-attention)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(graph\\s*neural\\s*network|GNN|graph\\s*convolutional|message\\s*passing)\\b", Pattern.CASE_INSENSITIVE),
        // Review types
        Pattern.compile("\\b(survey|meta-analysis|systematic\\s*review|randomized\\s*controlled\\s*trial|RCT)\\b", Pattern.CASE_INSENSITIVE),
        // Physics / Engineering
        Pattern.compile("\\b(finite\\s*element|FEM|CFD|molecular\\s*dynamics|DFT|density\\s*functional)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(ab\\s*initio|first[- ]principles|tight[- ]binding|k\\.p\\s*method)\\b", Pattern.CASE_INSENSITIVE),
        // Qualitative
        Pattern.compile("\\b(qualitative|ethnography|grounded\\s*theory|case\\s*study|thematic\\s*analysis)\\b", Pattern.CASE_INSENSITIVE),
        // RL / Optimization
        Pattern.compile("\\b(reinforcement\\s*learning|Q-learning|policy\\s*gradient|actor[- ]critic)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(genetic\\s*algorithm|particle\\s*swarm|simulated\\s*annealing|evolutionary)\\b", Pattern.CASE_INSENSITIVE),
        // NLP
        Pattern.compile("\\b(natural\\s*language\\s*processing|NLP|named\\s*entity\\s*recognition|NER|sentiment\\s*analysis)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(topic\\s*modeling|LDA|word\\s*embedding|word2vec|TF-IDF)\\b", Pattern.CASE_INSENSITIVE),
        // Computer Vision
        Pattern.compile("\\b(object\\s*detection|image\\s*segmentation|feature\\s*extraction|optical\\s*flow)\\b", Pattern.CASE_INSENSITIVE),
        // User studies
        Pattern.compile("\\b(A/B\\s*test|user\\s*study|usability|eye[- ]tracking|think[- ]aloud)\\b", Pattern.CASE_INSENSITIVE),
        // Simulation
        Pattern.compile("\\b(agent[- ]based\\s*model|discrete\\s*event\\s*simulation|system\\s*dynamics)\\b", Pattern.CASE_INSENSITIVE),
        // Materials / Chemistry
        Pattern.compile("\\b(spin[- ]orbit\\s*coupling|SOC|Rashba|Dresselhaus|spintronics|valleytronics)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(photoluminescence|Raman\\s*spectroscopy|XRD|SEM|TEM|AFM|STM)\\b", Pattern.CASE_INSENSITIVE)
    );

    // ── Domain Patterns ──────────────────────────────────────────────────
    private static final List<Pattern> DOMAIN_PATTERNS = List.of(
        Pattern.compile("\\b(medical\\s*imaging|clinical\\s*imaging|radiology|pathology)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(remote\\s*sensing|satellite\\s*imagery|earth\\s*observation|geospatial)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(natural\\s*language|text\\s*mining|computational\\s*linguistics)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(computer\\s*vision|visual\\s*recognition|image\\s*understanding)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(quantum\\s*materials|condensed\\s*matter|solid\\s*state\\s*physics)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(robotics|autonomous\\s*systems|motion\\s*planning|robot\\s*manipulation)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(drug\\s*discovery|pharmaceutical|pharmacology|drug\\s*design)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(climate\\s*science|climate\\s*change|atmospheric|meteorology)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(agriculture|crop|precision\\s*farming|soil\\s*science|agronomy)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(cybersecurity|network\\s*security|intrusion\\s*detection|malware)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(bioinformatics|computational\\s*biology|systems\\s*biology)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(renewable\\s*energy|solar\\s*cell|photovoltaic|wind\\s*energy|battery)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(autonomous\\s*driving|self[- ]driving|ADAS|vehicle\\s*detection)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(recommender\\s*system|recommendation|collaborative\\s*filtering)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(speech\\s*recognition|speech\\s*synthesis|voice\\s*assistant|ASR|TTS)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(2D\\s*materials|transition\\s*metal\\s*dichalcogenide|TMDC|Janus|MoS2|WS2|MoSe2)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(topological\\s*insulator|Weyl\\s*semimetal|Dirac\\s*material)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(healthcare|electronic\\s*health|clinical\\s*decision|patient\\s*outcome)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(education|e-learning|educational\\s*technology|learning\\s*analytics)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(finance|fintech|stock\\s*market|portfolio|algorithmic\\s*trading)\\b", Pattern.CASE_INSENSITIVE)
    );

    // ── Task Patterns ────────────────────────────────────────────────────
    private static final List<Pattern> TASK_PATTERNS = List.of(
        Pattern.compile("\\b(classification|categorization|recognition)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(segmentation|instance\\s*segmentation|semantic\\s*segmentation|panoptic)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(object\\s*detection|face\\s*detection|anomaly\\s*detection|fault\\s*detection)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(prediction|forecasting|time\\s*series\\s*prediction|regression)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(optimization|minimization|maximization|objective\\s*function)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(generation|synthesis|data\\s*augmentation|image\\s*generation)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(translation|machine\\s*translation|neural\\s*machine\\s*translation)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(summarization|text\\s*summarization|abstractive|extractive)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(clustering|community\\s*detection|grouping|partitioning)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(question\\s*answering|QA|reading\\s*comprehension)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(reconstruction|3D\\s*reconstruction|image\\s*reconstruction|tomography)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(denoising|noise\\s*reduction|signal\\s*processing|filtering)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(super[- ]resolution|upscaling|enhancement|image\\s*restoration)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(tracking|visual\\s*tracking|motion\\s*tracking|trajectory)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(transfer\\s*learning|domain\\s*adaptation|few[- ]shot|zero[- ]shot)\\b", Pattern.CASE_INSENSITIVE)
    );

    // ── Dataset Names ────────────────────────────────────────────────────
    private static final Set<String> KNOWN_DATASETS = Set.of(
        "imagenet", "coco", "cifar-10", "cifar-100", "mnist", "fashion-mnist",
        "pascal voc", "cityscapes", "kitti", "lfw", "celeba",
        "squad", "glue", "superglue", "wikitext", "imdb", "sst",
        "pubmed", "mimic", "chestx-ray", "isic",
        "materials project", "aflow", "oqmd", "jarvis",
        "openstreetmap", "sentinel", "landsat", "modis",
        "librispeech", "voxceleb", "commonvoice",
        "movielens", "amazon reviews", "yelp",
        "shapenet", "modelnet", "scannet",
        "flickr30k", "visual genome", "vqa",
        "eurosat", "ucmerced", "aid",
        "drugbank", "chembl", "zinc",
        "cora", "citeseer", "pubmed graph", "ogb"
    );

    // ── Algorithm Names ──────────────────────────────────────────────────
    private static final List<Pattern> ALGORITHM_PATTERNS = List.of(
        // Optimizers
        Pattern.compile("\\b(Adam|AdamW|SGD|RMSprop|Adagrad|LBFGS)\\b"),
        // Architectures
        Pattern.compile("\\b(ResNet|VGG|AlexNet|Inception|DenseNet|EfficientNet|MobileNet)\\b"),
        Pattern.compile("\\b(U-Net|UNet|YOLO|SSD|Faster\\s*R-CNN|Mask\\s*R-CNN|DETR)\\b"),
        Pattern.compile("\\b(BERT|GPT|T5|RoBERTa|XLNet|ALBERT|DistilBERT|LLaMA|Gemini)\\b"),
        Pattern.compile("\\b(ViT|Vision\\s*Transformer|Swin\\s*Transformer|DeiT|BEiT|CLIP)\\b"),
        Pattern.compile("\\b(DBSCAN|K-means|k-NN|Random\\s*Forest|XGBoost|LightGBM|CatBoost)\\b"),
        Pattern.compile("\\b(GCN|GAT|GraphSAGE|GIN|ChebNet)\\b"),
        Pattern.compile("\\b(Dijkstra|A\\*|BFS|DFS|Floyd[- ]Warshall)\\b"),
        Pattern.compile("\\b(PCA|SVD|t-SNE|UMAP|ICA)\\b"),
        Pattern.compile("\\b(SVM|support\\s*vector\\s*machine|logistic\\s*regression|naive\\s*Bayes)\\b"),
        // Physics-specific
        Pattern.compile("\\b(VASP|Quantum\\s*ESPRESSO|ABINIT|Gaussian|LAMMPS|GROMACS)\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Extract all entities from the given text (typically title + abstract).
     *
     * @param text combined title and abstract text
     * @return map of entity type to set of extracted entity values (lowercased)
     */
    public Map<EntityType, Set<String>> extractEntities(String text) {
        Map<EntityType, Set<String>> result = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.values()) {
            result.put(type, new LinkedHashSet<>());
        }

        if (text == null || text.isBlank()) {
            return result;
        }

        String lowerText = text.toLowerCase();

        // Methods
        for (Pattern p : METHOD_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                result.get(EntityType.METHOD).add(normalizeEntity(m.group(1)));
            }
        }

        // Domains
        for (Pattern p : DOMAIN_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                result.get(EntityType.DOMAIN).add(normalizeEntity(m.group(1)));
            }
        }

        // Tasks
        for (Pattern p : TASK_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                result.get(EntityType.TASK).add(normalizeEntity(m.group(1)));
            }
        }

        // Datasets — simple substring matching
        for (String dataset : KNOWN_DATASETS) {
            if (lowerText.contains(dataset)) {
                result.get(EntityType.DATASET).add(dataset);
            }
        }

        // Algorithms
        for (Pattern p : ALGORITHM_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                result.get(EntityType.ALGORITHM).add(normalizeEntity(m.group(1)));
            }
        }

        return result;
    }

    /**
     * Extract entities from a paper's title and abstract.
     *
     * @param title    the paper title
     * @param abstract_ the paper abstract (nullable)
     * @return map of entity type to set of extracted entity values
     */
    public Map<EntityType, Set<String>> extractFromPaper(String title, String abstract_) {
        StringBuilder combined = new StringBuilder();
        if (title != null) combined.append(title).append(". ");
        if (abstract_ != null) combined.append(abstract_);
        return extractEntities(combined.toString());
    }

    /** Normalize entity text: trim, lowercase, collapse whitespace */
    private String normalizeEntity(String entity) {
        return entity.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
