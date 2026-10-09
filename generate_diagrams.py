import matplotlib.pyplot as plt
import matplotlib.patches as patches

def draw_rounded_rect(ax, x, y, width, height, label, subtext="", box_color="#1E293B", text_color="#F8FAFC", edge_color="#38BDF8", fontsize=10):
    rect = patches.FancyBboxPatch((x, y), width, height,
                                 boxstyle="round,pad=0.03,rounding_size=0.15",
                                 linewidth=1.5, edgecolor=edge_color, facecolor=box_color)
    ax.add_patch(rect)
    
    if subtext:
        ax.text(x + width/2, y + height*0.6, label, fontsize=fontsize, fontweight='bold', color=text_color, ha='center', va='center')
        ax.text(x + width/2, y + height*0.25, subtext, fontsize=fontsize-2.5, color='#94A3B8', ha='center', va='center', style='italic')
    else:
        ax.text(x + width/2, y + height/2, label, fontsize=fontsize, fontweight='bold', color=text_color, ha='center', va='center')

def draw_arrow(ax, x1, y1, x2, y2, label="", color="#64748B"):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="->", color=color, lw=2, mutation_scale=15))
    if label:
        mid_x = (x1 + x2) / 2
        mid_y = (y1 + y2) / 2
        ax.text(mid_x, mid_y + 0.15, label, fontsize=8, color="#CBD5E1", ha='center', va='bottom', fontweight='semibold',
                bbox=dict(boxstyle="round,pad=0.2", facecolor="#0F172A", edgecolor="none", alpha=0.8))

# ==========================================
# DIAGRAM 1: SYSTEM ARCHITECTURE PIPELINE
# ==========================================
def create_architecture_diagram():
    fig, ax = plt.subplots(figsize=(14, 9), dpi=300)
    fig.patch.set_facecolor('#0F172A')
    ax.set_facecolor('#0F172A')
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 9)
    ax.axis('off')

    # Title
    ax.text(7, 8.5, "CiteRight - High-Level System Architecture Pipeline", 
            fontsize=16, fontweight='bold', color='#F8FAFC', ha='center')
    ax.text(7, 8.15, "Multi-Layered Modular Architecture with Offline-First Local AI & Async Ingestion", 
            fontsize=10, color='#38BDF8', ha='center', style='italic')

    # Layer 1: Presentation (JavaFX UI)
    ax.text(0.5, 7.3, "PRESENTATION LAYER (JavaFX Desktop UI)", fontsize=9, fontweight='bold', color='#A855F7')
    draw_rounded_rect(ax, 0.5, 6.2, 2.8, 0.9, "Library Table View", "ReferenceTableView & FilterBar", "#1E1B4B", "#F3E8FF", "#A855F7")
    draw_rounded_rect(ax, 3.7, 6.2, 2.8, 0.9, "PDF Reader Pane", "PdfViewerPane & PDFBox", "#1E1B4B", "#F3E8FF", "#A855F7")
    draw_rounded_rect(ax, 6.9, 6.2, 2.8, 0.9, "Research Graph", "Macro / Meso / Micro Views", "#1E1B4B", "#F3E8FF", "#A855F7")
    draw_rounded_rect(ax, 10.1, 6.2, 3.4, 0.9, "AI Chat & Prompt", "LocalSemanticAIPrompt & Chat", "#1E1B4B", "#F3E8FF", "#A855F7")

    # Layer 2: Application Controller / Orchestration
    ax.text(0.5, 5.3, "APPLICATION & SERVICE ORCHESTRATION LAYER", fontsize=9, fontweight='bold', color='#38BDF8')
    draw_rounded_rect(ax, 0.5, 4.2, 3.8, 0.9, "LibraryService & MainLayout", "State Management & Event Bus", "#0F172A", "#E0F2FE", "#38BDF8")
    draw_rounded_rect(ax, 4.7, 4.2, 4.2, 0.9, "MetadataEnrichmentService", "Async Thread Pool & Event Loop", "#0F172A", "#E0F2FE", "#38BDF8")
    draw_rounded_rect(ax, 9.3, 4.2, 4.2, 0.9, "CitationStyleManager", "citeproc-java Engine & CSL", "#0F172A", "#E0F2FE", "#38BDF8")

    # Layer 3: Intelligence & Analytics Engine
    ax.text(0.5, 3.3, "INTELLIGENCE & NLP ENGINE LAYER", fontsize=9, fontweight='bold', color='#34D399')
    draw_rounded_rect(ax, 0.5, 2.2, 3.8, 0.9, "SemanticLibrarySearch", "BGE-M3 Neural & TF-IDF Cosine", "#064E3B", "#ECFDF5", "#34D399")
    draw_rounded_rect(ax, 4.7, 2.2, 4.2, 0.9, "ClusteringEngine & GapAnalyzer", "Ward's Hierarchical Clustering", "#064E3B", "#ECFDF5", "#34D399")
    draw_rounded_rect(ax, 9.3, 2.2, 4.2, 0.9, "BibSyncService", "Debounced LaTeX Sync Engine", "#064E3B", "#ECFDF5", "#34D399")

    # Layer 4: Storage & Data Access
    ax.text(0.5, 1.3, "STORAGE & EXTERNAL DATA LAYER", fontsize=9, fontweight='bold', color='#FBBF24')
    draw_rounded_rect(ax, 0.5, 0.3, 3.8, 0.8, "SQLite Database", "citeright.db & PaperDAO", "#451A03", "#FEF3C7", "#FBBF24")
    draw_rounded_rect(ax, 4.7, 0.3, 4.2, 0.8, "Local PDF & Vector Cache", "~/.citeright/ & BGE-M3 Cache", "#451A03", "#FEF3C7", "#FBBF24")
    draw_rounded_rect(ax, 9.3, 0.3, 4.2, 0.8, "External Web APIs", "CrossRef & Semantic Scholar", "#451A03", "#FEF3C7", "#FBBF24")

    # Connectors
    for x in [1.9, 5.1, 8.3, 11.8]:
        draw_arrow(ax, x, 6.2, x, 5.1)
    for x in [2.4, 6.8, 11.4]:
        draw_arrow(ax, x, 4.2, x, 3.1)
    for x in [2.4, 6.8, 11.4]:
        draw_arrow(ax, x, 2.2, x, 1.1)

    plt.tight_layout()
    plt.savefig('pipeline_architecture.png', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()

# ==========================================
# DIAGRAM 2: METADATA ENRICHMENT PIPELINE
# ==========================================
def create_metadata_pipeline_diagram():
    fig, ax = plt.subplots(figsize=(13, 6), dpi=300)
    fig.patch.set_facecolor('#0F172A')
    ax.set_facecolor('#0F172A')
    ax.set_xlim(0, 13)
    ax.set_ylim(0, 6)
    ax.axis('off')

    # Title
    ax.text(6.5, 5.5, "CiteRight - Asynchronous Metadata Enrichment Pipeline", 
            fontsize=15, fontweight='bold', color='#F8FAFC', ha='center')

    # Flow Steps
    draw_rounded_rect(ax, 0.4, 3.2, 2.2, 1.2, "1. PDF Ingestion", "Drag-Drop / File Upload", "#1E293B", "#F8FAFC", "#60A5FA")
    draw_rounded_rect(ax, 3.0, 3.2, 2.2, 1.2, "2. Local Extraction", "Apache PDFBox Parser\nDOI Regex Match", "#1E293B", "#F8FAFC", "#60A5FA")
    draw_rounded_rect(ax, 5.6, 3.2, 2.4, 1.2, "3. Enrichment Thread", "MetadataEnrichment\nAsync Thread Pool", "#1E293B", "#F8FAFC", "#F59E0B")
    draw_rounded_rect(ax, 8.4, 3.2, 2.2, 1.2, "4. REST API Query", "CrossRef / Semantic\nScholar REST Client", "#1E293B", "#F8FAFC", "#10B981")
    draw_rounded_rect(ax, 11.0, 3.2, 1.6, 1.2, "5. SQLite & UI", "Merge Metadata\nPlatform.runLater()", "#1E293B", "#F8FAFC", "#A855F7")

    # Arrows
    draw_arrow(ax, 2.6, 3.8, 3.0, 3.8)
    draw_arrow(ax, 5.2, 3.8, 5.6, 3.8)
    draw_arrow(ax, 8.0, 3.8, 8.4, 3.8)
    draw_arrow(ax, 10.6, 3.8, 11.0, 3.8)

    # Sub-flow for BibTeX Syncing
    draw_rounded_rect(ax, 8.4, 0.8, 3.6, 1.1, "Debounced BibTeX Sync Engine", "3s Inactivity -> Updates ~/.citeright/library.bib", "#1E1B4B", "#ECFDF5", "#34D399")
    draw_arrow(ax, 11.8, 3.2, 10.2, 1.9, "Database Update Event")

    plt.tight_layout()
    plt.savefig('metadata_pipeline.png', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()

# ==========================================
# DIAGRAM 3: SEMANTIC SEARCH & RAG PIPELINE
# ==========================================
def create_semantic_search_diagram():
    fig, ax = plt.subplots(figsize=(13, 6.5), dpi=300)
    fig.patch.set_facecolor('#0F172A')
    ax.set_facecolor('#0F172A')
    ax.set_xlim(0, 13)
    ax.set_ylim(0, 6.5)
    ax.axis('off')

    # Title
    ax.text(6.5, 6.0, "CiteRight - Local Semantic Search & RAG AI Processing Pipeline", 
            fontsize=15, fontweight='bold', color='#F8FAFC', ha='center')

    draw_rounded_rect(ax, 0.4, 3.5, 2.2, 1.3, "User Research Query", "\"What is transformers impact\non vision models?\"", "#1E1B4B", "#F3E8FF", "#A855F7")
    draw_rounded_rect(ax, 3.0, 3.5, 2.3, 1.3, "Text Preprocessor", "Tokenization, Stopwords,\nStemming / Normalization", "#0F172A", "#E0F2FE", "#38BDF8")

    # Dual track box
    draw_rounded_rect(ax, 5.7, 4.4, 3.4, 1.1, "Track A: BGE-M3 Embedding", "Dense Vector Cosine Similarity", "#064E3B", "#ECFDF5", "#34D399")
    draw_rounded_rect(ax, 5.7, 2.6, 3.4, 1.1, "Track B: TF-IDF Fallback", "Sparse Vector Space Scoring", "#064E3B", "#ECFDF5", "#34D399")

    draw_rounded_rect(ax, 9.5, 3.5, 1.6, 1.3, "Ranked Results", "Top-K Relevant\nPaper Context", "#451A03", "#FEF3C7", "#FBBF24")
    draw_rounded_rect(ax, 11.4, 3.5, 1.3, 1.3, "Local LLM", "Ollama / ONNX\nSynthesis", "#1E1B4B", "#F3E8FF", "#EC4899")

    # Arrows
    draw_arrow(ax, 2.6, 4.15, 3.0, 4.15)
    draw_arrow(ax, 5.3, 4.3, 5.7, 4.95)
    draw_arrow(ax, 5.3, 4.0, 5.7, 3.15)
    draw_arrow(ax, 9.1, 4.95, 9.5, 4.3)
    draw_arrow(ax, 9.1, 3.15, 9.5, 4.0)
    draw_arrow(ax, 11.1, 4.15, 11.4, 4.15)

    plt.tight_layout()
    plt.savefig('semantic_search_pipeline.png', facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close()

if __name__ == '__main__':
    create_architecture_diagram()
    create_metadata_pipeline_diagram()
    create_semantic_search_diagram()
    print("All 3 pipeline diagrams generated successfully!")
