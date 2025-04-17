package ruzicka.creators;

/**
 * @author Adam
 * @since 2025-04-17
 */
public class DefCreator {
    //----Database connection parameters----------------------------------------------------------------------------------
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    //----Database connection---------------------------------------------------------------------------------------------
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
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
}
