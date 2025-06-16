package ruzicka.creators;

import ruzicka.databaseOprations.DatabaseConfig;

/**
 * @author Adam
 * @since 2025-04-17
 */
public class DefCreator {
    //----Database connection---------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        // Use the DatabaseConfig class to get credentials
        return java.sql.DriverManager.getConnection(
                DatabaseConfig.getUrl(),
                DatabaseConfig.getUser(),
                DatabaseConfig.getPassword()
        );
    }

    //----Definition creation---------------------------------------------------------------------------------------------

    /**
     * Creates a new definition of nodes
     *
     * @param idTree
     * @param idDefParentTreeNode
     * @param docIncludeCondition
     * @param nodeNamescript
     */

    public void createDefinition(Integer idTree, String idDefParentTreeNode, String docIncludeCondition, String nodeNamescript) {
        String checkSql = "SELECT COUNT(*) FROM dm_deftreenode WHERE idtree = ?";
        String insertSql = "INSERT INTO dm_deftreenode (idtree, iddefparenttreenode, docincludecondition, nodenamescript) VALUES (?, ?, ?, ?)";

        try (java.sql.Connection conn = getConnection();
             var checkStmt = conn.prepareStatement(checkSql);
             var insertStmt = conn.prepareStatement(insertSql)) {

            // Check if the definition already exists
            checkStmt.setInt(1, idTree);
            try (var rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Definition already exists with id: " + idTree);
                }
            }

            // If it doesn't exist, insert the new definition
            insertStmt.setInt(1, idTree);
            insertStmt.setString(2, idDefParentTreeNode);
            insertStmt.setString(3, docIncludeCondition);
            insertStmt.setString(4, nodeNamescript);
            insertStmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error creating definition in database.", e);
        }
    }
    //----Definition deletion---------------------------------------------------------------------------------------------

    /**
     * Deletes a definition of nodes
     */
//    public void deleteDefinition(Integer idDefTreeNode) {
//        String checkSql = "SELECT COUNT(*) FROM dm_deftreenode WHERE iddefparenttreenode = ?";
//        String deleteSql = "DELETE FROM dm_deftreenode WHERE iddefparenttreenode = ?";
//
//
//    }
    //----Definition change-----------------------------------------------------------------------------------------------

    /**
     * Updates a definition of nodes by creating a new versioned copy
     *
     * @param idDefTreeNode       ID of the definition tree node to update
     * @param docIncludeCondition New document include condition
     * @param nodeNamescript      New node name script
     */
    public void changeDefinition(Integer idDefTreeNode, String docIncludeCondition, String nodeNamescript) {
        // Query to find the original definition details
        String defQuery = "SELECT idtree, iddefparenttreenode FROM dm_deftreenode WHERE iddefparenttreenode = ?";
        // Query to find the highest version number for the definition
        String versionQuery = "SELECT idtree FROM dm_deftreenode WHERE idtree LIKE ? || 'version%' ORDER BY idtree DESC LIMIT 1";

        try (java.sql.Connection conn = getConnection();
             var defStmt = conn.prepareStatement(defQuery);
             var versionStmt = conn.prepareStatement(versionQuery)) {

            // First, check if the definition exists and get its details
            defStmt.setInt(1, idDefTreeNode);
            Integer idTree = null;
            String parentTreeNode = null;

            try (var rs = defStmt.executeQuery()) {
                if (rs.next()) {
                    idTree = rs.getInt("idtree");
                    parentTreeNode = rs.getString("iddefparenttreenode");
                } else {
                    throw new IllegalArgumentException("Definition not found with id: " + idDefTreeNode);
                }
            }

            // Find the highest version number for this definition
            versionStmt.setString(1, idTree.toString());
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

            // Create a new definition with a version + number
            String newIdTree = idTree + "ver" + version;

            // Create a new versioned definition using the createDefinition method
            createDefinition(Integer.parseInt(newIdTree), parentTreeNode, docIncludeCondition, nodeNamescript);

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error changing definition in database.", e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Error parsing tree ID as integer.", e);
        }
    }
}
