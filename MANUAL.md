# CiteRight 1.0 - Comprehensive User Manual

Welcome to **CiteRight**, a modern, privacy-first reference management and reading environment designed for researchers, students, and academics. CiteRight provides powerful organizational tools, built-in PDF reading, and AI-assisted insights, all while keeping your data 100% local to your machine.

This manual provides a detailed, step-by-step guide to every feature available in CiteRight.

---

## Table of Contents
1. [Installation & First Launch](#1-installation--first-launch)
2. [Interface & Layout Overview](#2-interface--layout-overview)
3. [Library Management (Step-by-Step)](#3-library-management-step-by-step)
4. [PDF Viewer & Annotations](#4-pdf-viewer--annotations)
5. [Smart Search & Discovery](#5-smart-search--discovery)
6. [Chat with Your Library (AI Integration)](#6-chat-with-your-library-ai-integration)
7. [Visualizing Connections (Paper Graph)](#7-visualizing-connections-paper-graph)
8. [Generating Citations](#8-generating-citations)
9. [Data Privacy, Storage & Backups](#9-data-privacy-storage--backups)

---

## 1. Installation & First Launch

CiteRight 1.0 is distributed as a **Portable Application**, meaning it does not require a complex installation process or external dependencies like Java.

### Step-by-Step Installation:
1. **Download the Release**: Obtain the `CiteRight_Portable.zip` file.
2. **Extract the Folder**: Right-click the `.zip` file and select "Extract All..." to unzip it to a location of your choice (e.g., your Desktop or Documents folder).
3. **Launch the Application**: Open the extracted folder and double-click the `CiteRight.exe` file.
4. **First Launch Initialization**: On its first run, CiteRight will automatically create a hidden directory in your user folder (`C:\Users\YourUsername\.citeright\`) to store your local database and PDF files. 

---

## 2. Interface & Layout Overview

When you launch CiteRight, you are greeted with a three-pane layout designed to maximize productivity:

- **Top Navigation Bar (Command Palette & Tabs)**: 
  - Switch between the core modules: **Library**, **Search/Discovery**, **Paper Graph**, and **AI Chat**.
  - A global filter bar allows you to quickly filter visible items by text.
- **Left Sidebar (Navigation Pane)**: 
  - Manage your collections. By default, you have **All Papers**, **Favorites**, and **Unsorted**.
  - The **"+ Add Entry"** button is permanently located at the bottom of the sidebar.
- **Center Workspace**: 
  - Displays the active module. In the Library tab, this shows a tabular view of all your papers.
- **Right Panel (Detail Panel)**: 
  - When a paper is selected in the center workspace, this panel populates with rich metadata (Title, Authors, Abstract, Year).
  - It also includes reading statistics, 1-click citation generators, and the "Find Similar Papers" tool.

---

## 3. Library Management (Step-by-Step)

### How to Add a Paper
1. Click the **"+ Add Entry"** button at the bottom of the left sidebar.
2. A dialog box will appear with two options:
   - **Manual Entry**: Fill in the Title, Authors, Year, Abstract, and Venue manually. Click "Save".
   - **PDF Import**: Click the "Import PDF" button or drag a PDF into the application. 
3. **Auto-Extraction**: When you import a PDF, CiteRight uses built-in parsers to automatically attempt to extract the title and metadata from the document's contents.

### Organizing with Smart Collections
- **Favorites**: To bookmark a paper, select it in the center workspace, then look at the Right Detail Panel. Click the Heart icon (🤍) to toggle it to solid red (❤️). It will now appear in your "Favorites" collection in the sidebar.
- **Unsorted**: Any paper imported that hasn't been specifically categorized will remain in the Unsorted folder, allowing you to easily review new additions.

### Editing Paper Details
1. Select a paper from the center list.
2. The Right Detail Panel will update to show its information. 
3. *Note*: In version 1.0, core metadata is locked upon import to maintain database integrity, but you can interact with the paper via the AI and Annotation tools.

---

## 4. PDF Viewer & Annotations

CiteRight features a native, built-in PDF reader (powered by Apache PDFBox) so you never have to leave the application.

### Opening a PDF
1. Select a paper in your library.
2. In the Right Detail Panel, click the **"Read PDF"** button. The center workspace will transition from the library table to the PDF document.

### Using the Annotation Toolbar
At the top of the PDF viewer, you have an interactive toolbar:
- **Highlight (🖊️)**: Click this, then click and drag over text in the PDF to highlight it in yellow.
- **Draw (🖌️)**: Allows freehand drawing over the document.
- **Add Note (📝)**: Click this, then click anywhere on the page to drop a text note. A popup will ask you to type your note.

### The Annotations Sidebar
- All notes and highlights appear in a sidebar on the right side of the PDF viewer.
- **Hiding the Sidebar**: To maximize reading space, click the **"Hide"** button at the top of the annotations sidebar, or toggle the `📝` icon in the main application navigation bar. This will collapse the annotations panel. Click it again to restore it.

---

## 5. Smart Search & Discovery

The **Discovery/Search Tab** is designed to find exact claims, sentences, and related research across your entire library.

### Performing a Search
1. Click the **"Search"** tab in the top navigation bar.
2. Type a concept or specific question (e.g., *"How does AI affect healthcare?"*) into the search bar and press Enter.
3. CiteRight will search your local library and rank papers based on relevance, displaying them as result cards.

### AI Evidence Extraction
To help you find exact answers without manually reading every paper, you can ask the AI to extract evidence:
1. Perform a search as described above.
2. Underneath a specific search result card, locate the **"✨ Extract Evidence (AI)"** button.
3. Click it. The button will change to "⏳ Extracting...".
4. CiteRight will securely send the text of that specific paper to the AI and ask it to find evidence answering your search query.
5. The extracted sentence or paragraph will appear directly inside the result card. 
*Note: This feature requires an AI API Key to be configured (see Section 6).*

### Find Similar Papers
1. Select any paper in the standard Library view.
2. In the Right Detail Panel, scroll to the bottom.
3. Click **"Find Similar Papers"**. CiteRight will analyze the abstract and metadata to suggest related research from your library.

---

## 6. Chat with Your Library (AI Integration)

CiteRight integrates directly with Google's Gemini AI, allowing you to ask complex questions and get answers grounded entirely in your own research.

### Step 1: Configuring Your API Key
1. Click the **"AI Chat"** tab in the top navigation bar.
2. Click the **Settings (⚙️)** icon in the chat window.
3. Select **Gemini** as your provider.
4. Paste your free Gemini API Key (which you can obtain from Google AI Studio).
5. Click Save.

### Step 2: Using the Chat
- Type your question into the chat box at the bottom (e.g., *"Summarize the main findings of my recent papers on machine learning"*).
- The AI will process your request and return an answer. 
- **Important Quota Note**: The free tier of Gemini allows **15 requests per minute** and **1,500 requests per day**. If you exceed this, CiteRight will display an error message. Wait a minute and try again.

---

## 7. Visualizing Connections (Paper Graph)

The **Paper Graph** tab provides a beautiful, node-based visual map of how your research connects.

### Using the Graph
1. Click the **"Paper Graph"** tab in the top navigation bar.
2. You will see a web of nodes (circles representing papers) and edges (lines representing connections).
3. **Interacting**: 
   - Click and drag any node to move it around and organize your visual map.
   - Click a node to immediately update the Right Detail Panel with that paper's information.
- Connections are automatically generated based on shared authors, similar topics, or overlapping metadata.

---

## 8. Generating Citations

CiteRight makes referencing your papers effortless with 1-click citation generators.

1. Select a paper in the Library view, OR view a paper in the Search results.
2. Look for the row of buttons labeled **"Cite as:"** followed by **[APA] [MLA] [IEEE] [Harvard]**.
3. Click your desired format.
4. A small notification toast will appear at the bottom of the screen saying "Citation copied to clipboard!".
5. You can now paste (Ctrl+V) the perfectly formatted citation into your word processor.

---

## 9. Data Privacy, Storage & Backups

CiteRight is fundamentally built on a **Privacy-First** architecture.

### Zero Cloud Dependency
- There are no telemetry servers, no user accounts, and no mandatory cloud syncs. Everything happens on your PC.

### Where is my data stored?
All of your data is stored locally on your hard drive in a hidden folder in your user directory:
- **Windows**: `C:\Users\YourUsername\.citeright\`
- Inside this folder, you will find:
  - `library.db`: The SQLite database containing all your text data, metadata, and annotations.
  - `pdfs\`: A folder containing all the actual PDF files you have imported.

### How to Backup or Move to a New PC
Because CiteRight is fully portable, backing up is incredibly simple:
1. Close CiteRight.
2. Navigate to `C:\Users\YourUsername\`.
3. Copy the entire `.citeright` folder to a USB drive or cloud backup service.
4. To restore on a new PC, simply paste that folder into the new PC's user directory and launch `CiteRight.exe`. Your entire library, PDFs, and annotations will be exactly as you left them.
