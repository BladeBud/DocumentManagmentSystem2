package ruzicka.creators;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Adam
 * @since 2025-04-17
 */
public class DocCreator {
    //----Database connection parameters----------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    //----Database connection---------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    //----Document creation------------------------------------------------------------------------------------------------
    /**
     * Creates a new document in the database.
     *
     * @param idDocType The document type ID
     * @param docNameScript The document name
     * @param docContent The document content as a Blob
     * @param docFormat The document format
     * @param docAttrValues List of attribute values
     * @param docAttrNames List of attribute names
     */
    public void createDocument(Integer idDocType, String docNameScript, Blob docContent, String docFormat,
                               List<String> docAttrValues, List<String> docAttrNames, List<String> attrTypes) {

        if (docAttrValues.size() != docAttrNames.size() || docAttrValues.size() != attrTypes.size()) {
            throw new IllegalArgumentException("The number of attribute values, names, and types must match");
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert into dm_doc and get the generated ID
                long idDoc;
                String insertSql = "INSERT INTO dm_doc (iddoctype, docname) VALUES (?, ?) RETURNING iddoc";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, idDocType);
                    ps.setString(2, docNameScript);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idDoc = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to get generated document ID");
                        }
                    }
                }

                // Insert document content
                String insertDocContentSql = "INSERT INTO dm_doccontent (iddoc, doccontent, docformat) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertDocContentSql)) {
                    ps.setLong(1, idDoc);
                    ps.setBlob(2, docContent);
                    ps.setString(3, docFormat);
                    ps.executeUpdate();
                }

                // Save all attribute values
                for (int i = 0; i < docAttrNames.size(); i++) {
                    int idDocTypeAttr = getAttributeIdByName(conn, idDocType, docAttrNames.get(i));
                    String attrType = attrTypes.get(i);
                    String attrValue = docAttrValues.get(i);

                    saveAttributeValue(conn, idDoc, idDocTypeAttr, attrType, attrValue);
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to create document", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error", e);
        }
    }


    //----Helper methods---------------------------------------------------------------------------------------------
    private void saveAttributeValue(Connection conn, long idDoc, int idDocTypeAttr, String attrType, String value) throws SQLException {
        String insertQuery;

        switch (attrType.toLowerCase()) {
            case "string" -> {
                insertQuery = "INSERT INTO DM_AttrValueStr (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);
                    ps.setString(3, value); // Directly use the string
                    ps.executeUpdate();
                }
            }
            case "date" -> {
                insertQuery = "INSERT INTO DM_AttrValueDate (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);

                    // Parse input string into LocalDate and convert to java.sql.Date
                    try {
                        java.sql.Date sqlDate = java.sql.Date.valueOf(value); // Assumes "yyyy-MM-dd" format
                        ps.setDate(3, sqlDate);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid date format. Expected format: yyyy-MM-dd. Input: " + value, e);
                    }

                    ps.executeUpdate();
                }
            }
            case "int" -> {
                insertQuery = "INSERT INTO DM_AttrValueLong (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);
                    ps.setInt(3, Integer.parseInt(value)); // Convert string to int
                    ps.executeUpdate();
                }
            }
            default -> throw new IllegalArgumentException("Unsupported attribute type: " + attrType);
        }
    }
    //--------------------------------------------------------------------------------------------
    public int getAttributeIdByName(Connection conn, int idDocType, String attrName) throws SQLException {
        String sql = "SELECT iddocattr FROM dm_docattr WHERE  attrname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attrName);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("iddocattr");
                } else {
                    throw new SQLException("Attribute not found: " + attrName);
                }
            }
        }
    }
}
