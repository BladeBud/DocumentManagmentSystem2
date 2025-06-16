package ruzicka.creators;

import ruzicka.databaseOprations.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Adam
 * @since 2025-04-15
 */
public class TypeCreator {
    //----Database connection---------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        // Use the DatabaseConfig class to get credentials
        return java.sql.DriverManager.getConnection(
                DatabaseConfig.getUrl(),
                DatabaseConfig.getUser(),
                DatabaseConfig.getPassword()
        );
    }

    //----Type creation----------------------------------------------------------------------------------------------------

    /**
     * creates a new type in the database. checks if the relevant attributes are there and if the type doesnt aleeady exist
     *
     * @param typeName       name of the type to be created
     * @param nameScript     name script for the new type
     * @param attributeNames list of attribute names for the new type
     * @param isRequired     list of booleans indicating if the attributes are required
     */
    public void createType(String typeName, String nameScript, List<String> attributeNames, List<Boolean> isRequired) {
        if (attributeNames.size() != isRequired.size()) {
            throw new IllegalArgumentException("The number of attributes must match the number of required flags");
        }

        String checkTypeSql = "SELECT COUNT(*) FROM dm_doctype WHERE doctypename = ?";
        // Corrected SQL: dm_doctypeattr.iddocattr should be iddocattr from dm_docattr
        String checkAttrSql = "SELECT COUNT(*) FROM dm_docattr WHERE attrname = ?";
        String insertTypeSql = "INSERT INTO dm_doctype (doctypename, docnamescript) VALUES (?, ?) RETURNING iddoctype";
        // Corrected SQL: subselect should be for dm_docattr.iddocattr based on attrname
        String insertDoctypeattrSql = "INSERT INTO dm_doctypeattr (iddoctype, iddocattr, isrequired) VALUES (?, " +
                "(SELECT da.iddocattr FROM dm_docattr da WHERE da.attrname = ?), ?)";


        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement checkTypeStmt = conn.prepareStatement(checkTypeSql);
                 PreparedStatement checkAttrStmt = conn.prepareStatement(checkAttrSql);
                 PreparedStatement insertTypeStmt = conn.prepareStatement(insertTypeSql);
                 PreparedStatement insertDoctypeattrStmt = conn.prepareStatement(insertDoctypeattrSql)) {

                // Check if the type already exists
                checkTypeStmt.setString(1, typeName);
                try (ResultSet rs = checkTypeStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new IllegalArgumentException("Type already exists with name: " + typeName);
                    }
                }

                // Check if all attributes exist
                for (String attrName : attributeNames) {
                    checkAttrStmt.setString(1, attrName);
                    try (ResultSet rs = checkAttrStmt.executeQuery()) {
                        if (!rs.next() || rs.getInt(1) == 0) {
                            throw new IllegalArgumentException("Attribute does not exist: " + attrName);
                        }
                    }
                }

                // Insert a new type and get its ID
                int doctypeId;
                insertTypeStmt.setString(1, typeName);
                insertTypeStmt.setString(2, nameScript);
                try (ResultSet rs = insertTypeStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Failed to create new type, no ID returned.");
                    }
                    doctypeId = rs.getInt(1);
                }

                // Create connections between type and attributes
                for (int i = 0; i < attributeNames.size(); i++) {
                    insertDoctypeattrStmt.setInt(1, doctypeId);
                    insertDoctypeattrStmt.setString(2, attributeNames.get(i));
                    insertDoctypeattrStmt.setBoolean(3, isRequired.get(i));
                    insertDoctypeattrStmt.addBatch(); // Batching for potentially better performance
                }
                insertDoctypeattrStmt.executeBatch();

                conn.commit();
            } catch (SQLException e) { // Catch SQLException specifically for rollback
                if (conn != null) conn.rollback(); // Rollback on SQL error
                throw new RuntimeException("Error during type creation transaction.", e);
            } catch (Exception e) { // Catch other exceptions (like IllegalArgumentException)
                if (conn != null) conn.rollback(); // Rollback on any other error during transaction
                throw e; // Re-throw other exceptions
            }

        } catch (SQLException e) { // Catch connection-level SQLException
            throw new RuntimeException("Database connection error or outer transaction error.", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Reset auto-commit
                    conn.close();
                } catch (SQLException e) {
                    // log error on close
                }
            }
        }
    }

    //----Type delete-----------------------------------------------------------------------------------------------------
    public void deleteType(String typeName) {
        String checkSql = "SELECT COUNT(*) FROM dm_doctype WHERE doctypename = ?";
        String deleteSql = "DELETE FROM dm_doctype WHERE doctypename = ?";
        String checkUsageSql = "SELECT COUNT(*) FROM dm_doc WHERE iddoctype = (SELECT iddoctype FROM dm_doctype WHERE doctypename = ?)";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement checkUsageStmt = conn.prepareStatement(checkUsageSql);
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {

            // Check if the type exists
            checkStmt.setString(1, typeName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    throw new IllegalArgumentException("Type does not exist with name: " + typeName);
                }
            }

            // Check if the type is used in any document
            checkUsageStmt.setString(1, typeName);
            try (ResultSet rs = checkUsageStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Type is used in documents and cannot be deleted: " + typeName);
                }
            }

            deleteStmt.setString(1, typeName);
            int affectedRows = deleteStmt.executeUpdate();
            if (affectedRows == 0) {
                // This might happen if type was deleted between check and delete, or if initial check was flawed.
                // Or simply, type name didn't match for delete for some reason.
                System.err.println("Warning: deleteType operation for '" + typeName + "' affected 0 rows, though it was expected to exist.");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting type from database.", e);
        }
    }

    //----Type change-----------------------------------------------------------------------------------------------------
    public void changeType(String typeName, String nameScript, List<String> attributeNames, List<Boolean> isRequired) {
        // Query to find the highest version number for the type
        // Using LIKE for versioning can be tricky if typeName itself contains "version".
        // A more robust versioning might use a dedicated version column or a clearer naming pattern.
        String versionQuery = "SELECT doctypename FROM dm_doctype WHERE doctypename LIKE ? || 'ver%' ORDER BY doctypename DESC LIMIT 1";
        String originalTypeCheckSql = "SELECT COUNT(*) FROM dm_doctype WHERE doctypename = ?";


        try (Connection conn = getConnection();
             PreparedStatement versionStmt = conn.prepareStatement(versionQuery);
             PreparedStatement originalCheckStmt = conn.prepareStatement(originalTypeCheckSql)) {

            // Check if original type exists
            originalCheckStmt.setString(1, typeName);
            try (ResultSet rs = originalCheckStmt.executeQuery()) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    throw new IllegalArgumentException("Original type '" + typeName + "' does not exist. Cannot change it.");
                }
            }

            versionStmt.setString(1, typeName);
            int version = 1;

            try (ResultSet rs = versionStmt.executeQuery()) {
                if (rs.next()) {
                    String lastVersion = rs.getString(1);
                    try {
                        // Assuming "ver" prefix for version part
                        String versionStr = lastVersion.substring(typeName.length() + "ver".length());
                        version = Integer.parseInt(versionStr) + 1;
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        System.err.println("Could not parse version from '" + lastVersion + "', defaulting to version 1. Error: " + e.getMessage());
                        version = 1; // Fallback if parsing fails
                    }
                }
            }

            String newTypeName = typeName + "ver" + version;
            // Call createType which handles its own transaction
            createType(newTypeName, nameScript, attributeNames, isRequired);
            System.out.println("Type '" + typeName + "' changed. New version created as '" + newTypeName + "'.");


        } catch (SQLException e) {
            throw new RuntimeException("Error changing type in database.", e);
        }
    }
}