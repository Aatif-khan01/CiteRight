package com.citeright.database;

import com.citeright.model.PdfFile;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PdfDAO {
    public PdfFile save(PdfFile pdf) {
        String sql = "INSERT INTO pdf_files (paper_id, file_path, file_name, file_size, page_count) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return pdf;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, pdf.getPaperId());
                ps.setString(2, pdf.getFilePath());
                ps.setString(3, pdf.getFileName());
                ps.setLong(4, pdf.getFileSize());
                ps.setInt(5, pdf.getPageCount());
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) pdf.setId(rs.getInt(1));
            }
        } catch (SQLException e) { System.err.println("[PdfDAO] save error: " + e.getMessage()); }
        return pdf;
    }

    public PdfFile getByPaperId(int paperId) {
        String sql = "SELECT * FROM pdf_files WHERE paper_id = ? LIMIT 1";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, paperId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) { System.err.println("[PdfDAO] getByPaperId error: " + e.getMessage()); }
        return null;
    }

    public List<PdfFile> getAll() {
        List<PdfFile> list = new ArrayList<>();
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return list;
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM pdf_files"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { System.err.println("[PdfDAO] getAll error: " + e.getMessage()); }
        return list;
    }

    public void delete(int id) {
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pdf_files WHERE id = ?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
        } catch (SQLException e) { System.err.println("[PdfDAO] delete error: " + e.getMessage()); }
    }

    private PdfFile mapRow(ResultSet rs) throws SQLException {
        PdfFile pdf = new PdfFile();
        pdf.setId(rs.getInt("id"));
        pdf.setPaperId(rs.getInt("paper_id"));
        pdf.setFilePath(rs.getString("file_path"));
        pdf.setFileName(rs.getString("file_name"));
        pdf.setFileSize(rs.getLong("file_size"));
        pdf.setPageCount(rs.getInt("page_count"));
        return pdf;
    }
}
