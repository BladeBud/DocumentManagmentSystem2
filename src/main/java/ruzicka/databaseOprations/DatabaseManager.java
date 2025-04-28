package ruzicka.databaseOprations;

import java.sql.Connection;
import java.sql.DriverManager;
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
}
