package ruzicka.databaseOprations;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseManager {
    //----Database connection parameters--------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    //----Database connection-------------------------------------------------------------------------------------------
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
    //----Database operations-----------------------------------------------------------------------------------------------
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
     * Inserts a record into DM_DocNode, linking a document to a specific node index within a tree.
     *
     * @param conn    The database connection
     * @param idDoc   The document ID
     * @param ixNode  The index of the node in the ArrayTree for this tree instance
     * @param idTree  The tree ID
     * @throws SQLException
     */
    public void insertDocNode(Connection conn, long idDoc, int ixNode, int idTree) throws SQLException {
        String sql = "INSERT INTO dm_docnode (iddoc, ixnode, idtree) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDoc);
            ps.setInt(2, ixNode); // This is the finalNodeIdInArrayTree
            ps.setInt(3, idTree);
            ps.executeUpdate();
        }
    }

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

    public byte[] getTreeContent(Connection conn, int treeId) throws SQLException {
        String sql = "SELECT TreeContent FROM dm_tree WHERE idtree = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("TreeContent"); // For BYTEA
                }
            }
        }
        return null;
    }

    public void updateTree(Connection conn, int treeId, byte[] treeContent, int nextFreeNodeIndex) throws SQLException {
        String sql = "UPDATE dm_tree SET TreeContent = ?, ixFreeNode = ? WHERE idtree = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (treeContent != null && treeContent.length > 0) {
                ps.setBytes(1, treeContent); // For BYTEA
            } else {
                ps.setNull(1, java.sql.Types.BINARY); // Use BINARY or VARBINARY for bytea null
            }
            ps.setInt(2, nextFreeNodeIndex);
            ps.setInt(3, treeId);
            ps.executeUpdate();
        }
    }

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

    public void insertDocXPath(Connection conn, long idDoc, long idNodeXPath) throws SQLException {
        String sql = "INSERT INTO dm_docxpath (iddoc, idnodexpath) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDoc);
            ps.setLong(2, idNodeXPath);
            ps.executeUpdate();
        }
    }
}