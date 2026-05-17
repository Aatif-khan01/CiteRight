package com.citeright.ui;

import com.citeright.model.LibraryEntry;
import com.citeright.model.Publication;
import com.citeright.service.LibraryService;
import com.citeright.service.ReadingTimerService;
import javafx.geometry.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reading Statistics Dashboard — gamified research progress tracker.
 * Shows papers read, reading streaks, top topics, and citation stats.
 */
public class StatsPane extends VBox {

    private final LibraryService libraryService;

    public StatsPane(LibraryService libraryService) {
        this.libraryService = libraryService;
        buildUI();
    }

    private void buildUI() {
        setStyle("-fx-background-color: #f4f4f8;");
        setSpacing(0);

        // ── Header ──
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 24, 14, 24));
        header.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e8e8f0; -fx-border-width: 0 0 1 0;");

        Label icon = new Label("📊");
        icon.setStyle("-fx-font-size: 22px;");
        Label title = new Label("Reading Statistics");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        header.getChildren().addAll(icon, title);

        // ── Stats Cards ──
        List<LibraryEntry> all = libraryService.getAllActive();
        List<LibraryEntry> favs = libraryService.getFavorites();
        List<LibraryEntry> recent = libraryService.getRecentlyAdded();

        int totalPapers = all.size();
        int totalFavs = favs.size();
        int totalRecent = recent.size();
        long readCount = all.stream().filter(e -> "READ".equals(e.getReadStatus())).count();

        HBox cards = new HBox(16);
        cards.setPadding(new Insets(20, 24, 20, 24));
        cards.setAlignment(Pos.CENTER);

        cards.getChildren().addAll(
            statCard("📚", "Total Papers", String.valueOf(totalPapers), "#4a6cf7"),
            statCard("✅", "Papers Read", String.valueOf(readCount), "#1a7a3a"),
            statCard("⭐", "Favorites", String.valueOf(totalFavs), "#f5a623"),
            statCard("🆕", "This Week", String.valueOf(totalRecent), "#6c5ce7")
        );

        // ── Reading Progress ──
        VBox progressSection = new VBox(8);
        progressSection.setPadding(new Insets(0, 24, 20, 24));
        Label progTitle = new Label("📖 Reading Progress");
        progTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        double readPct = totalPapers > 0 ? (readCount * 100.0 / totalPapers) : 0;
        ProgressBar readBar = new ProgressBar(readPct / 100.0);
        readBar.setPrefWidth(Double.MAX_VALUE);
        readBar.setPrefHeight(16);
        readBar.setStyle("-fx-accent: #4a6cf7;");
        Label readLbl = new Label(String.format("%.0f%% of your library read (%d / %d)", readPct, readCount, totalPapers));
        readLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 11px;");
        progressSection.getChildren().addAll(progTitle, readBar, readLbl);

        // ── Year Distribution Chart ──
        VBox chartSection = new VBox(8);
        chartSection.setPadding(new Insets(0, 24, 20, 24));
        Label chartTitle = new Label("📅 Papers by Year");
        chartTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        TreeMap<Integer, Integer> yearCounts = new TreeMap<>();
        for (LibraryEntry e : all) {
            Publication p = e.getPublication();
            if (p != null && p.getYear() > 1900) {
                yearCounts.merge(p.getYear(), 1, Integer::sum);
            }
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Year");
        yAxis.setLabel("Papers");
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(200);
        barChart.setStyle("-fx-background-color: transparent;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        // Show last 10 years max
        List<Map.Entry<Integer, Integer>> yearList = new ArrayList<>(yearCounts.entrySet());
        int start = Math.max(0, yearList.size() - 10);
        for (int i = start; i < yearList.size(); i++) {
            series.getData().add(new XYChart.Data<>(String.valueOf(yearList.get(i).getKey()), yearList.get(i).getValue()));
        }
        barChart.getData().add(series);
        chartSection.getChildren().addAll(chartTitle, barChart);

        // ── Top Topics ──
        VBox topicsSection = new VBox(8);
        topicsSection.setPadding(new Insets(0, 24, 20, 24));
        Label topicsTitle = new Label("🏷️ Top Venues");
        topicsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Map<String, Integer> venueCounts = new HashMap<>();
        for (LibraryEntry e : all) {
            Publication p = e.getPublication();
            if (p != null && p.getVenue() != null && !p.getVenue().isBlank()) {
                venueCounts.merge(p.getVenue(), 1, Integer::sum);
            }
        }

        VBox venueList = new VBox(4);
        venueCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(8)
            .forEach(entry -> {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 8, 4, 8));
                row.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 6;");
                Label name = new Label(entry.getKey());
                name.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 11px;");
                name.setMaxWidth(300);
                name.setEllipsisString("…");
                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);
                Label cnt = new Label(entry.getValue() + " papers");
                cnt.setStyle("-fx-text-fill: #4a6cf7; -fx-font-size: 11px; -fx-font-weight: bold;");
                row.getChildren().addAll(name, sp, cnt);
                venueList.getChildren().add(row);
            });
        topicsSection.getChildren().addAll(topicsTitle, venueList);

        // ── Citation Format Stats ──
        VBox quoteSection = new VBox(8);
        quoteSection.setPadding(new Insets(0, 24, 20, 24));
        Label quoteTitle = new Label("💡 Research Insight");
        quoteTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        int avgYear = yearCounts.isEmpty() ? 0 :
            yearCounts.entrySet().stream().mapToInt(e -> e.getKey() * e.getValue()).sum() /
            yearCounts.values().stream().mapToInt(Integer::intValue).sum();
        Label insight = new Label(String.format(
            "Your library spans %d years (%s → %s). Average publication year: %d. " +
            "You have %d favorited papers and %d papers read.",
            yearCounts.isEmpty() ? 0 : yearCounts.lastEntry().getKey() - yearCounts.firstEntry().getKey(),
            yearCounts.isEmpty() ? "—" : yearCounts.firstEntry().getKey(),
            yearCounts.isEmpty() ? "—" : yearCounts.lastEntry().getKey(),
            avgYear, totalFavs, readCount
        ));
        insight.setWrapText(true);
        insight.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 11px; -fx-padding: 12; " +
            "-fx-background-color: #eef0ff; -fx-background-radius: 8;");
        quoteSection.getChildren().addAll(quoteTitle, insight);

        // ── Scroll wrapper ──
        VBox content = new VBox(0, cards, new Separator(), progressSection,
            new Separator(), chartSection, new Separator(), topicsSection,
            new Separator(), quoteSection);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f4f4f8; -fx-background-color: #f4f4f8;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);
    }

    private VBox statCard(String emoji, String label, String value, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16, 24, 16, 24));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2); -fx-min-width: 140;");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label emojiLbl = new Label(emoji);
        emojiLbl.setStyle("-fx-font-size: 24px;");
        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label nameLbl = new Label(label);
        nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a5a7a;");

        card.getChildren().addAll(emojiLbl, valLbl, nameLbl);
        return card;
    }
}
