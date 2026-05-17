import com.citeright.service.LibraryService;
import com.citeright.model.JournalArticle;
import java.sql.*;
import java.io.File;

public class DumpDB {
    public static void main(String[] args) throws Exception {
        LibraryService ls = new LibraryService();
        JournalArticle paper = new JournalArticle();
        paper.setTitle("My Dummy Paper " + System.currentTimeMillis());
        paper.setYear(2025);
        paper.setDoi("10.1234/dummy");
        
        System.out.println("Saving...");
        ls.saveToDefaultCollection(paper);
        System.out.println("Saved!");

        String dbPath = System.getProperty("user.home") + "/.citeright/library.db";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT ul.* FROM user_library ul ORDER BY ul.id DESC LIMIT 5")) {
                while (rs.next()) {
                    System.out.println("ul_id=" + rs.getInt("id") + ", paper_id=" + rs.getInt("paper_id") + ", is_deleted=" + rs.getInt("is_deleted"));
                }
            }
        }
    }
}
