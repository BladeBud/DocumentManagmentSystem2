package ruzicka.databaseOprations;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Manages all direct database interactions for document management system entities.
 * Methods in this class that are part of a larger transaction should accept a {@link Connection} parameter.
 * Methods that can operate independently can acquire their own connection.
 */
public class DatabaseManager {
    //----Database connection parameters--------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    /**
     * Gets a new database connection. Used by methods not participating in an existing transaction.
     * @return A new database connection.
     * @throws SQLException If a database access error occurs.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Saves a node name to the DM_NodeName table. If the name exists, returns its ID; otherwise, inserts and returns the new ID.
     * This method is expected to be called within an existing transaction.
     * @param conn The active database connection.
     * @param nodeName The name of the node.
     * @return The ID (idNodeName) of the node name.
     * @throws SQLException If a database access error occurs.
     */
    public long saveNodeName(Connection conn, String nodeName) throws SQLException {
        String selectSql = "SELECT idnodename FROM dm_nodename WHERE nodename = ?";
        String insertSql = "INSERT INTO dm_nodename (nodename) VALUES (?) RETURNING idnodename";

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, nodeName);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("idnodename");
                }
            }
        }
        // If not found, insert
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, nodeName);
            try (ResultSet generatedKeys = insertStmt.executeQuery()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong("idnodename");
                } else {
                    throw new SQLException("Failed to insert node name '" + nodeName + "', no ID obtained.");
                }
            }
        }
    }

    /**
     * Retrieves a node name by its ID from DM_NodeName.
     * This method is expected to be called within an existing transaction.
     * @param conn The active database connection.
     * @param idNodeName The ID of the node name.
     * @return The node name string.
     * @throws SQLException If the node name is not found or a database error occurs.
     */
    public String getNodeNameById(Connection conn, long idNodeName) throws SQLException {
        String selectSql = "SELECT nodename FROM dm_nodename WHERE idnodename = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setLong(1, idNodeName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nodename");
                } else {
                    throw new SQLException("Node name not found for ID: " + idNodeName);
                }
            }
        }
    }

    /**
     * Saves a node XPath to the DM_NodeXPath table. If the XPath exists, returns its ID; otherwise, inserts and returns the new ID.
     * This method is expected to be called within an existing transaction.
     * @param conn The active database connection.
     * @param nodeXPath The XPath string of the node.
     * @return The ID (idNodeXPath) of the node XPath.
     * @throws SQLException If a database access error occurs.
     */
    public long saveNodeXPath(Connection conn, String nodeXPath) throws SQLException {
        String selectSql = "SELECT idnodexpath FROM dm_nodexpath WHERE nodexpath = ?";
        String insertSql = "INSERT INTO dm_nodexpath (nodexpath) VALUES (?) RETURNING idnodexpath";

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, nodeXPath);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("idnodexpath");
                }
            }
        }
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, nodeXPath);
            try (ResultSet generatedKeys = insertStmt.executeQuery()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong("idnodexpath");
                } else {
                    throw new SQLException("Failed to insert XPath '" + nodeXPath + "', no ID obtained.");
                }
            }
        }
    }

    /**
     * Retrieves a node XPath by its ID from DM_NodeXPath.
     * This method is expected to be called within an existing transaction.
     * @param conn The active database connection.
     * @param idNodeXPath The ID of the node XPath.
     * @return The node XPath string.
     * @throws SQLException If the node XPath is not found or a database error occurs.
     */
    public String getNodeXPathById(Connection conn, long idNodeXPath) throws SQLException {
        String selectSql = "SELECT nodexpath FROM dm_nodexpath WHERE idnodexpath = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setLong(1, idNodeXPath);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nodexpath");
                } else {
                    throw new SQLException("Node XPath not found for ID: " + idNodeXPath);
                }
            }
        }
    }

    /**
     * Retrieves the TreeContent (as byte array) from DM_Tree for a given tree ID.
     * Assumes DM_Tree.TreeContent is of type BYTEA.
     * @param conn The active database connection.
     * @param treeId The ID of the tree.
     * @return The tree content as a byte array, or null if not found or content is null.
     * @throws SQLException If a database access error occurs.
     */
    public byte[] getTreeContent(Connection conn, int treeId) throws SQLException {
        String sql = "SELECT TreeContent FROM dm_tree WHERE idtree = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("TreeContent");
                }
            }
        }
        return null;
    }

    /**
     * Updates the TreeContent (BYTEA) and ixFreeNode in the DM_Tree table.
     * @param conn The active database connection.
     * @param treeId The ID of the tree to update.
     * @param treeContent The new tree content as a byte array.
     * @param nextFreeNodeIndex The new free node index for the ArrayTree.
     * @throws SQLException If a database access error occurs.
     */
    public void updateTree(Connection conn, int treeId, byte[] treeContent, int nextFreeNodeIndex) throws SQLException {
        String sql = "UPDATE dm_tree SET TreeContent = ?, ixFreeNode = ? WHERE idtree = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (treeContent != null && treeContent.length > 0) {
                ps.setBytes(1, treeContent);
            } else {
                ps.setNull(1, java.sql.Types.BINARY); // Or VARBINARY, suitable for bytea
            }
            ps.setInt(2, nextFreeNodeIndex);
            ps.setInt(3, treeId);
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves the ixFreeNode index from DM_Tree for a given tree ID.
     * @param conn The active database connection.
     * @param treeId The ID of the tree.
     * @return The ixFreeNode value.
     * @throws SQLException If the tree is not found or a database error occurs.
     */
    public int getTreeNextFreeNodeIndex(Connection conn, int treeId) throws SQLException {
        String sql = "SELECT ixFreeNode FROM dm_tree WHERE idtree = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ixFreeNode");
                } else {
                    throw new SQLException("Tree not found with idTree: " + treeId + " when fetching ixFreeNode.");
                }
            }
        }
    }

    /**
     * Inserts a record into DM_DocXPath, linking a document to its node XPath representation.
     * @param conn The active database connection.
     * @param idDoc The ID of the document.
     * @param idNodeXPath The ID of the node XPath.
     * @throws SQLException If a database access error occurs.
     */
    public void insertDocXPath(Connection conn, long idDoc, long idNodeXPath) throws SQLException {
        String sql = "INSERT INTO dm_docxpath (iddoc, idnodexpath) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDoc);
            ps.setLong(2, idNodeXPath);
            ps.executeUpdate();
        }
    }

    /**
     * Inserts a record into DM_DocNode, linking a document to a specific node index within a tree structure.
     * @param conn The active database connection.
     * @param idDoc The ID of the document.
     * @param ixNode The index of the node in the ArrayTree for this tree instance.
     * @param idTree The ID of the tree.
     * @throws SQLException If a database access error occurs.
     */
    public void insertDocNode(Connection conn, long idDoc, int ixNode, int idTree) throws SQLException {
        String sql = "INSERT INTO dm_docnode (iddoc, ixnode, idtree) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDoc);
            ps.setInt(2, ixNode);
            ps.setInt(3, idTree);
            ps.executeUpdate();
        }
    }
}