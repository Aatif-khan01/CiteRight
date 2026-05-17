package com.citeright.service;

import com.citeright.database.*;
import com.citeright.model.*;
import com.citeright.model.LibraryEntry.ReadStatus;
import java.util.List;

/**
 * Facade for all library operations.
 * Provides a simple interface to manage the user's personal paper library.
 * 
 * Demonstrates: FACADE PATTERN
 */
public class LibraryService {

    private final LibraryDAO libraryDAO;
    private final CollectionDAO collectionDAO;
    private final TagDAO tagDAO;

    public LibraryService() {
        this.libraryDAO = new LibraryDAO();
        this.collectionDAO = new CollectionDAO();
        this.tagDAO = new TagDAO();
    }

    // === Library Operations ===

    public void saveToLibrary(Publication paper, int collectionId) {
        libraryDAO.saveToLibrary(paper, collectionId);
    }

    public void saveToDefaultCollection(Publication paper) {
        List<Collection> collections = collectionDAO.getAll();
        int defaultId = collections.isEmpty() ? 1 : collections.get(0).getId();
        libraryDAO.saveToLibrary(paper, defaultId);
    }

    public void removeFromLibrary(int paperId) {
        libraryDAO.removeFromLibrary(paperId);
    }

    public boolean isInLibrary(String paperId) {
        return libraryDAO.isInLibraryByPaperId(paperId);
    }

    public List<LibraryEntry> getLibraryPapers(Integer collectionId, String readStatus, Boolean favoritesOnly) {
        if (collectionId != null) {
            Collection c = collectionDAO.getById(collectionId);
            if (c != null && c.isSmart()) {
                return libraryDAO.getSmartCollectionPapers(c.getSmartQuery(), readStatus, favoritesOnly);
            }
        }
        return libraryDAO.getAll(collectionId, readStatus, favoritesOnly);
    }

    public List<LibraryEntry> getAllLibraryPapers() {
        return libraryDAO.getAll(null, null, null);
    }

    public int getLibraryCount() {
        return libraryDAO.getLibraryCount();
    }

    public int getDbPaperId(String paperId) {
        return libraryDAO.getDbPaperId(paperId);
    }

    // === Favorites & Read Status ===

    public void toggleFavorite(int paperId) {
        libraryDAO.toggleFavorite(paperId);
    }

    public void updateReadStatus(int paperId, ReadStatus status) {
        libraryDAO.updateReadStatus(paperId, status);
    }

    public void updateNotes(int paperId, String notes) {
        libraryDAO.updateNotes(paperId, notes);
    }

    public void updateMetadata(int paperId, Publication paper) {
        libraryDAO.updateMetadata(paperId, paper);
    }

    public void moveToCollection(int paperId, int newCollectionId) {
        libraryDAO.moveToCollection(paperId, newCollectionId);
    }

    // === Collection Operations ===

    public Collection createCollection(String name, String color) {
        return collectionDAO.create(new Collection(name, color));
    }

    public Collection createCollection(String name) {
        return collectionDAO.create(new Collection(name));
    }

    public List<Collection> getCollections() {
        return collectionDAO.getAll();
    }

    public void renameCollection(int id, String newName) {
        collectionDAO.rename(id, newName);
    }

    public void deleteCollection(int id) {
        collectionDAO.delete(id);
    }

    // === Tag Operations ===

    public Tag createTag(String name, String color) {
        return tagDAO.create(new Tag(name, color));
    }

    public Tag createTag(String name) {
        return tagDAO.create(new Tag(name));
    }

    public List<Tag> getAllTags() {
        return tagDAO.getAll();
    }

    public List<Tag> getTagsForPaper(int paperId) {
        return tagDAO.getTagsForPaper(paperId);
    }

    public void addTagToPaper(int paperId, int tagId) {
        tagDAO.addTagToPaper(paperId, tagId);
    }

    public void removeTagFromPaper(int paperId, int tagId) {
        tagDAO.removeTagFromPaper(paperId, tagId);
    }

    public void deleteTag(int tagId) {
        tagDAO.delete(tagId);
    }

    // === Smart Folder Queries ===

    public List<LibraryEntry> getRecentlyAdded() { return libraryDAO.getRecentlyAdded(7); }
    public List<LibraryEntry> getRecentlyRead() { return libraryDAO.getRecentlyRead(7); }
    public List<LibraryEntry> getMyPublications() { return libraryDAO.getMyPublications(); }
    public List<LibraryEntry> getUnsorted() { return libraryDAO.getUnsorted(); }
    public List<LibraryEntry> getFavorites() { return libraryDAO.getAll(null, null, true); }
    public List<LibraryEntry> getTrashed() { return libraryDAO.getTrashed(); }
    public List<LibraryEntry> getAllActive() { return libraryDAO.getAllActive(); }

    /** Returns groups of duplicate library entries */
    public List<List<LibraryEntry>> getDuplicateGroups() {
        DuplicateDetectionService dds = new DuplicateDetectionService();
        return dds.findDuplicates(getAllActive());
    }

    /** Returns a flat list of all entries that have at least one duplicate */
    public List<LibraryEntry> getDuplicates() {
        List<LibraryEntry> dupes = new java.util.ArrayList<>();
        for (List<LibraryEntry> group : getDuplicateGroups()) {
            dupes.addAll(group);
        }
        return dupes;
    }

    /** Returns all active publications (used for export) */
    public List<Publication> getAllActivePublications() {
        List<Publication> pubs = new java.util.ArrayList<>();
        for (LibraryEntry e : getAllActive()) {
            if (e.getPublication() != null) pubs.add(e.getPublication());
        }
        return pubs;
    }

    // === Trash Operations ===

    public void softDelete(int paperId) { libraryDAO.softDelete(paperId); }
    public void restore(int paperId) { libraryDAO.restore(paperId); }
    public void permanentDelete(int paperId) { libraryDAO.permanentDelete(paperId); }
    public void markAsMyPublication(int paperId, boolean isMine) { libraryDAO.markAsMyPublication(paperId, isMine); }
    public void updateLastReadAt(int paperId) { libraryDAO.updateLastReadAt(paperId); }
}
