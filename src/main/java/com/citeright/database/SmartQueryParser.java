package com.citeright.database;

/**
 * Parses smart queries like "year>=2020 tag:AI" into SQL WHERE clauses.
 */
public class SmartQueryParser {

    public static String parseToSqlWhere(String query) {
        if (query == null || query.isBlank()) {
            return "1=1"; // Match all if empty
        }

        StringBuilder sql = new StringBuilder();
        String[] tokens = query.split("\\s+");

        boolean first = true;
        for (String token : tokens) {
            if (!first) {
                sql.append(" AND ");
            }
            first = false;

            if (token.startsWith("year>=")) {
                String val = token.substring(6);
                sql.append("p.year >= ").append(val);
            } else if (token.startsWith("year<=")) {
                String val = token.substring(6);
                sql.append("p.year <= ").append(val);
            } else if (token.startsWith("year=")) {
                String val = token.substring(5);
                sql.append("p.year = ").append(val);
            } else if (token.startsWith("tag:")) {
                String val = token.substring(4);
                sql.append("EXISTS (SELECT 1 FROM paper_tags pt JOIN tags t ON pt.tag_id = t.id WHERE pt.paper_id = p.id AND t.name LIKE '%").append(val).append("%')");
            } else if (token.startsWith("author:")) {
                String val = token.substring(7);
                sql.append("EXISTS (SELECT 1 FROM paper_authors pa JOIN authors a ON pa.author_id = a.id WHERE pa.paper_id = p.id AND a.name LIKE '%").append(val).append("%')");
            } else {
                // Default to title search
                sql.append("p.title LIKE '%").append(token).append("%'");
            }
        }
        return sql.toString();
    }
}
