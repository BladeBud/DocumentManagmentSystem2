package ruzicka.databaseOprations;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Adam
 * @since 2025-04-28
 */
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
    //----Save node name------------------------------------------------------------------------------------------------
    /**
     * Saves a node name to the database. If the node name already exists, it returns the existing ID.
     *
     * @param nodeName The name of the node to save.
     * @return The ID of the saved or existing node name.
     */
    public long saveNodeName(String nodeName) {
        String selectSql = "SELECT idNodeName FROM DM_NodeName WHERE nodeName = ?";
        String insertSql = "INSERT INTO DM_NodeName (nodeName) VALUES (?) RETURNING idNodeName";

        try (Connection conn = getConnection();
             var selectStmt = conn.prepareStatement(selectSql);
             var insertStmt = conn.prepareStatement(insertSql)) {

            selectStmt.setString(1, nodeName);
            var rs = selectStmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("idNodeName");
            } else {
                insertStmt.setString(1, nodeName);
                var generatedKeys = insertStmt.executeQuery();
                if (generatedKeys.next()) {
                    return generatedKeys.getLong("idNodeName");
                } else {
                    throw new SQLException("Failed to insert node name, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving node name to database.", e);
        }
    }
    //----Get node name by ID-------------------------------------------------------------------------------------------
    /**
     * Retrieves a node name from the database by its ID.
     *
     * @param idNodeName The ID of the node name to retrieve.
     * @return The name of the node.
     */
    public String getNodeNameById(long idNodeName) {
        String selectSql = "SELECT nodeName FROM DM_NodeName WHERE idNodeName = ?";
        try (Connection conn = getConnection();
             var pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setLong(1, idNodeName);
            var rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("nodeName");
            throw new IllegalArgumentException("Node name not found for ID: " + idNodeName);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching node name from database.", e);
        }
    }
    //----Save node XPath-----------------------------------------------------------------------------------------------
    /**
     * Saves a node XPath to the database. If the XPath already exists, it returns the existing ID.
     *
     * @param nodeXPath The XPath of the node to save.
     * @return The ID of the saved or existing node XPath.
     */
    public long saveNodeXPath(String nodeXPath) {
        String selectSql = "SELECT idNodeXPath FROM DM_NodeXPath WHERE nodeXPath = ?";
        String insertSql = "INSERT INTO DM_NodeXPath (nodeXPath) VALUES (?) RETURNING idNodeXPath";

        try (Connection conn = getConnection();
             var selectStmt = conn.prepareStatement(selectSql);
             var insertStmt = conn.prepareStatement(insertSql)) {

            selectStmt.setString(1, nodeXPath);
            var rs = selectStmt.executeQuery();
            if (rs.next()) return rs.getLong("idNodeXPath");

            insertStmt.setString(1, nodeXPath);
            var generatedKeys = insertStmt.executeQuery();
            if (generatedKeys.next()) return generatedKeys.getLong("idNodeXPath");
            throw new SQLException("Failed to insert XPath, no ID obtained.");
        } catch (SQLException e) {
            throw new RuntimeException("Error saving XPath to database: " + e.getMessage(), e);
        }
    }
    //----Get node XPath by ID----------------------------------------------------------------------------------------
    /**
     * Retrieves a node XPath from the database by its ID.
     *
     * @param idNodeXPath The ID of the node XPath to retrieve.
     * @return The XPath of the node.
     */
    public String getNodeXPathById(long idNodeXPath) {
        String selectSql = "SELECT nodeXPath FROM DM_NodeXPath WHERE idNodeXPath = ?";
        try (Connection conn = getConnection();
             var pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setLong(1, idNodeXPath);
            var rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("nodeXPath");
            throw new IllegalArgumentException("Node XPath not found for ID: " + idNodeXPath);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching node XPath from database.", e);
        }
    }
    //----Get Or Create Default Tree------------------------------------------------------------------------------------
    private int getOrCreateDefaultTree(Connection conn) throws SQLException {
        // Check if the default tree already exists
        String checkSql = "SELECT idtree FROM dm_deftreenode LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("idTree");
            }
        }

    }
}
