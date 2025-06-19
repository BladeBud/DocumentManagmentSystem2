package ruzicka.creators;

import ruzicka.databaseOprations.DatabaseConfig;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Adam
 * @since 2025-04-17
 */
public class DocCreator {
    //----Database connection---------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        // Use the DatabaseConfig class to get credentials
        return java.sql.DriverManager.getConnection(
                DatabaseConfig.getUrl(),
                DatabaseConfig.getUser(),
                DatabaseConfig.getPassword()
        );
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
     * @param attrTypes     List of attribute types
     *
     * @return The ID of the created document
     */
    public long createDocument(Integer idDocType, Blob docContent, String docFormat, List<String> docAttrValues,
                               List<String> docAttrNames, List<String> attrTypes) {

        if (docAttrValues.size() != docAttrNames.size() || docAttrValues.size() != attrTypes.size()) {
            throw new IllegalArgumentException("The number of attribute values, names, and types must match");
        }

        Connection conn = null; // Declare connection outside try to use in finally for AutoCommit
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Get the document name by executing the nameScript
            String docNameScriptResult = getDocumentName(conn, idDocType, docAttrValues, docAttrNames); // Pass conn

            // Insert into dm_doc and get the generated ID
            long idDoc;
            String insertSql = "INSERT INTO dm_doc (iddoctype, docname) VALUES (?, ?) RETURNING iddoc";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, idDocType);
                ps.setString(2, docNameScriptResult);
                try (ResultSet rs = ps.executeQuery()) { // Use ResultSet for RETURNING
                    if (rs.next()) {
                        idDoc = rs.getLong(1);
                    } else {
                        throw new SQLException("Failed to get generated document ID");
                    }
                }
            }

            // Insert document content
            insertDocContent(conn, idDoc, docContent, docFormat); // Pass conn and idDoc

            // Save all attribute values
            saveAllAttrVal(conn, idDocType, idDoc, docAttrValues, docAttrNames, attrTypes); // Pass conn and idDoc

            conn.commit();
            return idDoc; // Return the generated document ID

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
            }
            throw new RuntimeException("Failed to create document", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    // Log closing error
                }
            }
        }
    }
    //----Document deletion------------------------------------------------------------------------------------------------
    /**
     * Deletes a document and all its associated data (content, attribute values) from the database.
     *
     * @param conn  The active database connection from the calling transaction.
     * @param idDoc The ID of the document to delete.
     * @throws SQLException If a database error occurs.
     */
    public void deleteDocument(Connection conn, long idDoc) throws SQLException {
        // Delete from dm_docnode
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dm_docnode WHERE iddoc = ?")) {
            ps.setLong(1, idDoc);
            ps.executeUpdate();
        }

        // Delete from dm_docxpath
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dm_docxpath WHERE iddoc = ?")) {
            ps.setLong(1, idDoc);
            ps.executeUpdate();
        }

        // Delete attribute val
        try (PreparedStatement psStr = conn.prepareStatement("DELETE FROM dm_attrvaluestr WHERE iddoc = ?");
             PreparedStatement psLong = conn.prepareStatement("DELETE FROM dm_attrvaluelong WHERE iddoc = ?");
             PreparedStatement psDate = conn.prepareStatement("DELETE FROM dm_attrvaluedate WHERE iddoc = ?")) {

            psStr.setLong(1, idDoc);
            psStr.executeUpdate();

            psLong.setLong(1, idDoc);
            psLong.executeUpdate();

            psDate.setLong(1, idDoc);
            psDate.executeUpdate();
        }

        // Delete document content
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dm_doccontent WHERE iddoc = ?")) {
            ps.setLong(1, idDoc);
            ps.executeUpdate();
        }

        //delete the main document record from dm_doc
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dm_doc WHERE iddoc = ?")) {
            ps.setLong(1, idDoc);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                System.err.println("Warning: Document with idDoc " + idDoc + " not found in dm_doc for deletion.");
            }
        }
    }

    //----document change------------------------------------------------------------------------------------------------

    /**
     * Updates the document attributes. Not the content.
     *
     * @param idDoc         The document ID
     * @param idDocType     The document type ID
     * @param docAttrValues List of new attribute values
     * @param docAttrNames  List of attribute names to update
     * @param attrTypes     List of attribute types
     */
    public void updateDocument(long idDoc, Integer idDocType, List<String> docAttrValues, List<String> docAttrNames, List<String> attrTypes) {
        if (docAttrValues.size() != docAttrNames.size() || docAttrValues.size() != attrTypes.size()) {
            throw new IllegalArgumentException("The number of attribute values, names, and types must match");
        }
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            // Delete existing attribute values for this idDoc from DM_AttrValue* tables.
            // Then call saveAllAttrVal.
            saveAllAttrVal(conn, idDocType, idDoc, docAttrValues, docAttrNames, attrTypes);
            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
            }
            throw new RuntimeException("Failed to update document", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                }
            }
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
    private static void insertDocContent(Connection conn, long idDoc, Blob docContent, String docFormat) throws SQLException {
        String insertDocContentSql = "INSERT INTO dm_doccontent (iddoc, doccontent, docformat) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertDocContentSql)) {
            ps.setLong(1, idDoc);
            if (docContent != null) {
                ps.setBlob(2, docContent);
            } else {
                ps.setNull(2, java.sql.Types.BLOB);
            }
            ps.setString(3, docFormat);
            ps.executeUpdate();
        }
    }

    private void sortSaveAttrVal(Connection conn, long idDoc, int idDocTypeAttr, String attrType, String value) throws SQLException {
        String insertQuery;
        // Skip inserting if value is null or empty, or handle as per requirements
        if (value == null || value.isEmpty()) {
            // Or throw new IllegalArgumentException("Attribute value cannot be null or empty for " + attrType);
            // Depending on isRequired, this might be an issue. For now, skip.
            System.out.println("Skipping attribute with null/empty value for idDocTypeAttr: " + idDocTypeAttr);
            return;
        }

        switch (attrType.toLowerCase()) {
            case "string":
                insertQuery = "INSERT INTO DM_AttrValueStr (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);
                    ps.setString(3, value);
                    ps.executeUpdate();
                }
                break;
            case "date":
                insertQuery = "INSERT INTO DM_AttrValueDate (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);
                    try {
                        java.sql.Date sqlDate = java.sql.Date.valueOf(value); // Assumes "yyyy-MM-dd" format
                        ps.setDate(3, sqlDate);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid date format for value '" + value + "'. Expected yyyy-MM-dd.", e);
                    }
                    ps.executeUpdate();
                }
                break;
            case "long":
                insertQuery = "INSERT INTO DM_AttrValueLong (idDoc, idDocTypeAttr, Value) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setLong(1, idDoc);
                    ps.setInt(2, idDocTypeAttr);
                    try {
                        ps.setLong(3, Long.parseLong(value)); // Use Long.parseLong and setLong
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid long format for value '" + value + "'.", e);
                    }
                    ps.executeUpdate();
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported attribute type: " + attrType);
        }
    }

    private void saveAllAttrVal(Connection conn, Integer idDocType, long idDoc, List<String> docAttrValues,
                                List<String> docAttrNames, List<String> attrTypes) throws SQLException {
        for (int i = 0; i < docAttrNames.size(); i++) {
            // Pass conn to getAttributeIdByName
            int idDocTypeAttr = getAttributeIdByName(conn, idDocType, docAttrNames.get(i));
            String attrType = attrTypes.get(i);
            String attrValue = docAttrValues.get(i);

            sortSaveAttrVal(conn, idDoc, idDocTypeAttr, attrType, attrValue);
        }
    }

    public int getAttributeIdByName(Connection conn, int idDocType, String attrName) throws SQLException {
        String getAttrIdSql = "SELECT iddocattr FROM dm_docattr WHERE attrname = ?";
        int coreAttrId;
        try (PreparedStatement ps = conn.prepareStatement(getAttrIdSql)) {
            ps.setString(1, attrName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    coreAttrId = rs.getInt("iddocattr");
                } else {
                    throw new SQLException("Attribute core definition not found for name: " + attrName);
                }
            }
        }

        String getDocTypeAttrIdSql = "SELECT iddoctypeattr FROM dm_doctypeattr WHERE iddoctype = ? AND iddocattr = ?";
        try (PreparedStatement ps = conn.prepareStatement(getDocTypeAttrIdSql)) {
            ps.setInt(1, idDocType);
            ps.setInt(2, coreAttrId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("iddoctypeattr");
                } else {
                    throw new SQLException("Attribute '" + attrName + "' (core ID: " + coreAttrId +
                            ") is not associated with document type ID " + idDocType +
                            " in dm_doctypeattr table.");
                }
            }
        }
    }

    private String getDocumentName(Connection conn, Integer idDocType, List<String> docAttrValues,
                                   List<String> docAttrNames) throws SQLException {
        String nameScript = null;
        // Fetch the script using the provided connection
        try (PreparedStatement ps_fetch_script = conn.prepareStatement("SELECT docnamescript FROM dm_doctype WHERE iddoctype = ?")) {
            ps_fetch_script.setInt(1, idDocType);
            try (ResultSet rs = ps_fetch_script.executeQuery()) {
                if (rs.next()) {
                    nameScript = rs.getString("docnamescript");
                } else {
                    throw new SQLException("Document type not found: " + idDocType);
                }
            }
        }

        if (nameScript == null || nameScript.trim().isEmpty()) {
            throw new IllegalStateException("Name script is not defined or empty for document type: " + idDocType);
        }

        // System.out.println("DEBUG: Fetched nameScript for idDocType " + idDocType + " IS: [" + nameScript + "]");

        try (PreparedStatement ps_execute_script = conn.prepareStatement(nameScript)) {
            int expectedParams = 0;
            try {
                expectedParams = ps_execute_script.getParameterMetaData().getParameterCount();
            } catch (SQLException e) {
                long questionMarkCount = nameScript.chars().filter(ch -> ch == '?').count();
                if (expectedParams == 0 && questionMarkCount > 0) {
                    expectedParams = (int) questionMarkCount;
                }
            }


            if (docAttrValues.size() < expectedParams) {
                throw new SQLException("Name script expects " + expectedParams + " parameters, but only " +
                        docAttrValues.size() + " attribute values provided for name generation.");
            }

            for (int i = 0; i < expectedParams; i++) {
                ps_execute_script.setString(i + 1, docAttrValues.get(i));
            }

            try (ResultSet rs = ps_execute_script.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                } else {
                    throw new SQLException("Name script execution returned no results for script: " + nameScript);
                }
            }
        }
    }
}