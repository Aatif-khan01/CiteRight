package com.citeright.service;

import com.citeright.model.LibraryEntry;
import java.util.*;

/**
 * Detects duplicate library entries using DOI matching and fuzzy title similarity.
 */
public class DuplicateDetectionService {

    /** Returns groups of duplicate entries */
    public List<List<LibraryEntry>> findDuplicates(List<LibraryEntry> entries) {
        List<List<LibraryEntry>> duplicateGroups = new ArrayList<>();
        boolean[] visited = new boolean[entries.size()];

        for (int i = 0; i < entries.size(); i++) {
            if (visited[i]) continue;
            List<LibraryEntry> group = new ArrayList<>();
            group.add(entries.get(i));

            for (int j = i + 1; j < entries.size(); j++) {
                if (visited[j]) continue;
                if (isDuplicate(entries.get(i), entries.get(j))) {
                    group.add(entries.get(j));
                    visited[j] = true;
                }
            }
            if (group.size() > 1) {
                duplicateGroups.add(group);
                visited[i] = true;
            }
        }
        return duplicateGroups;
    }

    private boolean isDuplicate(LibraryEntry a, LibraryEntry b) {
        if (a.getPublication() == null || b.getPublication() == null) return false;

        // Exact DOI match
        String doiA = a.getPublication().getDoi();
        String doiB = b.getPublication().getDoi();
        if (doiA != null && doiB != null && !doiA.isEmpty() && !doiB.isEmpty()) {
            return doiA.equalsIgnoreCase(doiB);
        }

        // Fuzzy title match + same year
        String titleA = normalize(a.getPublication().getTitle());
        String titleB = normalize(b.getPublication().getTitle());
        if (titleA.isEmpty() || titleB.isEmpty()) return false;

        double similarity = jaccardSimilarity(titleA, titleB);
        boolean sameYear = a.getPublication().getYear() == b.getPublication().getYear();
        return similarity >= 0.85 && sameYear;
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
    }
}
