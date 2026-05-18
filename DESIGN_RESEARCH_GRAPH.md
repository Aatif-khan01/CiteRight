# Architecture: Multi-Scale Research Cognition Interface

## 1. Vision & Purpose
To transition CiteRight from a "storage bin" into an "externalized scholarly cognition environment." The graph is not the database; it is a lens over the database. The system prevents cognitive overload ("hairballs") by enforcing progressive disclosure across three distinct layers of interaction.

## 2. The Intelligence Hierarchy
To ensure offline-first scalability, privacy, and speed, the system enforces a strict intelligence pipeline:

1. **Local Intelligence (The Baseline):** Fast algorithms (TF-IDF, TextRank, keyword overlap, co-authorship) establish baseline clusters and semantic similarities automatically and instantly.
2. **AI Selectively (The Reasoning Layer):** High-cost LLM calls are reserved for high-value reasoning (e.g., determining if two highly similar papers *support* or *contradict* each other) or user-initiated deep dives.
3. **User Confirmation (The Ground Truth):** The user curates AI suggestions or manually draws specific edges. User assertions override all automated inferences and become permanent database records.

## 3. The Multi-Scale Navigation Paradigm

### Layer 1: Macro (Topic Landscape)
* **Purpose:** Global navigation, exploration, and landscape discovery.
* **Entry Point:** The default homepage of the graph system.
* **Rendering Strategy:** Renders conceptual "islands" or topic clusters (e.g., "Machine Learning", "Quantum Physics") rather than individual papers.
* **Generation:** Driven entirely by Local Intelligence (embeddings, keyword overlap, metadata communities).

### Layer 2: Meso (Ego-Centric Reasoning)
* **Purpose:** Focused reasoning, argument tracing, and relationship analysis.
* **Entry Point:** Triggered by selecting a specific paper (either from the Macro view or the traditional library table).
* **Rendering Strategy:** Renders a 1-hop (default) or 2-hop (optional) neighborhood centered on the target paper. Shows explicit reasoning edges (`supports`, `contradicts`, `extends`, `shares methodology`).
* **Generation:** Powered by the Selective AI layer to infer qualitative relationships between highly connected nodes.

### Layer 3: Micro (User Research Workspace)
* **Purpose:** Synthesis, thesis planning, and literature review drafting.
* **Entry Point:** A blank, persistent canvas (or "whiteboard" mode).
* **Rendering Strategy:** Renders only what the user explicitly places on it.
* **Features:** Allows users to pin papers, manually draw and label relationships, group concepts, and add sticky notes. It serves as the ultimate "thinking environment."

## 4. Non-Functional Requirements & Constraints
* **Scalability:** By enforcing progressive disclosure (only rendering clusters at the macro level and 1-hop neighborhoods at the meso level), the UI can theoretically scale to 100k+ papers without frame drops.
* **Offline Capability:** The Macro and Micro layers must function 100% offline. The Meso layer can function offline using basic metadata edges, with AI reasoning progressively enhancing the edges when a connection is available.
* **Progressive Loading:** The graph must never be "fully loaded." Node data and edges are fetched lazily as the user zooms or expands neighborhoods.

## 5. Future Extensibility
This foundational architecture paves the way for advanced semantic querying, such as:
* **Debate Mapping:** Finding high-density clusters of `contradicts` edges.
* **Methodology Networks:** Filtering the Macro view by specific experimental approaches.
* **Timeline Evolution:** Adding temporal sliders to watch Macro clusters split and merge over decades.
