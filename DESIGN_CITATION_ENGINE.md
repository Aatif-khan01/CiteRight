# Citation Engine Maturity - Design Document

## 1. Understanding Summary
*   **What is being built**: A mature citation and metadata engine featuring native CSL (Citation Style Language) integration, robust DOI resolution, BibTeX/RIS parsing, and LaTeX workflow support (e.g., `.bib` syncing).
*   **Why it exists**: To elevate CiteRight to Zotero/Mendeley parity, addressing academic users' strict demands for flawless citation accuracy and edge-case handling.
*   **Who it is for**: Academic researchers, students, and professionals who rely heavily on publisher-specific styles and LaTeX environments.
*   **Key Constraints**: Must be **offline-first** (CSL processing happens locally, not via a web API) and **highly scalable** (DOI lookups and bulk imports must be fully asynchronous so the UI never freezes, even with 10,000+ papers).
*   **Explicit Non-goals**: We will *not* build our own string-formatters anymore, nor will we rely on cloud APIs to format citations on the fly.

## 2. Assumptions
*   We will use an existing Java-based CSL processor (`citeproc-java`) to handle the complex styling rules locally.
*   DOI resolution will act as an enhancement layer. If the user is offline, CiteRight will gracefully fall back to local PDF metadata extraction or manual entry.
*   "LaTeX workflow support" implies that CiteRight can automatically maintain/sync a `.bib` file on the user's disk alongside their SQLite database.

## 3. Decision Log
1.  **Architecture Style**: Hybrid CSL & Async Sync Pipeline
    *   *Alternatives considered*: Full Offline Monolith (bundling all 10,000 styles, generating `.bib` on demand), Headless Citation Server (microservice).
    *   *Why chosen*: Best balance of small binary size while maintaining full offline capabilities and magical background `.bib` syncing.
2.  **Metadata Ingestion**: Asynchronous Enrichment
    *   *Alternatives considered*: Blocking UI during import.
    *   *Why chosen*: Allows handling massive libraries (10k+ papers) without freezing the UI.
3.  **LaTeX Integration**: Background Debounced File Sync
    *   *Alternatives considered*: Manual "Export to BibTeX" button.
    *   *Why chosen*: Reduces friction in the academic writing flow. A debounced timer ensures we don't thrash the disk during bulk imports.

## 4. Final Design

### Metadata Enrichment & Ingestion
*   **Component**: `MetadataEnrichmentService`
*   **Data Flow**: When a PDF is added, local extraction happens first. If a DOI is found, `MetadataEnrichmentService` spins up a background thread, calls CrossRef API, updates SQLite, and safely updates the UI via `Platform.runLater()`.
*   **Error Handling**: Graceful fallback to local metadata if offline or rate-limited. Prioritizes published DOIs over pre-prints if conflicts arise.

### CSL Engine & Formatting
*   **Component**: `CitationStyleManager` (powered by `citeproc-java`)
*   **Data Flow**: Bundles 10 core styles. If a niche style is requested, it downloads the `.csl` XML from the official GitHub repository and caches it in `~/.citeright/styles/` for permanent offline use.
*   **Error Handling**: Corrupted XML downloads are detected, deleted, and retried.

### LaTeX Workflow
*   **Component**: `BibSyncService`
*   **Data Flow**: A low-priority thread watches for database changes. After a 3-second debounce (inactivity period), it exports the library to `~/.citeright/library.bib` using `jbibtex` to ensure strict parsing.
*   **Error Handling**: Special characters (like `&` or `%`) are safely escaped by `jbibtex` to prevent breaking the LaTeX compiler.
