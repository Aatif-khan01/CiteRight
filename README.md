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
* **AI Research Assistant:** Connect your free Google Gemini API key to ask questions grounded directly in your own papers.
* **Visual Paper Graph:** Explore connections, topic clusters, and relationships between papers on an interactive visual canvas.
* **1-Click Citations:** Copy properly formatted citations in APA, MLA, IEEE, and Harvard styles instantly to your clipboard.
* **100% Privacy & Local Storage:** No mandatory accounts, no cloud sync lock-in, and zero tracking. All your database records and PDFs stay on your machine.

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

### 8. Data Privacy, Storage & Backups

CiteRight is built from the ground up to respect your privacy:

* **Zero Cloud Lock-In:** Your papers and annotations are never uploaded to third-party databases.
* **Storage Location:** All data is kept in your user folder:
  ```
  C:\Users\<YourUsername>\.citeright\
  ├── library.db      (SQLite database with all metadata, notes & citations)
  └── pdfs\           (All imported PDF documents)
  ```
* **Easy Backups & Migration:** To back up or move CiteRight to a new computer, simply copy the `.citeright` folder to your new PC.

---

## 📄 License

CiteRight is distributed for research and academic use. All rights reserved.
