package ruzicka.creators;

import java.util.List;

/**
 * @author Adam
 * @since 2025-04-15
 */
public class TypeCreator {
    //----Database connection parameters----------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    //----Database connection---------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    //----Type creation----------------------------------------------------------------------------------------------------

    /**
     * creates a new type in the database. checks if the relevant attributes are there and if the type doesnt aleeady exist
     * @param typeName
     * @param nameScript
     * @param attributeNames
     * @param isRequired
     */

    public void createType(String typeName, String nameScript, List<String> attributeNames, List<Boolean> isRequired) {
        if (attributeNames.size() != isRequired.size()) {
            throw new IllegalArgumentException("The number of attributes must match the number of required flags");
        }

        String checkTypeSql = "SELECT COUNT(*) FROM dm_doctype WHERE doctypename = ?";
        String checkAttrSql = "SELECT COUNT(*) FROM dm_docattr WHERE attrname = ?";
        String insertTypeSql = "INSERT INTO dm_doctype (doctypename, docnamescript) VALUES (?, ?) RETURNING iddoctype";
        String insertDoctypeattrSql = "INSERT INTO dm_doctypeattr (iddoctype, iddocattr, isrequired) VALUES (?, " +
                "(SELECT dm_doctypeattr.iddocattr FROM dm_docattr WHERE attrname = ?), ?)";

        try (java.sql.Connection conn = getConnection()) {
            // Start transaction
            conn.setAutoCommit(false);
            try {
                // Check if the type already exists
                try (var checkTypeStmt = conn.prepareStatement(checkTypeSql)) {
                    checkTypeStmt.setString(1, typeName);
                    try (var rs = checkTypeStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new IllegalArgumentException("Type already exists with name: " + typeName);
                        }
                    }
                }

                // Check if all attributes exist
                try (var checkAttrStmt = conn.prepareStatement(checkAttrSql)) {
                    for (String attrName : attributeNames) {
                        checkAttrStmt.setString(1, attrName);
                        try (var rs = checkAttrStmt.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) == 0) {
                                throw new IllegalArgumentException("Attribute does not exist: " + attrName);
                            }
                        }
                    }
                }

                // Insert a new type and get its ID
                int doctypeId;
                try (var insertTypeStmt = conn.prepareStatement(insertTypeSql)) {
                    insertTypeStmt.setString(1, typeName);
                    insertTypeStmt.setString(2, nameScript);
                    try (var rs = insertTypeStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new RuntimeException("Failed to create new type");
                        }
                        doctypeId = rs.getInt(1);
                    }
                }

                // Create connections between type and attributes
                try (var insertDoctypeattrStmt = conn.prepareStatement(insertDoctypeattrSql)) {
                    for (int i = 0; i < attributeNames.size(); i++) {
                        insertDoctypeattrStmt.setInt(1, doctypeId);
                        insertDoctypeattrStmt.setString(2, attributeNames.get(i));
                        insertDoctypeattrStmt.setBoolean(3, isRequired.get(i));
                        insertDoctypeattrStmt.executeUpdate();
                    }
                }

                // Commit transaction
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error creating type in database.", e);
        }
    }
    //----Type delete-----------------------------------------------------------------------------------------------------
    /**
     * Deletes a type from the database. Checks if the type exists and if it is used in any document before deleting it.
     * @param typeName
     * @throws IllegalArgumentException if the type does not exist or is used in documents
     * @throws RuntimeException if there is an error during the database operation
     */

    public void deleteType(String typeName) {
        String checkSql = "SELECT COUNT(*) FROM dm_doctype WHERE doctypename = ?";
        String deleteSql = "DELETE FROM dm_doctype WHERE doctypename = ?";
        String checkUsageSql = "SELECT COUNT(*) FROM dm_doc WHERE iddoctype = (SELECT iddoctype FROM dm_doctype WHERE doctypename = ?)";

        try (java.sql.Connection conn = getConnection();
             var checkStmt = conn.prepareStatement(checkSql);
             var checkUsageStmt = conn.prepareStatement(checkUsageSql);
             var deleteStmt = conn.prepareStatement(deleteSql)) {

            // Check if the type exists
            checkStmt.setString(1, typeName);
            try (var rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    throw new IllegalArgumentException("Type does not exist with name: " + typeName);
                }
            }

            // Check if the type is used in any document
           checkUsageStmt.setString(1, typeName);
            try (var rs = checkUsageStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Type is used in documents and cannot be deleted: " + typeName);
                }
            }

            // If it exists and is not used, delete the type
            deleteStmt.setString(1, typeName);
            deleteStmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error deleting type from database.", e);
        }
    }
    //----Type change-----------------------------------------------------------------------------------------------------

    /**
     * Changes the name and type of an existing type in the database.
     * @param typeName
     * @param nameScript
     * @param attributeNames
     * @param isRequired
     */

    public void changeType(String typeName, String nameScript, List<String> attributeNames, List<Boolean> isRequired) {
        // Query to find the highest version number for the type
        String versionQuery = "SELECT doctypename FROM dm_doctype WHERE doctypename LIKE ? || 'version%' ORDER BY doctypename DESC LIMIT 1";

        try (java.sql.Connection conn = getConnection();
             var versionStmt = conn.prepareStatement(versionQuery)) {

            versionStmt.setString(1, typeName);
            int version = 1;

            try (var rs = versionStmt.executeQuery()) {
                if (rs.next()) {
                    String lastVersion = rs.getString(1);
                    // Extract version number from the last version
                    try {
                        String versionStr = lastVersion.substring(lastVersion.lastIndexOf("version") + 7);
                        version = Integer.parseInt(versionStr) + 1;
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        // If parsing fails, start with version 1
                        version = 1;
                    }
                }
            }

            // Create a new type with version + number
            String newTypeName = typeName + "ver" + version;
            createType(newTypeName, nameScript, attributeNames, isRequired);

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error changing type in database.", e);
        }
    }
}
