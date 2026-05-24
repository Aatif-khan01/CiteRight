package com.citeright.ui;

import com.citeright.model.LibraryEntry;
import com.citeright.model.PdfOpenRequest;
import com.citeright.model.Publication;
import com.citeright.database.AnnotationDAO;
import com.citeright.service.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;

import java.io.File;
import java.util.List;

/**
 * Main layout — clean 3-panel design with welcome screen.
 */
public class MainLayout extends BorderPane {

    private final LibraryService libraryService;
    private final SearchService searchService;
    private final MetadataLookupService lookupService;
    private final PdfService pdfService;
    private final ImportExportService importExportService;
    private final KeywordExtractorService keywordExtractor;
    private LocalImportServer localImportServer;

    private SidebarPane sidebar;
    private ReferenceTableView libraryTable;
    private SearchPane searchPane;
    private DetailPanel detailPanel;
    private FilterBar filterBar;
    private CommandPalette commandPalette;
    private PaperGraphPane paperGraphPane;
    private ChatPane chatPane;
    private PdfViewerPane pdfViewerPane;
    private StatsPane statsPane;
    private AnnotationDAO annotationDAO;

    private VBox libraryView;
    private Label viewTitle;
    private ProgressIndicator loadingSpinner;
    private StackPane rootStack;
    private String currentView = "ALL_ACTIVE";
    private boolean isTrashView = false;

    // === Local Semantic AI Status Bar ===
    private HBox statusBar;
    private Label statusLabel;
    private Hyperlink statusAction1;
    private Hyperlink statusAction2;
    private com.citeright.ai.BgeM3ModelDownloader.State lastDlStateRef = com.citeright.ai.BgeM3ModelDownloader.State.IDLE;
    private double lastPercent = 0.0;
    private double lastSpeed = 0.0;
    private String lastFileLabel = "";

    public MainLayout(LibraryService libraryService, SearchService searchService) {
        this.libraryService = libraryService;
        this.searchService = searchService;
        this.lookupService = new MetadataLookupService();
        this.pdfService = new PdfService();
        this.importExportService = new ImportExportService();
        this.keywordExtractor = new KeywordExtractorService();
        buildUI();
        startLocalServer();

        // Show welcome or library depending on paper count
        if (libraryService.getLibraryCount() > 0) {
            loadLibraryView("ALL_ACTIVE");
        } else {
            showWelcome();
        }
    }

    private void buildUI() {
        setStyle("-fx-background-color: #f4f4f8;");

        sidebar = new SidebarPane(libraryService);
        libraryTable = new ReferenceTableView(libraryService);
        searchPane = new SearchPane(searchService, libraryService, entry -> {
            // Open → from search: show detail panel in search-result mode
            detailPanel.showSearchResult(entry);
            setRight(detailPanel);
            setCenter(searchPane);
        });
        searchPane.setOnLibraryUpdated(() -> sidebar.refreshStats());
        detailPanel = new DetailPanel(libraryService);

        // When user deletes from detail panel, refresh the current library view
        detailPanel.setOnDeleted(() -> {
            sidebar.refreshStats();
            loadLibraryView(currentView);
        });

        // When user saves from search panel, refresh sidebar stats
        detailPanel.setOnAddedToLibrary(() -> {
            sidebar.refreshStats();
        });

        // PDF viewer
        annotationDAO = new AnnotationDAO();
        pdfViewerPane = new PdfViewerPane(pdfService, annotationDAO);
        pdfViewerPane.setOnBack(() -> {
            loadLibraryView(currentView);
        });
        detailPanel.setOnOpenPdf(req -> showPdfViewer(req));

        filterBar = new FilterBar();
        filterBar.setOnRefresh(() -> {
            sidebar.refreshStats();
            loadLibraryView(currentView);
        });
        filterBar.setOnFilterChange(() -> loadLibraryView(currentView));

        // View title bar
        viewTitle = new Label("My Library");
        viewTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e; -fx-padding: 14 16 8 16;");

        libraryView = new VBox();
        VBox.setVgrow(libraryTable, Priority.ALWAYS);

        // Loading spinner (hidden by default)
        loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(40, 40);
        loadingSpinner.setStyle("-fx-accent: #4a6cf7;");
        loadingSpinner.setVisible(false);
        loadingSpinner.setManaged(false);

        StackPane tableArea = new StackPane(libraryTable, loadingSpinner);
        VBox.setVgrow(tableArea, Priority.ALWAYS);

        // DOI Quick-Import Bar
        HBox doiBar = buildDoiImportBar();

        libraryView.getChildren().addAll(viewTitle, doiBar, filterBar, tableArea);

        // === Sidebar Navigation ===
        // === Chat Pane ===
        chatPane = new ChatPane(libraryService);
        chatPane.setOnSettingsClick(() -> {
            AISettingsDialog dialog = new AISettingsDialog();
            dialog.setOnSaved(() -> chatPane.refreshProviderLabel());
            dialog.showAndWait();
        });

        sidebar.setOnNavigate(nav -> {
            switch (nav) {
                case "search" -> showSearch();
                case "all_references" -> { isTrashView = false; loadLibraryView("ALL_ACTIVE"); }
                case "recently_added" -> { isTrashView = false; loadLibraryView("RECENTLY_ADDED"); }
                case "favorites" -> { isTrashView = false; loadLibraryView("FAVORITES"); }
                case "unsorted" -> { isTrashView = false; loadLibraryView("UNSORTED"); }
                case "trash" -> { isTrashView = true; loadLibraryView("TRASH"); }
                case "paper_graph" -> showPaperGraph();
                case "ai_chat" -> showChat();
                case "stats" -> showStats();
                case "add_entry" -> showAddDialog();
                case "create_collection" -> showCreateCollectionDialog();
                case "create_smart_collection" -> showCreateSmartCollectionDialog();
                default -> { isTrashView = false; loadLibraryView("ALL_ACTIVE"); }
            }
        });

        sidebar.setOnImport(this::handleImport);
        sidebar.setOnExport(this::handleExport);

        // Selection handler — show detail panel when a paper is clicked
        libraryTable.setOnSelect(this::onEntrySelected);

        // Edit handler — right-click Edit opens detail panel in edit mode immediately
        libraryTable.setOnEdit(entry -> {
            detailPanel.showEntry(entry);
            detailPanel.triggerEdit();  // switch to edit mode
            setRight(detailPanel);
        });

        // Delete handler - when deleted from table context menu
        libraryTable.setOnDelete(entry -> {
            sidebar.refreshStats();
            detailPanel.showEmpty();
        });

        setLeft(sidebar);
        setCenter(libraryView);

        // ── Command Palette ──────────────────────────────────────────────────
        commandPalette = new CommandPalette(libraryService);
        commandPalette.setOnNavigate(nav -> {
            switch (nav) {
                case "search" -> showSearch();
                case "all_references" -> { isTrashView = false; loadLibraryView("ALL_ACTIVE"); }
                case "recently_added" -> { isTrashView = false; loadLibraryView("RECENTLY_ADDED"); }
                case "favorites" -> { isTrashView = false; loadLibraryView("FAVORITES"); }
                case "unsorted" -> { isTrashView = false; loadLibraryView("UNSORTED"); }
                case "trash" -> { isTrashView = true; loadLibraryView("TRASH"); }
                case "paper_graph" -> showPaperGraph();
                case "ai_chat" -> showChat();
                case "stats" -> showStats();
                case "add_entry" -> showAddDialog();
                case "import" -> handleImport();
                case "export" -> handleExport();
                default -> {}
            }
        });
        commandPalette.setOnSelectEntry(entry -> {
            detailPanel.showEntry(entry);
            setCenter(libraryView);
            setRight(detailPanel);
        });

        // ── Paper Graph ──────────────────────────────────────────────────────
        paperGraphPane = new PaperGraphPane(libraryService);
        paperGraphPane.setOnSelectEntry(entry -> {
            detailPanel.showEntry(entry);
            setRight(detailPanel);
        });

        // ── Local Semantic AI Status Bar ─────────────────────────────────────
        initStatusBar();
        setupStatusBarListeners();
        updateStatusBar();
    }

    private void initStatusBar() {
        statusBar = new HBox(12);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 16, 6, 16));
        statusBar.setStyle("-fx-background-color: #f0f3ff; -fx-border-color: #d4d4e0; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #3f3f5a; -fx-font-weight: bold;");

        statusAction1 = new Hyperlink();
        statusAction1.setStyle("-fx-font-size: 10px; -fx-text-fill: #4a6cf7; -fx-font-weight: bold; -fx-underline: true;");
        statusAction1.setFocusTraversable(false);

        statusAction2 = new Hyperlink();
        statusAction2.setStyle("-fx-font-size: 10px; -fx-text-fill: #cc3333; -fx-font-weight: bold; -fx-underline: true;");
        statusAction2.setFocusTraversable(false);

        statusBar.getChildren().addAll(statusLabel, statusAction1, statusAction2);
    }

    private void setupStatusBarListeners() {
        com.citeright.ai.BgeM3ModelDownloader downloader = com.citeright.ai.EmbeddingService.getInstance().getDownloader();
        downloader.addListener(new com.citeright.ai.BgeM3ModelDownloader.DownloadListener() {
            @Override
            public void onProgress(double percent, double speedMBs, String activeFile, long downloadedBytes, long totalBytes) {
                lastPercent = percent;
                lastSpeed = speedMBs;
                lastFileLabel = activeFile;
                Platform.runLater(() -> {
                    if (downloader.getState() == com.citeright.ai.BgeM3ModelDownloader.State.DOWNLOADING) {
                        if (getBottom() != statusBar) {
                            setBottom(statusBar);
                        }
                        statusLabel.setText(String.format("Downloading Semantic AI Pack (%s)... %d%% (Speed: %.1f MB/s)",
                                activeFile, Math.round(percent * 100), speedMBs));
                        statusAction1.setText("Pause");
                        statusAction1.setVisible(true);
                        statusAction1.setManaged(true);
                        statusAction1.setOnAction(e -> downloader.pause());

                        statusAction2.setText("Cancel");
                        statusAction2.setVisible(true);
                        statusAction2.setManaged(true);
                        statusAction2.setOnAction(e -> downloader.cancel());
                    }
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> updateStatusBar());
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    if (getBottom() != statusBar) {
                        setBottom(statusBar);
                    }
                    statusLabel.setText("❌ Local AI download error: " + errorMessage);
                    statusAction1.setText("Retry");
                    statusAction1.setVisible(true);
                    statusAction1.setManaged(true);
                    statusAction1.setOnAction(e -> downloader.start());

                    statusAction2.setText("Dismiss");
                    statusAction2.setVisible(true);
                    statusAction2.setManaged(true);
                    statusAction2.setOnAction(e -> setBottom(null));
                });
            }

            @Override
            public void onStateChanged(com.citeright.ai.BgeM3ModelDownloader.State newState) {
                Platform.runLater(() -> updateStatusBar());
            }
        });

        com.citeright.ai.EmbeddingQueueManager.getInstance().addListener((remainingSize, isIndexing) -> {
            Platform.runLater(() -> updateStatusBar());
        });
    }

    private void updateStatusBar() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateStatusBar);
            return;
        }

        com.citeright.ai.BgeM3ModelDownloader downloader = com.citeright.ai.EmbeddingService.getInstance().getDownloader();
        com.citeright.ai.BgeM3ModelDownloader.State dlState = downloader.getState();
        com.citeright.ai.EmbeddingQueueManager queueManager = com.citeright.ai.EmbeddingQueueManager.getInstance();

        if (dlState == com.citeright.ai.BgeM3ModelDownloader.State.DOWNLOADING) {
            if (getBottom() != statusBar) {
                setBottom(statusBar);
            }
            // Will be updated by onProgress callback
        } else if (dlState == com.citeright.ai.BgeM3ModelDownloader.State.PAUSED) {
            if (getBottom() != statusBar) {
                setBottom(statusBar);
            }
            statusLabel.setText("Downloading Semantic AI Pack... Paused");
            statusAction1.setText("Resume");
            statusAction1.setVisible(true);
            statusAction1.setManaged(true);
            statusAction1.setOnAction(e -> downloader.resume());

            statusAction2.setText("Cancel");
            statusAction2.setVisible(true);
            statusAction2.setManaged(true);
            statusAction2.setOnAction(e -> downloader.cancel());
        } else if (com.citeright.ai.GeminiConfig.isBgeM3() && com.citeright.ai.EmbeddingService.getInstance().isBgeM3Ready() && queueManager.getQueueSize() > 0) {
            if (getBottom() != statusBar) {
                setBottom(statusBar);
            }
            int remaining = queueManager.getQueueSize();
            statusLabel.setText("⚙ Local AI is indexing papers... " + remaining + " remaining");
            statusAction1.setVisible(false);
            statusAction1.setManaged(false);
            statusAction2.setVisible(false);
            statusAction2.setManaged(false);
        } else {
            if (dlState == com.citeright.ai.BgeM3ModelDownloader.State.COMPLETED && lastDlStateRef != com.citeright.ai.BgeM3ModelDownloader.State.COMPLETED) {
                if (getBottom() != statusBar) {
                    setBottom(statusBar);
                }
                statusLabel.setText("🟢 Local Semantic AI Ready");
                statusAction1.setVisible(false);
                statusAction1.setManaged(false);
                statusAction2.setVisible(false);
                statusAction2.setManaged(false);

                new Thread(() -> {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> {
                        if (downloader.getState() == com.citeright.ai.BgeM3ModelDownloader.State.COMPLETED && queueManager.getQueueSize() == 0) {
                            setBottom(null);
                        }
                    });
                }).start();
            } else {
                setBottom(null);
            }
        }
        lastDlStateRef = dlState;
    }

    /**
     * Installs the global Ctrl+K shortcut on the scene.
     * Must be called after the scene is set on the stage.
     */
    public void installKeyboardShortcuts() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                KeyCodeCombination ctrlK = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
                newScene.getAccelerators().put(ctrlK, () -> {
                    if (commandPalette.isShowing()) {
                        commandPalette.dismiss();
                    } else {
                        commandPalette.show();
                    }
                });
            }
        });
    }

    /** Returns the command palette overlay (must be added to a StackPane root). */
    public CommandPalette getCommandPalette() {
        return commandPalette;
    }

    // === Welcome Screen ===
    private void showWelcome() {
        VBox welcome = new VBox(16);
        welcome.setAlignment(Pos.CENTER);
        welcome.setPadding(new Insets(60));
        welcome.setStyle("-fx-background-color: #f4f4f8;");

        Label emoji = new Label("📚");
        emoji.setStyle("-fx-font-size: 48px;");

        Label title = new Label("Welcome to CiteRight");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Your personal reference manager.\nSearch papers, save them, and generate citations instantly.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6a6a8a; -fx-text-alignment: center;");
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);

        Button searchBtn = new Button("🔍  Search Papers");
        searchBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 13px; " +
                "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold;");
        searchBtn.setOnAction(e -> showSearch());

        Button importBtn = new Button("📥  Import File");
        importBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 13px; " +
                "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
        importBtn.setOnAction(e -> handleImport());

        Button addBtn = new Button("＋  Add Manually");
        addBtn.setStyle("-fx-background-color: #e8e8f0; -fx-text-fill: #4a4a6a; -fx-font-size: 13px; " +
                "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
        addBtn.setOnAction(e -> showAddDialog());

        actions.getChildren().addAll(searchBtn, importBtn, addBtn);

        // Tips
        VBox tips = new VBox(8);
        tips.setAlignment(Pos.CENTER);
        tips.setPadding(new Insets(24, 0, 0, 0));
        Label tipTitle = new Label("Quick Tips");
        tipTitle.setStyle("-fx-text-fill: #8a8aaa; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label tip1 = new Label("💡  Search by typing a topic — we search 3 databases at once");
        Label tip2 = new Label("💡  Right-click any paper to copy its citation in APA, IEEE, or BibTeX");
        Label tip3 = new Label("💡  Click a paper to see its details and abstract");
        Label tip4 = new Label("💡  Press Ctrl + K to open the Command Palette");
        for (Label tip : new Label[]{tip1, tip2, tip3, tip4}) {
            tip.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 11px;");
        }
        tips.getChildren().addAll(tipTitle, tip1, tip2, tip3, tip4);

        welcome.getChildren().addAll(emoji, title, subtitle, actions, tips);
        setCenter(welcome);
        setRight(null);
    }

    // === Show Search ===
    private void showSearch() {
        setCenter(searchPane);
        // Don't hide the detail panel if one is already open
        if (getRight() != detailPanel) setRight(null);
    }

    // === Show Paper Graph ===
    private void showPaperGraph() {
        paperGraphPane.buildGraph();
        setCenter(paperGraphPane);
        setRight(null);
    }

    // === Show AI Chat ===
    private void showChat() {
        setCenter(chatPane);
        setRight(null);
    }

    // === Show PDF Viewer ===
    private void showPdfViewer(PdfOpenRequest req) {
        pdfViewerPane.loadPdf(req.getPdfPath(), req.getPdfId(), req.getTitle(), req.getPublication());
        SplitPane splitView = new SplitPane();
        splitView.getItems().addAll(pdfViewerPane, detailPanel);
        splitView.setDividerPositions(0.75); // 75% PDF, 25% Notes/Details
        setCenter(splitView);
        setRight(null); // Clear the usual right panel since it's now in the SplitPane
    }

    // === Entry Selection ===
    private void onEntrySelected(LibraryEntry entry) {
        detailPanel.showEntry(entry);
        setRight(detailPanel);
    }

    // === Load Library View ===
    private void loadLibraryView(String viewName) {
        currentView = viewName;
        String displayName = switch (viewName) {
            case "RECENTLY_ADDED" -> "Recently Added";
            case "FAVORITES" -> "⭐ Favorites";
            case "UNSORTED" -> "Unsorted";
            case "TRASH" -> "🗑 Trash";
            default -> "My Library";
        };

        // Show loading spinner
        loadingSpinner.setVisible(true);
        loadingSpinner.setManaged(true);

        new Thread(() -> {
            List<LibraryEntry> entries = switch (viewName) {
                case "RECENTLY_ADDED" -> libraryService.getRecentlyAdded();
                case "FAVORITES" -> libraryService.getFavorites();
                case "UNSORTED" -> libraryService.getUnsorted();
                case "TRASH" -> libraryService.getTrashed();
                default -> libraryService.getAllActive();
            };

            // Apply text filter
            String searchText = filterBar.getSearchText();
            if (!searchText.isEmpty()) {
                entries = entries.stream().filter(e -> {
                    Publication p = e.getPublication();
                    if (p == null) return false;
                    return (p.getTitle() != null && p.getTitle().toLowerCase().contains(searchText))
                            || p.getAuthorsShort().toLowerCase().contains(searchText);
                }).toList();
            }

            final List<LibraryEntry> finalEntries = entries;
            Platform.runLater(() -> {
                loadingSpinner.setVisible(false);
                loadingSpinner.setManaged(false);
                viewTitle.setText(displayName + "  (" + finalEntries.size() + ")");
                libraryTable.loadEntries(finalEntries);
                libraryTable.setTrashMode(isTrashView);
                detailPanel.setTrashMode(isTrashView);
                detailPanel.showEmpty();
                setCenter(libraryView);
                setRight(null);
            });
        }).start();
    }

    // === Add Dialog ===
    private void showAddDialog() {
        AddEntryDialog dialog = new AddEntryDialog(lookupService, pdfService, libraryService);
        dialog.setOnSaved(pub -> {
            sidebar.refreshStats();
            loadLibraryView(currentView);
        });
        dialog.show();
    }

    // === Import ===
    private void handleImport() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Import References");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("BibTeX Files", "*.bib", "*.bibtex"),
                new javafx.stage.FileChooser.ExtensionFilter("RIS Files", "*.ris"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;

        new Thread(() -> {
            try {
                List<Publication> imported = importExportService.importAuto(file);
                int count = 0;
                for (Publication pub : imported) {
                    if (!libraryService.isInLibrary(pub.getPaperId())) {
                        libraryService.saveToDefaultCollection(pub);
                        count++;
                    }
                }
                final int saved = count;
                Platform.runLater(() -> {
                    sidebar.refreshStats();
                    loadLibraryView("ALL_ACTIVE");
                    showInfo("Import Complete", "Added " + saved + " references to your library.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showInfo("Import Error", ex.getMessage()));
            }
        }).start();
    }

    // === Export ===
    private void handleExport() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Library");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("BibTeX (.bib)", "*.bib"),
                new javafx.stage.FileChooser.ExtensionFilter("RIS (.ris)", "*.ris"),
                new javafx.stage.FileChooser.ExtensionFilter("CSV (.csv)", "*.csv")
        );

        // Default to Downloads folder
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (downloads.exists() && downloads.isDirectory()) {
            chooser.setInitialDirectory(downloads);
        }

        chooser.setInitialFileName("my_library");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        new Thread(() -> {
            try {
                List<Publication> pubs = libraryService.getAllActivePublications();
                String name = file.getName().toLowerCase();
                if (name.endsWith(".ris")) importExportService.exportToRIS(pubs, file);
                else if (name.endsWith(".csv")) importExportService.exportToCSV(pubs, file);
                else importExportService.exportToBibTeX(pubs, file);
                Platform.runLater(() -> showInfo("Export Complete", "Saved " + pubs.size() + " references."));
            } catch (Exception ex) {
                Platform.runLater(() -> showInfo("Export Error", ex.getMessage()));
            }
        }).start();
    }

    // === Create Collection ===
    private void showCreateCollectionDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Collection");
        dialog.setHeaderText(null);
        dialog.setContentText("Collection name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                libraryService.createCollection(name.trim());
                sidebar.refreshStats(); // Update sidebar collections
                showInfo("Done", "Collection '" + name.trim() + "' created.");
            }
        });
    }

    // === Create Smart Collection ===
    private void showCreateSmartCollectionDialog() {
        javafx.scene.control.Dialog<javafx.util.Pair<String, String>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("New Smart Collection");
        dialog.setHeaderText("Create a dynamic collection based on rules.");

        ButtonType createButtonType = new ButtonType("Create", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Name (e.g., AI Papers 2024)");
        TextField queryField = new TextField();
        queryField.setPromptText("Query (e.g., tag:AI year>=2024)");
        queryField.setPrefWidth(250);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Query:"), 0, 1);
        grid.add(queryField, 1, 1);

        Label help = new Label("Available filters: tag:NAME, author:NAME, year>=YYYY, year=YYYY, or plain text for title.");
        help.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        grid.add(help, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new javafx.util.Pair<>(nameField.getText(), queryField.getText());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            String name = result.getKey().trim();
            String query = result.getValue().trim();
            if (!name.isEmpty()) {
                com.citeright.database.CollectionDAO collectionDAO = new com.citeright.database.CollectionDAO();
                collectionDAO.createSmartCollection(name, query);
                sidebar.refreshStats();
                showInfo("Done", "Smart Collection '" + name + "' created.");
            }
        });
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    // === DOI Quick-Import Bar ===
    private HBox buildDoiImportBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 16, 6, 16));
        bar.setStyle("-fx-background-color: #eef0ff; -fx-border-color: #d4d4e0; -fx-border-width: 0 0 1 0;");

        Label icon = new Label("🔗");
        icon.setStyle("-fx-font-size: 14px;");

        TextField doiField = new TextField();
        doiField.setPromptText("Quick Import: Paste DOI, ArXiv ID, or PMID...");
        doiField.setPrefWidth(350);
        doiField.setStyle("-fx-font-size: 11px; -fx-padding: 6 10; -fx-background-radius: 6; " +
                "-fx-border-radius: 6; -fx-border-color: #c4c4d0; -fx-background-color: #ffffff;");

        Button importBtn = new Button("⬇ Import");
        importBtn.setStyle("-fx-background-color: #4a6cf7; -fx-text-fill: #fff; -fx-font-size: 11px; " +
                "-fx-font-weight: bold; -fx-padding: 6 16; -fx-background-radius: 6; -fx-cursor: hand;");

        Label status = new Label("");
        status.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 10px;");

        Runnable doImport = () -> {
            String id = doiField.getText().trim();
            if (id.isEmpty()) return;
            status.setText("⏳ Looking up...");
            importBtn.setDisable(true);
            new Thread(() -> {
                try {
                    Publication pub = lookupService.lookup(id);
                    if (pub != null) {
                        libraryService.saveToDefaultCollection(pub);
                        autoTag(pub);
                        Platform.runLater(() -> {
                            status.setText("✅ Imported: " + pub.getTitle());
                            doiField.clear();
                            importBtn.setDisable(false);
                            sidebar.refreshStats();
                            loadLibraryView(currentView);
                        });
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        status.setText("❌ " + ex.getMessage());
                        importBtn.setDisable(false);
                    });
                }
            }).start();
        };

        doiField.setOnAction(e -> doImport.run());
        importBtn.setOnAction(e -> doImport.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(icon, doiField, importBtn, spacer, status);
        return bar;
    }

    // === Smart Auto-Tagging ===
    private void autoTag(Publication pub) {
        if (pub == null) return;
        try {
            String text = (pub.getTitle() != null ? pub.getTitle() : "") + " " +
                          (pub.getAbstractText() != null ? pub.getAbstractText() : "");
            java.util.List<String> keywords = keywordExtractor.extractKeywords(text, 3);
            int dbId = libraryService.getDbPaperId(pub.getPaperId());
            if (dbId <= 0) return;
            for (String kw : keywords) {
                // Create tag if it doesn't exist, then link
                com.citeright.model.Tag tag = null;
                for (com.citeright.model.Tag existing : libraryService.getAllTags()) {
                    if (existing.getName().equalsIgnoreCase(kw)) { tag = existing; break; }
                }
                if (tag == null) tag = libraryService.createTag(kw);
                libraryService.addTagToPaper(dbId, tag.getId());
            }
        } catch (Exception e) {
            System.err.println("[AutoTag] " + e.getMessage());
        }
    }

    // === Stats Dashboard ===
    private void showStats() {
        statsPane = new StatsPane(libraryService);
        setCenter(statsPane);
        setRight(null);
    }

    // === Local Import Server ===
    private void startLocalServer() {
        localImportServer = new LocalImportServer(lookupService, libraryService);
        localImportServer.setOnImportSuccess(pub -> {
            autoTag(pub);
            sidebar.refreshStats();
            if (currentView.equals("ALL_ACTIVE")) loadLibraryView("ALL_ACTIVE");
        });
        localImportServer.setOnImportError(err -> {
            System.err.println("[LocalImportServer] Error: " + err);
        });
        localImportServer.start();
    }

    /** Gets the root StackPane for toast notifications */
    public void setRootStack(StackPane root) { this.rootStack = root; }
    public StackPane getRootStack() { return rootStack; }
}
