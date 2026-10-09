package com.citeright.database;

import com.citeright.model.Annotation;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AnnotationDAO {
    public Annotation save(Annotation a) {
        String sql = "INSERT INTO annotations (pdf_id, type, page_number, x, y, width, height, content, color, stroke_data) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return a;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, a.getPdfId());
                ps.setString(2, a.getTypeString());
                ps.setInt(3, a.getPageNumber());
                ps.setDouble(4, a.getX());
                ps.setDouble(5, a.getY());
                ps.setDouble(6, a.getWidth());
                ps.setDouble(7, a.getHeight());
                ps.setString(8, a.getContent());
                ps.setString(9, a.getColor());
                ps.setString(10, a.getStrokeData());
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) a.setId(rs.getInt(1));
            }
        } catch (SQLException e) { System.err.println("[AnnotationDAO] save error: " + e.getMessage()); }
        return a;
    }

    public List<Annotation> getByPdfId(int pdfId) {
        List<Annotation> list = new ArrayList<>();
        String sql = "SELECT * FROM annotations WHERE pdf_id = ? ORDER BY page_number, y";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return list;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pdfId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { System.err.println("[AnnotationDAO] getByPdfId error: " + e.getMessage()); }
        return list;
    }

    public List<Annotation> getByPage(int pdfId, int page) {
        List<Annotation> list = new ArrayList<>();
        String sql = "SELECT * FROM annotations WHERE pdf_id = ? AND page_number = ? ORDER BY y";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return list;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pdfId); ps.setInt(2, page);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { System.err.println("[AnnotationDAO] getByPage error: " + e.getMessage()); }
        return list;
    }

    public void delete(int id) {
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM annotations WHERE id = ?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
        } catch (SQLException e) { System.err.println("[AnnotationDAO] delete error: " + e.getMessage()); }
    }

    public void update(Annotation a) {
        String sql = "UPDATE annotations SET content = ?, color = ?, x = ?, y = ?, width = ?, height = ? WHERE id = ?";
        try (Connection conn = SQLiteDatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, a.getContent()); ps.setString(2, a.getColor());
                ps.setDouble(3, a.getX()); ps.setDouble(4, a.getY());
                ps.setDouble(5, a.getWidth()); ps.setDouble(6, a.getHeight());
                ps.setInt(7, a.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) { System.err.println("[AnnotationDAO] update error: " + e.getMessage()); }
    }

    private Annotation mapRow(ResultSet rs) throws SQLException {
        Annotation a = new Annotation();
        a.setId(rs.getInt("id"));
        a.setPdfId(rs.getInt("pdf_id"));
        a.setType(rs.getString("type"));
        a.setPageNumber(rs.getInt("page_number"));
        a.setX(rs.getDouble("x")); a.setY(rs.getDouble("y"));
        a.setWidth(rs.getDouble("width")); a.setHeight(rs.getDouble("height"));
        a.setContent(rs.getString("content"));
        a.setColor(rs.getString("color"));
        try { a.setStrokeData(rs.getString("stroke_data")); } catch (SQLException ignored) {}
        return a;
    }
}
