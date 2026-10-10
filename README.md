# 📚 CiteRight

> **Self-Contained Autonomous Desktop Research Intelligence Tool**

**CiteRight** is a self-contained, autonomous desktop research intelligence tool built with a local-first approach. It utilizes a quantized **BGE-M3 multilingual neural embedding** engine to execute complex semantic searches and similarity calculations directly on consumer CPUs without requiring cloud dependencies. 

CiteRight merges your paper collection into an interactive, multi-relational paper graph that captures critical relationships, including research support, methodological extensions, theoretical contradictions, and shared methodologies, complete with confidence scoring and provenance tracking. It also offers optional **Google Gemini** integration to execute deep natural language queries grounded entirely in your local documents.

Built around a unified, high-performance data layer optimized for local text processing across multiple analysis modules, CiteRight features a hybrid multi-signal ranking system, rigorous evaluation protocols, and a modular engine for discovering research gaps in literature.

---

## ✨ What is CiteRight?

When conducting research, managing dozens or hundreds of PDF files across folders and clunky tools quickly becomes overwhelming. **CiteRight** solves this by providing:

* **Centralized Library:** Keep all your research papers, journals, conference articles, and books organized in one place.
* **Built-in PDF Studio:** Read papers, highlight text, draw annotations, and add notes without needing external PDF readers.
* **Instant Hybrid Search:** Find exact claims, keywords, and topics across your entire document collection in milliseconds.
* **AI Research Assistant:** Ask questions grounded in your own papers using a Google Gemini API key (cloud) or a local Ollama model.
* **Visual Paper Graph:** Explore connections, topic clusters, and relationships between papers on an interactive visual canvas.
* **1-Click Citations:** Copy properly formatted citations in APA, MLA, IEEE, and Harvard styles instantly to your clipboard.
* **Data locality:** No mandatory accounts and no tracking. Your database, PDFs and embeddings are stored on your machine; optional AI features send limited data to Gemini (see section 8).

---

## 📥 Download & Installation

CiteRight comes bundled with its own self-contained runtime. **You do not need to install Java or any third-party software.**

### Windows Installer (.exe)
1. Download **`CiteRight-1.0.0.exe`** from this repository (or from the Releases page).
2. Double-click the installer and follow the setup wizard.
3. Choose your installation folder and create a Desktop shortcut.
4. Launch **CiteRight** from your Start Menu or Desktop!

---

## 📖 User Manual & Guide

### 1. Interface & Layout Overview

When you open CiteRight, the interface is organized into four main sections designed for maximum productivity:

* **Top Navigation Bar:** Switch between core tabs (**Library**, **Search & Discovery**, **Paper Graph**, and **AI Chat**).
* **Left Navigation Sidebar:** Access your paper collections (**All Papers**, **Favorites**, and **Unsorted**) and find the **"+ Add Entry"** button.
* **Center Workspace:** Your primary workspace. In the Library view, it displays your paper table; in the PDF view, it renders your active document.
* **Right Detail Panel:** Displays full paper metadata (Title, Authors, Abstract, Year, Venue), reading stats, 1-click citation buttons, and the "Find Similar Papers" tool.

---

### 2. Adding & Organizing Papers

#### Adding a Paper
1. Click the **"+ Add Entry"** button at the bottom of the left sidebar.
2. Choose one of two options:
   * **Import PDF:** Click import or drag & drop any PDF file. CiteRight automatically extracts the title, authors, and abstract.
   * **Manual Entry:** Type the Title, Authors, Year, Abstract, and Publication Venue, then click **Save**.

#### Organizing Collections
* **Favorites (❤️):** Select any paper, then click the Heart icon in the Right Detail Panel to bookmark it into your Favorites folder.
* **Unsorted:** All newly imported items appear in Unsorted until you organize them.

---

### 3. Native PDF Reader & Annotation Studio

You never need to leave CiteRight to read or annotate papers.

#### Reading a PDF
1. Select any paper from your library list.
2. In the Right Detail Panel, click the **"Read PDF"** button.
3. The center workspace will instantly display the full document.

#### Annotating
Use the interactive toolbar at the top of the PDF viewer:
* **Highlight (🖊️):** Click the highlighter, then drag over any text to highlight it.
* **Draw (🖌️):** Draw freehand annotations or diagrams directly onto pages.
* **Sticky Notes (📝):** Click the note tool and click anywhere on a page to attach a comment or summary.

#### Annotations Sidebar
All notes and highlights are listed in the right-side annotation sidebar. Click **"Hide"** or toggle the `📝` icon in the top bar to collapse it whenever you want a distraction-free, full-screen reading mode.

---

### 4. Smart Search & Evidence Discovery

#### Running a Search
1. Click the **Search** tab in the top navigation bar.
2. Enter keywords, research questions, or author names (e.g., *"neural attention mechanisms in medical imaging"*).
3. CiteRight ranks matching papers and displays rich result cards.

#### AI Evidence Extraction
1. On any search result card, click **"✨ Extract Evidence (AI)"**.
2. CiteRight scans the document's content and extracts the exact sentences answering your search query.
3. The extracted finding is displayed directly in the result card.

#### Finding Similar Papers
Select any paper in your library and click **"Find Similar Papers"** in the Right Detail Panel to find related papers in your collection based on topic and abstract similarity.

---

### 5. Chat with Your Library (AI Integration)

CiteRight lets you converse with your research library using Google Gemini AI.

#### Setup (Free Gemini API Key)
1. Click the **AI Chat** tab in the top navigation bar.
2. Click the **Settings (⚙️)** icon in the chat header.
3. Select **Gemini** and paste your free API key from [Google AI Studio](https://aistudio.google.com/).
4. Click **Save**.

#### Using AI Chat
* Ask questions such as:
  * *"Summarize the key findings across all my papers on transformer models."*
  * *"Which papers in my library discuss battery degradation?"*
  * *"Compare the methodologies used in Smith 2023 vs. Johnson 2024."*
* CiteRight retrieves relevant contexts from your local library and provides grounded, synthesized answers.

---

### 6. Visualizing Connections (Paper Graph)

The **Paper Graph** provides an interactive visual map of your research library:

1. Click the **Paper Graph** tab in the top navigation bar.
2. View papers as nodes and their semantic and citation relationships as connected edges.
3. **Drag and move nodes** to organize your thinking and explore topic clusters.
4. **Click any node** to instantly load that paper's metadata and citations into the Detail Panel.

---

### 7. Generating Citations (1-Click Copy)

Whenever you need to cite a paper in your writing:
1. Select the paper in the **Library** or **Search** view.
2. Look at the **"Cite as:"** bar in the Right Detail Panel.
3. Click your preferred format: **[APA]**, **[MLA]**, **[IEEE]**, or **[Harvard]**.
4. The formatted citation is automatically copied to your clipboard—ready to paste (`Ctrl+V`) into Word, Google Docs, or LaTeX!

---

### 8. Data Locality, Storage & Backups

CiteRight keeps your library on your computer. Some optional AI features send limited data to Google's Gemini API:

* **Stays local:** PDFs, notes, the SQLite database, BGE-M3 embeddings, semantic search, clustering, graph construction and gap discovery.
* **Relationship inference (Gemini):** paper titles, years and abstracts truncated to 500 characters, in batches of up to 10 papers. Without an API key, a local rule engine is used instead.
* **AI chat (Gemini):** your question and up to 8 retrieved excerpts from your papers. Selecting a local Ollama model keeps chat on `localhost`, but if Ollama is unavailable and a Gemini key is configured, CiteRight falls back to Gemini.
* **Extract Evidence (AI):** uses the local BGE-M3 model when it is loaded; otherwise your claim and the paper's abstract go to the configured AI provider. A missing abstract may be fetched from Semantic Scholar using the paper's DOI.
* **Unpublished work:** titles and abstracts sent to Gemini need not be public if your library contains unpublished manuscripts.
* **Not encrypted:** the local `library.db` file is not encrypted by CiteRight. Data locality is not a formal privacy guarantee.
* **Storage Location:** All data is kept in your user folder:
  ```
  C:\Users\<YourUsername>\.citeright\
  ├── library.db      (SQLite database with all metadata, notes & citations)
  └── pdfs\           (All imported PDF documents)
  ```
* **Easy Backups & Migration:** To back up or move CiteRight to a new computer, simply copy the `.citeright` folder to your new PC.

---

## 🔬 Scientific Reproducibility & Benchmark Suite

CiteRight includes an automated, self-contained empirical evaluation harness (`BenchmarkTest.java`) and complete recorded benchmark evaluation datasets supporting the empirical claims of the paper (*"CiteRight: A Local-First Framework for Hybrid Scholarly Retrieval and Research Opportunity Discovery"*):

### Benchmark Datasets (`src/test/resources/benchmark/`)
* **`retrieval_queries_25.json`**: 25 conceptual queries with ranked document IDs (top 10 retrieved documents per model), binary query relevance judgments (exactly six relevant documents per query), target known-item document IDs, evaluated Reciprocal Ranks (MRR), Precision@5, NDCG@10, and top-10 relevance vectors across lexical (TF-IDF), dense (BGE-M3), and 5-signal hybrid models.
  - *Target MRR:* Reciprocal rank of the primary known-item document ($1 / \text{rank}(D^*)$).
  - *Precision@5:* Fraction of top-5 retrieved documents relevant ($\text{rel} = 1$).
  - *NDCG@10:* Normalized Discounted Cumulative Gain over top 10 positions with logarithmic discount.
* **`relationship_pairs_50.json`**: 50 paper pairs with relationship labels assigned by the first author (single annotator), Gemini 2.5 Flash predictions, local rule predictions, and confidence scores across 4 relationship types (`SUPPORTS`, `EXTENDS`, `CONTRADICTS`, `METHODOLOGY`). Rule-based local engine achieves Macro-$F_1 = 0.72$ ($\kappa = 0.59$); Gemini achieves Macro-$F_1 = 0.85$ ($\kappa = 0.78$).
* **`gap_recommendations_20.json`**: 120 candidate gap recommendations across 4 distinct thematic seed domains (20 candidates per module for Random Baseline, Network Centrality, Topic Gap, Temporal Gap, Methodology Transfer, and Interdisciplinary Gap) with method-specific candidate descriptions, relevance annotations, and confidence values.

### Running the Benchmark Harness
The benchmark suite runs on Java 21+ with no external dependencies. **Run it from the repository root**: the data paths are relative, and the harness stops with an error if a data file is missing or incomplete.

```bash
# Compile and run directly via Java 21+
java src/test/java/com/citeright/BenchmarkTest.java
```

The harness computes items 1-3 and 5 below from the released files. Items 4 and 6 replay *recorded* values (see notes); the gap candidates (item 6) are manually constructed and manually labelled.
1. **Runtime Scalability (Table 4):** Measured brute-force cosine query latency (median of 5 trials) on synthetic 1024-d vectors for 100 to 10,000 documents. Values vary between runs; the paper reports the range over four runs on the authors' laptop, one of which is `benchmark_results.txt`. End-to-end embedding latency, graph-construction time and peak process memory are not measured and are omitted; this benchmark covers synthetic vector search only.
2. **Information Retrieval Quality (Table 1):** Dynamic MRR, P@5, and NDCG@10 from query evaluations.
3. **Statistical Uncertainty & Significance (Table 1b):** 1,000-resample percentile bootstrap 95% confidence intervals for MRR, P@5 and NDCG@10, and paired Monte Carlo sign-permutation tests on per-query reciprocal rank (10,000 resamples, seed 1). Two-sided p-values (primary): Dense vs. TF-IDF $p = 0.0041$, Hybrid vs. TF-IDF $p = 0.0041$, Hybrid vs. Dense $p = 0.4955$. One-sided p-values ($H_1: \Delta > 0$) are also printed: 0.0023, 0.0023, 0.2474. Only MRR is tested. P@5 = 1.000 for dense and hybrid is a ceiling effect. The hybrid-vs-dense difference comes from two queries (Q05, Q20).
4. **Ranking Signal Ablation (Table 2):** Leave-one-out table printed from *recorded* values; per-signal scores need the 100-paper corpus and BGE-M3 model, which are not released, so it is not recomputed.
5. **Multi-Relational Classification (Table 3):** 4x4 confusion matrix, Macro-F1, Precision, Recall, and Cohen's Kappa ($\kappa$).
6. **Research Gap Discovery (Table 5):** Precision@5 and average confidence across all 6 methods, computed from the recorded candidate relevance flags (candidates are not regenerated by the harness).

---

## 📄 License

CiteRight is distributed for research and academic use. All rights reserved.
