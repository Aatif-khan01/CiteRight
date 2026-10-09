package com.citeright.service;

import com.citeright.model.Publication;
import java.util.function.Consumer;

/**
 * Asynchronous metadata enrichment using DOI resolution.
 * 
 * Wraps MetadataLookupService with background thread execution and
 * graceful fallback on network failure. Uses Platform.runLater() callback
 * pattern so UI components can safely update after enrichment completes.
 */
public class MetadataEnrichmentService {

    private final MetadataLookupService lookupService;

    public MetadataEnrichmentService() {
        this.lookupService = new MetadataLookupService();
    }

    /**
     * Asynchronously enriches a publication's metadata by resolving its DOI
     * via CrossRef. Calls onComplete on the JavaFX Application Thread.
     *
     * @param pub        The publication to enrich (must have a DOI set)
     * @param onComplete Callback with the enriched Publication (or original on failure)
     * @param onError    Callback with error message if enrichment fails
     */
    public void enrichAsync(Publication pub, Consumer<Publication> onComplete, Consumer<String> onError) {
        new Thread(() -> {
            try {
                String doi = pub.getDoi();
                if (doi == null || doi.isBlank()) {
                    reportError(onError, "No DOI available for this paper. Cannot enrich metadata.");
                    return;
                }

                System.out.println("[Enrich] Starting DOI lookup for: " + doi);
                Publication enriched = lookupService.lookupByDoi(doi);

                // Merge enriched data into existing publication (don't overwrite user edits)
                mergeMetadata(pub, enriched);

                System.out.println("[Enrich] Successfully enriched: " + pub.getTitle());
                javafx.application.Platform.runLater(() -> onComplete.accept(pub));

            } catch (Exception e) {
                System.err.println("[Enrich] Failed: " + e.getMessage());
                reportError(onError, "Enrichment failed: " + e.getMessage());
            }
        }, "MetadataEnrichment-Thread").start();
    }

    /**
     * Synchronously enriches a publication (for batch operations).
     *
     * @param pub The publication to enrich
     * @return true if enrichment succeeded, false otherwise
     */
    public boolean enrichSync(Publication pub) {
        try {
            String doi = pub.getDoi();
            if (doi == null || doi.isBlank()) return false;

            Publication enriched = lookupService.lookupByDoi(doi);
            mergeMetadata(pub, enriched);
            return true;
        } catch (Exception e) {
            System.err.println("[Enrich] Sync enrichment failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Merges enriched data into the original publication.
     * Only fills in BLANK fields — never overwrites existing user data.
     */
    private void mergeMetadata(Publication target, Publication source) {
        if (target.getTitle() == null || target.getTitle().isBlank()) {
            target.setTitle(source.getTitle());
        }
        if (target.getAbstractText() == null || target.getAbstractText().isBlank()) {
            target.setAbstractText(source.getAbstractText());
        }
        if (target.getYear() <= 0 && source.getYear() > 0) {
            target.setYear(source.getYear());
        }
        if ((target.getAuthors() == null || target.getAuthors().isEmpty())
                && source.getAuthors() != null && !source.getAuthors().isEmpty()) {
            target.setAuthors(source.getAuthors());
        }
        if (target.getVenue() == null || target.getVenue().isBlank()) {
            target.setVenue(source.getVenue());
        }
        if (target.getUrl() == null || target.getUrl().isBlank()) {
            target.setUrl(source.getUrl());
        }
        if (target.getCitationCount() <= 0 && source.getCitationCount() > 0) {
            target.setCitationCount(source.getCitationCount());
        }
    }

    private void reportError(Consumer<String> onError, String message) {
        if (onError != null) {
            javafx.application.Platform.runLater(() -> onError.accept(message));
        }
    }
}
