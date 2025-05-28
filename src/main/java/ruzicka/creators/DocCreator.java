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
     * @param idDocType     The document type ID
     * @param docContent    The document content as a Blob
     * @param docFormat     The document format
     * @param docAttrValues List of attribute values
     * @param docAttrNames  List of attribute names
     */
    public long createDocument(Integer idDocType, Blob docContent, String docFormat, List<String> docAttrValues,
                               List<String> docAttrNames, List<String> attrTypes) {

        if (docAttrValues.size() != docAttrNames.size() || docAttrValues.size() != attrTypes.size()) {
            throw new IllegalArgumentException("The number of attribute values, names, and types must match");
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert into dm_doc and get the generated ID
                long idDoc;
                // Get the document name by executing the nameScript
                String docNameScriptResult = getDocumentName(idDocType, docAttrValues, docAttrNames);

                String insertSql = "INSERT INTO dm_doc (iddoctype, docname) VALUES (?, ?) RETURNING iddoc";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, idDocType);
                    ps.setString(2, docNameScriptResult);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idDoc = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to get generated document ID");
                        }
                    }
                }

                // Insert document content
                insertDocContent(docContent, docFormat, conn, idDoc);

                // Save all attribute values
                saveAllAttrVal(idDocType, docAttrValues, docAttrNames, attrTypes, conn, idDoc);

                // Commit the transaction
                conn.commit();

                return idDoc; // Return the generated document ID
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
    //----Document deletion------------------------------------------------------------------------------------------------
//TODO:kdyz budu delteovat document tak tady smazu jen samostatny dokument nekde je treba mit helper na kontrolu/mazani nodu ktery byudou prazdny
    /*
     * Deletes a document from all nodes in the tree. Without toucing the nodes
     *
     * @param idDoc The document ID to delete
     */
    public void deleteDocument(long idDoc) {
        String deleteSql = "DELETE FROM dm_doc WHERE iddoc = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setLong(1, idDoc);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    //----document change------------------------------------------------------------------------------------------------
//TODO: pridat pak node strom update, todle meni jen document a ne node

    /**
     * Updates the document attributes. Not the content.
     *
     * @param idDoc         The document ID
     * @param docAttrValues List of new attribute values
     * @param docAttrNames  List of attribute names to update
     */
    public void updateDocument(long idDoc, Integer idDocType, List<String> docAttrValues, List<String> docAttrNames, List<String> attrTypes) {

        if (docAttrValues.size() != docAttrNames.size() || docAttrValues.size() != attrTypes.size()) {
            throw new IllegalArgumentException("The number of attribute values, names, and types must match");
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Update all attribute values
                saveAllAttrVal(idDocType, docAttrValues, docAttrNames, attrTypes, conn, idDoc);
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to update document", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error", e);
        }
    }
    //----Document content update------------------------------------------------------------------------------------------------

    /**
     * Updates the document content without touching anything else.
     *
     * @param idDoc      The document ID
     * @param docContent The new document content as a Blob
     * @param docFormat  The new document format
     */
    public void updateDocumentContent(long idDoc, Blob docContent, String docFormat) {
        String updateDocContentSql = "UPDATE dm_doccontent SET doccontent = ?, docformat = ? WHERE iddoc = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(updateDocContentSql)) {
            ps.setBlob(1, docContent);
            ps.setString(2, docFormat);
            ps.setLong(3, idDoc);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update document content", e);
        }
    }

//----Helper methods----------------------------------------------------------------------------------------------------
    //----Insert document content----------------------------------------------------------------------------------------

    /**
     * Inserts the document content into the database.
     *
     * @param docContent The document content as a Blob
     * @param docFormat  The document format
     * @param conn       The database connection
     * @param idDoc      The document ID
     *
     * @throws SQLException If an error occurs while accessing the database
     */
    private static void insertDocContent(Blob docContent, String docFormat, Connection conn, long idDoc) throws SQLException {
        String insertDocContentSql = "INSERT INTO dm_doccontent (iddoc, doccontent, docformat) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertDocContentSql)) {
            ps.setLong(1, idDoc);
            ps.setBlob(2, docContent);
            ps.setString(3, docFormat);
            ps.executeUpdate();
        }
    }
    //----Sort and Save attribute value------------------------------------------------------------------------------------------

    /**
     * Saves the attribute value to the appropriate table based on its type.
     *
     * @param conn          The database connection
     * @param idDoc         The document ID
     * @param idDocTypeAttr The document type attribute ID
     * @param attrType      The attribute type (string, date, int)
     * @param value         The attribute value
     *
     * @throws SQLException If an error occurs while accessing the database
     */
    private void sortSaveAttrVal(Connection conn, long idDoc, int idDocTypeAttr, String attrType, String value) throws SQLException {
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
            case "long" -> {
                insertQuery = "INSERT INTO DM_AttrValueLong (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);
                    ps.setLong(3, Long.parseLong(value)); // Convert string to long and use setLong
                    ps.executeUpdate();
                }
            }
            default -> throw new IllegalArgumentException("Unsupported attribute type: " + attrType);
        }
    }
    //----Save all attribute values-------------------------------------------------------------------------------------
    /**
     * Saves all attribute values for the document.
     *
     * @param idDocType     The document type ID
     * @param docAttrValues List of attribute values
     * @param docAttrNames  List of attribute names
     * @param attrTypes     List of attribute types
     * @param conn          The database connection
     * @param idDoc         The document ID
     *
     * @throws SQLException If an error occurs while accessing the database
     */

    private void saveAllAttrVal(Integer idDocType, List<String> docAttrValues, List<String> docAttrNames, List<String> attrTypes, Connection conn, long idDoc) throws SQLException {
        for (int i = 0; i < docAttrNames.size(); i++) {
            int idDocTypeAttr = getAttributeIdByName(conn, idDocType, docAttrNames.get(i));
            String attrType = attrTypes.get(i);
            String attrValue = docAttrValues.get(i);

            sortSaveAttrVal(conn, idDoc, idDocTypeAttr, attrType, attrValue);
        }
    }

    //----get attribute id by name--------------------------------------------------------------------------------------

    /**
     * Retrieves the attribute ID by its name.
     *
     * @param conn      The database connection
     * @param idDocType The document type ID
     * @param attrName  The attribute name
     *
     * @return The attribute ID
     *
     * @throws SQLException If an error occurs while accessing the database
     */
    /**
     * Retrieves the document type attribute ID (iddoctypeattr) by its name and document type.
     *
     * @param conn      The database connection
     * @param idDocType The document type ID
     * @param attrName  The attribute name
     *
     * @return The document type attribute ID (iddoctypeattr)
     *
     * @throws SQLException If an error occurs while accessing the database or if attribute/association not found
     */
    public int getAttributeIdByName(Connection conn, int idDocType, String attrName) throws SQLException {
        // Step 1: Get idDocAttr from dm_docattr using attrName
        String getAttrIdSql = "SELECT iddocattr FROM dm_docattr WHERE attrname = ?";
        int idDocAttrValue; // Renamed to avoid conflict with idDocAttr in scope for table name
        try (PreparedStatement ps = conn.prepareStatement(getAttrIdSql)) {
            ps.setString(1, attrName);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    idDocAttrValue = rs.getInt("iddocattr");
                } else {
                    throw new SQLException("Attribute core definition not found for name: " + attrName);
                }
            }
        }

        // Step 2: Get iddoctypeattr from dm_doctypeattr using idDocType and idDocAttrValue
        String getDocTypeAttrIdSql = "SELECT iddoctypeattr FROM dm_doctypeattr WHERE iddoctype = ? AND iddocattr = ?";
        try (PreparedStatement ps = conn.prepareStatement(getDocTypeAttrIdSql)) {
            ps.setInt(1, idDocType);
            ps.setInt(2, idDocAttrValue);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("iddoctypeattr");
                } else {
                    throw new SQLException("Attribute '" + attrName + "' (core ID: " + idDocAttrValue +
                            ") is not associated with document type ID " + idDocType +
                            " in dm_doctypeattr table.");
                }
            }
        }
    }
    //----get document name by executing the nameScript-----------------------------------------------------------------

    /**
     * Gets document name by executing the nameScript for the given document type
     *
     * @param idDocType     The document type ID
     * @param docAttrValues List of attribute values
     * @param docAttrNames  List of attribute names
     *
     * @return Generated document name
     */
    private String getDocumentName(Integer idDocType, List<String> docAttrValues, List<String> docAttrNames) throws SQLException {
        String nameScript = null;

        // First, get the nameScript for the document type
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT docnamescript FROM dm_doctype WHERE iddoctype = ?")) {
            ps.setInt(1, idDocType);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    nameScript = rs.getString("docnamescript");
                } else {
                    throw new SQLException("Document type not found: " + idDocType);
                }
            }
        }

        if (nameScript == null || nameScript.isEmpty()) {
            throw new IllegalStateException("Name script is not defined for document type: " + idDocType);
        }

        // Execute the nameScript query
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(nameScript)) {

            // For each attribute in docAttrNames, set its value in the prepared statement
            for (int i = 0; i < docAttrNames.size(); i++) {
                ps.setString(i + 1, docAttrValues.get(i));
            }

            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1); // Return the first column of the result
                } else {
                    throw new SQLException("Name script execution returned no results");
                }
            }
        }
    }
}
