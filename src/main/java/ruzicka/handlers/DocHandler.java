package ruzicka.handlers;

import ruzicka.creators.DocCreator;
import ruzicka.databaseOprations.DatabaseManager;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Adam
 * @since 2025-04-28
 */
public class DocHandler {
    //----Database connection parameters--------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";
    //----Database connection-------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
    //------------------------------------------------------------------------------------------------------------------
    private DatabaseManager dbManager;
    DocCreator docCreator = new DocCreator();
//----Document operations-----------------------------------------------------------------------------------------------
    //----Add document--------------------------------------------------------------------------------------------------
    public void addDocument(Integer idDocType, Blob docContent, String docFormat, List<String> docAttrValues,
                            List<String> docAttrNames, List<String> attrTypes) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Create the document and get its ID
                long idDoc = docCreator.createDocument(idDocType, docContent, docFormat, docAttrValues, docAttrNames, attrTypes);

                // Get all tree definitions
                String treeQuery = "SELECT idtree FROM dm_tree";
                try (PreparedStatement treeStmt = conn.prepareStatement(treeQuery)) {
                    var treeRs = treeStmt.executeQuery();
                    while (treeRs.next()) {
                        int treeId = treeRs.getInt("idtree");
                        addDocumentToTree(conn, idDoc, treeId);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to add document to trees", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error", e);
        }
    }

    private void addDocumentToTree(Connection conn, long idDoc, int treeId) throws SQLException {
        // Get all tree node definitions for this tree
        String nodeDefQuery = "SELECT iddefparenttreenode, docincludecondition FROM dm_deftreenode " +
                "WHERE idtree = ? AND docincludecondition != 'false' AND docincludecondition IS NOT NULL AND TRIM(docincludecondition) <> ''"; // Added more robust check for empty/null

        try (PreparedStatement nodeDefStmt = conn.prepareStatement(nodeDefQuery)) {
            nodeDefStmt.setInt(1, treeId);
            var nodeDefRs = nodeDefStmt.executeQuery();

            while (nodeDefRs.next()) {
                String includeCondition = nodeDefRs.getString("docincludecondition");
                int parentNodeId = nodeDefRs.getInt("iddefparenttreenode"); // This is iddefparenttreenode, not an actual node ID from ArrayTree

                // IMPORTANT: The includeCondition from the DB should be written carefully.
                // We assume it's a valid SQL boolean expression that can refer to a document aliased as 'd'.
                // Example: "EXISTS (SELECT 1 FROM dm_attrvaluestr avs ... WHERE avs.iddoc = d.iddoc AND ...)"
                String safeIncludeCondition = includeCondition.replaceAll("\\b(dm_doc|DM_DOC)\\.iddoc\\b", "d.iddoc");

                String checkSql = "SELECT EXISTS (SELECT 1 FROM dm_doc d WHERE d.iddoc = ? AND (" + safeIncludeCondition + "))";

                // For debugging the generated SQL:
                // System.out.println("Executing checkSql: " + checkSql + " with idDoc: " + idDoc);

                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setLong(1, idDoc);
                    var checkRs = checkStmt.executeQuery();

                    if (checkRs.next() && checkRs.getBoolean(1)) {
                        // Document matches condition, add it to this node (definition context)
                        addDocumentToNode(conn, idDoc, treeId, parentNodeId); // parentNodeId here is iddeftreenode
                    }
                }
            }
        }
    }

    private void addDocumentToNode(Connection conn, long idDoc, int treeId, int parentNodeId) throws SQLException {
        // Get the next free node index for this tree
        String ixQuery = "SELECT ixfreenode FROM dm_tree WHERE idtree = ? FOR UPDATE";
        int ixNode;

        try (PreparedStatement ixStmt = conn.prepareStatement(ixQuery)) {
            ixStmt.setInt(1, treeId);
            var ixRs = ixStmt.executeQuery();
            if (ixRs.next()) {
                ixNode = ixRs.getInt(1);

                // Update the free node index
                try (PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE dm_tree SET ixfreenode = ixfreenode + 1 WHERE idtree = ?")) {
                    updateStmt.setInt(1, treeId);
                    updateStmt.executeUpdate();
                }
            } else {
                throw new SQLException("Tree not found: " + treeId);
            }
        }

        // Insert the document node
        String insertSql = "INSERT INTO dm_docnode (iddoc, ixnode, idtree) VALUES (?, ?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setLong(1, idDoc);
            insertStmt.setInt(2, ixNode);
            insertStmt.setInt(3, treeId);
            insertStmt.executeUpdate();
        }
    }

}
