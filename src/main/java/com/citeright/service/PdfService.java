package com.citeright.service;

import com.citeright.database.SQLiteDatabaseManager;
import com.citeright.model.Annotation;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.*;
import org.apache.pdfbox.rendering.PDFRenderer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * PDF operations: download, render pages, export with annotations.
 */
public class PdfService {

    private final HttpClient httpClient;
    private final String pdfDir;

    public PdfService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.pdfDir = SQLiteDatabaseManager.getInstance().getPdfDirPath();
    }

    /** Download PDF from URL to local storage, returns file path */
    public String downloadPdf(String url, String suggestedName) throws Exception {
        String safeName = suggestedName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!safeName.endsWith(".pdf")) safeName += ".pdf";
        Path dest = Path.of(pdfDir, safeName);

        // Avoid re-download
        if (Files.exists(dest) && Files.size(dest) > 0) return dest.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "CiteRight/1.0")
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new Exception("Download failed: HTTP " + response.statusCode());

        Files.createDirectories(dest.getParent());
        Files.write(dest, response.body());
        System.out.println("[PdfService] Downloaded: " + dest);
        return dest.toString();
    }

    /** Render a single PDF page as a JavaFX Image */
    public Image renderPage(String pdfPath, int pageNumber, float scale) throws Exception {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageIdx = Math.max(0, Math.min(pageNumber - 1, doc.getNumberOfPages() - 1));
            BufferedImage bimg = renderer.renderImageWithDPI(pageIdx, 72 * scale);
            return SwingFXUtils.toFXImage(bimg, null);
        }
    }

    /** Get total page count */
    public int getPageCount(String pdfPath) {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            return doc.getNumberOfPages();
        } catch (Exception e) { return 0; }
    }

    /** Get file size in bytes */
    public long getFileSize(String pdfPath) {
        try { return Files.size(Path.of(pdfPath)); } catch (Exception e) { return 0; }
    }

    /** Export PDF with embedded annotations */
    public void exportAnnotatedPdf(String sourcePath, List<Annotation> annotations, String outputPath) throws Exception {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(sourcePath))) {
            for (Annotation ann : annotations) {
                int pageIdx = ann.getPageNumber() - 1;
                if (pageIdx < 0 || pageIdx >= doc.getNumberOfPages()) continue;
                PDPage page = doc.getPage(pageIdx);
                PDRectangle mediaBox = page.getMediaBox();

                if ("HIGHLIGHT".equals(ann.getTypeString()) || "UNDERLINE".equals(ann.getTypeString())) {
                    PDAnnotationTextMarkup markup;
                    if ("HIGHLIGHT".equals(ann.getTypeString())) {
                        markup = new PDAnnotationHighlight();
                    } else {
                        markup = new PDAnnotationUnderline();
                    }
                    PDRectangle rect = new PDRectangle((float) ann.getX(), mediaBox.getHeight() - (float) ann.getY() - (float) ann.getHeight(),
                            (float) ann.getWidth(), (float) ann.getHeight());
                    markup.setRectangle(rect);
                    float[] quads = new float[]{rect.getLowerLeftX(), rect.getUpperRightY(), rect.getUpperRightX(), rect.getUpperRightY(),
                            rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getUpperRightX(), rect.getLowerLeftY()};
                    markup.setQuadPoints(quads);
                    if (ann.getContent() != null) markup.setContents(ann.getContent());
                    page.getAnnotations().add(markup);
                } else if ("STICKY_NOTE".equals(ann.getTypeString()) || "NOTE".equals(ann.getTypeString())) {
                    PDAnnotationText note = new PDAnnotationText();
                    note.setRectangle(new PDRectangle((float) ann.getX(), mediaBox.getHeight() - (float) ann.getY() - 24, 24, 24));
                    note.setContents(ann.getContent() != null ? ann.getContent() : "");
                    note.setName(PDAnnotationText.NAME_COMMENT);
                    page.getAnnotations().add(note);
                }
            }
            doc.save(outputPath);
            System.out.println("[PdfService] Exported annotated PDF: " + outputPath);
        }
    }

    /** Extract all text from a specific page */
    public String extractPageText(String pdfPath, int pageNumber) {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            return stripper.getText(doc);
        } catch (Exception e) {
            System.err.println("[PdfService] Text extract error: " + e.getMessage());
            return null;
        }
    }

    /** Extract text from a rectangular region on a page (coordinates in PDF points) */
    public String extractTextFromRegion(String pdfPath, int pageNumber, double x, double y, double w, double h) {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            int pageIdx = pageNumber - 1;
            if (pageIdx < 0 || pageIdx >= doc.getNumberOfPages()) return null;
            PDPage page = doc.getPage(pageIdx);
            org.apache.pdfbox.text.PDFTextStripperByArea stripper = new org.apache.pdfbox.text.PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            java.awt.geom.Rectangle2D region = new java.awt.geom.Rectangle2D.Double(x, y, w, h);
            stripper.addRegion("sel", region);
            stripper.extractRegions(page);
            return stripper.getTextForRegion("sel");
        } catch (Exception e) {
            System.err.println("[PdfService] Region extract error: " + e.getMessage());
            return null;
        }
    }

    /** Search for text in a page, returns true if found */
    public boolean pageContainsText(String pdfPath, int pageNumber, String query) {
        String text = extractPageText(pdfPath, pageNumber);
        return text != null && text.toLowerCase().contains(query.toLowerCase());
    }

    /** Batch search: opens the doc ONCE and searches all pages. Returns list of matching page numbers. */
    public List<Integer> searchAllPages(String pdfPath, String query) {
        List<Integer> results = new java.util.ArrayList<>();
        if (query == null || query.isBlank()) return results;
        String q = query.toLowerCase();
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                if (text != null && text.toLowerCase().contains(q)) results.add(i);
            }
        } catch (Exception e) { System.err.println("[PdfService] Batch search error: " + e.getMessage()); }
        return results;
    }

    /** Render a thumbnail (low-res image) of a page */
    public Image renderThumbnail(String pdfPath, int pageNumber, float scale) {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageIdx = Math.max(0, Math.min(pageNumber - 1, doc.getNumberOfPages() - 1));
            BufferedImage bimg = renderer.renderImageWithDPI(pageIdx, 72 * scale);
            return SwingFXUtils.toFXImage(bimg, null);
        } catch (Exception e) { return null; }
    }

    /** Batch render all thumbnails — opens the doc ONCE. Returns map of page number -> Image. */
    public java.util.Map<Integer, Image> renderAllThumbnails(String pdfPath, float scale) {
        java.util.Map<Integer, Image> thumbnails = new java.util.LinkedHashMap<>();
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage bimg = renderer.renderImageWithDPI(i, 72 * scale);
                thumbnails.put(i + 1, SwingFXUtils.toFXImage(bimg, null));
            }
        } catch (Exception e) { System.err.println("[PdfService] Batch thumbnail error: " + e.getMessage()); }
        return thumbnails;
    }

    /** Extract full text from a PDF for RAG/chat analysis */
    public String extractText(String pdfPath) {
        if (pdfPath == null || pdfPath.isEmpty()) return "";
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(pdfPath))) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            System.err.println("[PdfService] Error extracting text: " + e.getMessage());
            return "";
        }
    }
}
