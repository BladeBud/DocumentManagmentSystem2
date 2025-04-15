package ruzicka.creators;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Adam
 * @since 2025-04-15
 */
public class AttrCreator {
    //----Database connection parameters----------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    //----Database connection---------------------------------------------------------------------------------------------
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    //----Attribute creation----------------------------------------------------------------------------------------------

    /**
     * Creates a new attribute in the database. Checks if the attribute already exists before creating it.
     * @param attrName
     * @param attrType
     */
    public void createAttribute(String attrName, String attrType) {
        String checkSql = "SELECT COUNT(*) FROM dm_docattr WHERE attrName = ? AND attrtype = ?";
        String insertSql = "INSERT INTO dm_docattr (attrName, attrtype) VALUES (?, ?)";
        try (Connection conn = getConnection();
             var checkStmt = conn.prepareStatement(checkSql);
             var insertStmt = conn.prepareStatement(insertSql)) {

            // Check if the attribute already exists
            checkStmt.setString(1, attrName);
            checkStmt.setString(2, attrType);
            try (var rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Attribute already exists with name: " + attrName + " and type: " + attrType);
                }
            }

            // If it doesn't exist, insert the new attribute
            insertStmt.setString(1, attrName);
            insertStmt.setString(2, attrType);
            insertStmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error creating attribute in database.", e);
        }
    }


    //----Attribute change------------------------------------------------------------------------------------------------
    //TODO: when changing the attribute, it should be checked if the attribute is used in any document

    /**
     * Changes the name and type of an existing attribute in the database.
     * @param attrName
     * @param newAttrName
     * @param attrType
     * @param newAttrType
     */
    public void changeAttribute(String attrName, String newAttrName, String attrType, String newAttrType) {
        String sql = "UPDATE dm_docattr SET attrName = ? AND attrtype = ? WHERE attrName = ? AND attrtype = ?";
        try (Connection conn = getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newAttrName);
            pstmt.setString(2, newAttrType);
            pstmt.setString(3, attrName);
            pstmt.setString(4, attrType);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error changing attribute in database.", e);
        }
    }
}
