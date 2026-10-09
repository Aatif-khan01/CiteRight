package com.citeright.ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * Professional annotation toolbar inspired by MS Word's ribbon.
 * Tools are grouped into logical sections with separators.
 */
public class AnnotationToolbar extends HBox {

    public enum Tool { CURSOR, HIGHLIGHT, UNDERLINE, PEN, ERASER, STICKY_NOTE, TEXT_BOX }

    private Tool activeTool = Tool.CURSOR;
    private Color activeColor = Color.web("#FFEB3B");
    private Consumer<Tool> onToolChange;
    private Consumer<Color> onColorChange;
    private Label activeToolLabel;
    private ToggleGroup toolGroup;

    public AnnotationToolbar() { buildUI(); }

    public void setOnToolChange(Consumer<Tool> h) { this.onToolChange = h; }
    public void setOnColorChange(Consumer<Color> h) { this.onColorChange = h; }
    public Tool getActiveTool() { return activeTool; }
    public Color getActiveColor() { return activeColor; }

    private void buildUI() {
        setSpacing(0); setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(0));
        setStyle("-fx-background-color: #1e1e3a;");
        setMinHeight(44); setMaxHeight(44);

        toolGroup = new ToggleGroup();

        // ── Selection Group ──
        HBox selectGroup = toolSection("SELECT");
        ToggleButton cursorBtn = createToolBtn("🖱", "Select (V)", toolGroup, Tool.CURSOR);
        cursorBtn.setSelected(true);
        selectGroup.getChildren().add(cursorBtn);
        getChildren().addAll(selectGroup, groupSeparator());

        // ── Markup Group ──
        HBox markupGroup = toolSection("MARKUP");
        markupGroup.getChildren().addAll(
                createToolBtn("🖍", "Highlight (H)", toolGroup, Tool.HIGHLIGHT),
                createToolBtn("U̲", "Underline (U)", toolGroup, Tool.UNDERLINE),
                createToolBtn("🖊", "Pen (P)", toolGroup, Tool.PEN)
        );
        getChildren().addAll(markupGroup, groupSeparator());

        // ── Annotate Group ──
        HBox annotateGroup = toolSection("ANNOTATE");
        annotateGroup.getChildren().addAll(
                createToolBtn("📝", "Sticky Note (N)", toolGroup, Tool.STICKY_NOTE),
                createToolBtn("T", "Text Box (T)", toolGroup, Tool.TEXT_BOX),
                createToolBtn("⌫", "Eraser (E)", toolGroup, Tool.ERASER)
        );
        getChildren().addAll(annotateGroup, groupSeparator());

        // ── Color Palette ──
        HBox colorSection = toolSection("COLOR");
        VBox colorRows = new VBox(2);
        colorRows.setAlignment(Pos.CENTER);
        colorRows.setPadding(new Insets(2, 0, 0, 0));
        HBox row1 = new HBox(3); row1.setAlignment(Pos.CENTER);
        HBox row2 = new HBox(3); row2.setAlignment(Pos.CENTER);
        java.util.List<HBox> allRows = java.util.List.of(row1, row2);

        String[][] colors = {
                {"#FFEB3B", "Yellow"}, {"#FF5252", "Red"}, {"#4CAF50", "Green"}, {"#2196F3", "Blue"},
                {"#E040FB", "Purple"}, {"#FF9800", "Orange"}, {"#00BCD4", "Cyan"}, {"#FFFFFF", "White"},
                {"#EC407A", "Pink"}, {"#8D6E63", "Brown"}, {"#78909C", "Grey"}, {"#CDDC39", "Lime"},
                {"#FF6F00", "Dark Orange"}, {"#1565C0", "Navy"}, {"#00695C", "Teal"}, {"#000000", "Black"}
        };
        for (int i = 0; i < colors.length; i++) {
            String[] c = colors[i];
            Button cb = new Button();
            cb.setPrefSize(14, 14); cb.setMinSize(14, 14); cb.setMaxSize(14, 14);
            boolean isActive = c[0].equals("#FFEB3B");
            cb.setStyle(colorBtnStyle(c[0], isActive));
            cb.setTooltip(new Tooltip(c[1]));
            cb.setOnAction(e -> {
                activeColor = Color.web(c[0]);
                for (HBox r : allRows) r.getChildren().forEach(node -> {
                    if (node instanceof Button btn) {
                        String hex = (String) btn.getUserData();
                        btn.setStyle(colorBtnStyle(hex, hex.equals(c[0])));
                    }
                });
                if (onColorChange != null) onColorChange.accept(activeColor);
            });
            cb.setUserData(c[0]);
            (i < 8 ? row1 : row2).getChildren().add(cb);
        }
        colorRows.getChildren().addAll(row1, row2);
        colorSection.getChildren().add(colorRows);
        getChildren().add(colorSection);

        // ── Spacer + Active Tool Indicator ──
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        activeToolLabel = new Label("🖱 Select");
        activeToolLabel.setStyle("-fx-text-fill: #8888bb; -fx-font-size: 10px; -fx-padding: 0 12 0 0;");

        getChildren().addAll(spacer, activeToolLabel);
    }

    private String colorBtnStyle(String hex, boolean active) {
        // For dark colors, always use a visible light border
        boolean isDarkColor = isDark(hex);
        String border = active ? "#ffffff" : (isDarkColor ? "#8888aa" : "#55557a");
        String width = active ? "2" : "1";
        return "-fx-background-color: " + hex + "; -fx-background-radius: 3; -fx-cursor: hand; " +
                "-fx-border-color: " + border + "; -fx-border-radius: 3; -fx-border-width: " + width + ";";
    }

    private boolean isDark(String hex) {
        try {
            javafx.scene.paint.Color c = javafx.scene.paint.Color.web(hex);
            return c.getBrightness() < 0.35;
        } catch (Exception e) { return false; }
    }

    private HBox toolSection(String label) {
        HBox section = new HBox(2);
        section.setAlignment(Pos.CENTER);
        section.setPadding(new Insets(2, 8, 2, 8));
        return section;
    }

    private Region groupSeparator() {
        Region sep = new Region();
        sep.setPrefWidth(1); sep.setMinWidth(1); sep.setMaxWidth(1);
        sep.setPrefHeight(32);
        sep.setStyle("-fx-background-color: #33335a;");
        return sep;
    }

    private void selectTool(Tool tool) {
        activeTool = tool;
        String name = switch (tool) {
            case CURSOR -> "🖱 Select";
            case HIGHLIGHT -> "🖍 Highlight";
            case UNDERLINE -> "U̲ Underline";
            case PEN -> "🖊 Pen";
            case ERASER -> "⌫ Eraser";
            case STICKY_NOTE -> "📝 Note";
            case TEXT_BOX -> "T Text Box";
        };
        activeToolLabel.setText(name);
        if (onToolChange != null) onToolChange.accept(tool);
    }

    private ToggleButton createToolBtn(String icon, String tooltip, ToggleGroup group, Tool tool) {
        ToggleButton btn = new ToggleButton(icon);
        btn.setToggleGroup(group);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setMinSize(34, 30); btn.setPrefSize(34, 30); btn.setMaxHeight(30);
        btn.setStyle(toolBtnStyle(false));
        btn.selectedProperty().addListener((o, a, b) -> btn.setStyle(toolBtnStyle(b)));
        btn.setOnAction(e -> selectTool(tool));
        return btn;
    }

    private String toolBtnStyle(boolean selected) {
        if (selected) {
            return "-fx-background-color: #4a6cf7; -fx-text-fill: #ffffff; -fx-font-size: 13px; " +
                    "-fx-padding: 3 6; -fx-background-radius: 4;";
        }
        return "-fx-background-color: transparent; -fx-text-fill: #aaaacc; -fx-font-size: 13px; " +
                "-fx-padding: 3 6; -fx-cursor: hand; -fx-background-radius: 4;";
    }
}
