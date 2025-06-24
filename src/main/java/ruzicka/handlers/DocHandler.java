package ruzicka.handlers;

import ruzicka.creators.DocCreator;
import ruzicka.databaseOprations.DatabaseConfig;
import ruzicka.databaseOprations.DatabaseManager;
import ruzicka.treeSupport.ArrayTree;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles high-level document operations, including creation, and placement within defined tree structures.
 * It coordinates {@link DocCreator}, {@link DatabaseManager}, and {@link ArrayTree}.
 */
public class DocHandler {
//----------------------------------------------------------------------------------------------------------------------

    private DatabaseManager dbManager;
    private DocCreator docCreator;

    public DocHandler() {
        this.dbManager = new DatabaseManager();
        this.docCreator = new DocCreator();
    }

    public java.sql.Connection getConnection() throws java.sql.SQLException {
        // Use the DatabaseConfig class to get credentials
        return java.sql.DriverManager.getConnection(
                DatabaseConfig.getUrl(),
                DatabaseConfig.getUser(),
                DatabaseConfig.getPassword()
        );
    }

    //----addDocument-------------------------------------------------------------------------------------------------------
    public void addDocument(Integer idDocType, Blob docContent, String docFormat,
                            List<String> docAttrValues, List<String> docAttrNames, List<String> attrTypes) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            long idDoc = docCreator.createDocument(idDocType, docContent, docFormat, docAttrValues, docAttrNames, attrTypes);
            System.out.println("Successfully created document with idDoc: " + idDoc);

            Map<String, String> attributeMap = new HashMap<>();
            for (int i = 0; i < docAttrNames.size(); i++) {
                if (docAttrValues.get(i) != null && !docAttrValues.get(i).isEmpty()) {
                    attributeMap.put(docAttrNames.get(i), docAttrValues.get(i));
                }
            }

            String treeQuery = "SELECT idtree FROM dm_tree";
            try (PreparedStatement treeStmt = conn.prepareStatement(treeQuery)) {
                try (ResultSet treeRs = treeStmt.executeQuery()) {
                    while (treeRs.next()) {
                        int treeId = treeRs.getInt("idtree");
                        System.out.println("--- Processing document " + idDoc + " for tree " + treeId + " ---");
                        processDocumentForTree(conn, idDoc, treeId, attributeMap, idDocType);
                    }
                }
            }
            conn.commit();
            System.out.println("Successfully added document " + idDoc + " and processed for all applicable trees.");

        } catch (Exception e) {
            if (conn != null) {
                try {
                    System.err.println("Rolling back transaction due to error: " + e.getMessage());
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error during transaction rollback: " + ex.getMessage());
                    e.addSuppressed(ex);
                }
            }
            System.err.println("Full error during addDocument:");
            e.printStackTrace(System.err);
            throw new RuntimeException("Failed to add document and update trees: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    /**
     * Processes a document for a specific tree, placing it according to the defined structure and conditions.
     *
     * @param conn         The database connection.
     * @param idDoc        The ID of the document to process.
     * @param treeId       The ID of the tree to process the document against.
     * @param attributeMap A map of document attributes used for placement conditions.
     * @param idDocType    The type ID of the document.
     *
     * @throws SQLException If any SQL error occurs during processing.
     */

    private void processDocumentForTree(Connection conn, long idDoc, int treeId,
                                        Map<String, String> attributeMap, int idDocType) throws SQLException {

        //  Load or Initialize the Physical Tree ---
        int rootDefNodeIdForThisTree = getRootDefTreeNodeId(conn, treeId);
        if (rootDefNodeIdForThisTree == -1) {
            System.err.println("CRITICAL: No root definition node found for treeId: " + treeId + ". Cannot process this tree for doc " + idDoc);
            return;
        }

        ArrayTree arrayTree = new ArrayTree();
        byte[] treeContentBytes = dbManager.getTreeContent(conn, treeId);
        int nextFreeIxForTree = dbManager.getTreeNextFreeNodeIndex(conn, treeId);
        arrayTree.setTreeId(treeId); // Set the treeId for context printout

        if (treeContentBytes != null && treeContentBytes.length > 0) {
            arrayTree.fromByteArray(treeContentBytes);
            arrayTree.setNextFreeIndex(nextFreeIxForTree);
        } else {
            arrayTree.initArrayTree();
        }

        // Initialize the Root Node of the Physical Tree if it's new ---
        if (arrayTree.getNode(0) != null && arrayTree.getNode(0).idNodeName == 0) {
            String rootNodeNameScript = null;
            String rootNodeNameSql = "SELECT nodenamescript FROM dm_deftreenode WHERE iddeftreenode = ?";
            try (PreparedStatement ps = conn.prepareStatement(rootNodeNameSql)) {
                ps.setInt(1, rootDefNodeIdForThisTree);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rootNodeNameScript = rs.getString(1);
                    } else {
                        System.err.println("CRITICAL: Could not fetch nodenamescript for root definition " + rootDefNodeIdForThisTree + " of tree " + treeId);
                        return; // Cannot proceed if root definition itself is missing
                    }
                }
            }
            // fallback to get the name, even for the root.
            String actualRootNodeName = getNodeNameForDocumentAtLevel(conn, rootNodeNameScript, attributeMap, idDoc, rootDefNodeIdForThisTree);
            if (actualRootNodeName == null || actualRootNodeName.isEmpty()) {
                actualRootNodeName = "_Tree" + treeId + "_DefinedRoot_"; // Fallback name
                System.err.println("WARN: Root definition script for tree " + treeId + " yielded no name for doc " + idDoc + ". Using fallback: " + actualRootNodeName);
            }
            // Save the name and path for the physical root node.
            arrayTree.getNode(0).idNodeName = dbManager.saveNodeName(conn, actualRootNodeName);
            arrayTree.getNode(0).idNodeXPath = dbManager.saveNodeXPath(conn, "/" + actualRootNodeName);
        }

        // Recursively Find All Placement Locations
        List<Integer> finalNodeIdsInArrayTree = placeDocumentInTreeRecursive(conn, arrayTree, rootDefNodeIdForThisTree, 0, idDoc, attributeMap, treeId);

        //  Handle the Placement Results
        if (finalNodeIdsInArrayTree.isEmpty()) {
            System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Did not meet conditions for placement in any branch.");
            // still save the tree because the process might have created new intermediate nodes
            dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
            return;
        }

        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Placing in " + finalNodeIdsInArrayTree.size() + " location(s): " + finalNodeIdsInArrayTree);

        // Link the Document to Each Found Location
        for (int finalNodeId : finalNodeIdsInArrayTree) {
            ArrayTree.TreeNode docFinalNode = arrayTree.getNode(finalNodeId);
            if (docFinalNode == null) {
                System.err.println("CRITICAL: Final ArrayTree node " + finalNodeId + " is null for tree " + treeId + ". Skipping this placement.");
                continue; // Skip to the next placement ID
            }

            // Generate and save the node's XPath if it hasnt been done already.
            if (docFinalNode.idNodeXPath == 0) {
                String xpathStr = arrayTree.generateXpath(finalNodeId, conn, this.dbManager);
                long idNodeXPath = dbManager.saveNodeXPath(conn, xpathStr);
                docFinalNode.idNodeXPath = idNodeXPath;
            }

            // Create the links in the database
            dbManager.insertDocXPath(conn, idDoc, docFinalNode.idNodeXPath);
            dbManager.insertDocNode(conn, idDoc, finalNodeId, treeId);

            // Increment the document count for the physical node
            docFinalNode.docCount++;
            System.out.println("--> Linked doc to XPath ID " + docFinalNode.idNodeXPath + ". ArrayTree Node " + finalNodeId + " docCount is now: " + docFinalNode.docCount);
        }

        //Save the Final State of the Tree
        dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Updated DM_Tree content. NextFreeIndex for ArrayTree: " + arrayTree.getNextFreeIndex());
    }


    // In class DocHandler

    private List<Integer> placeDocumentInTreeRecursive(Connection conn, ArrayTree arrayTree,
                                                       int currentDefTreeNodeId, int currentParentArrayNodeIdInTree,
                                                       long idDoc, Map<String, String> attributeMap, int treeId) throws SQLException {

        List<Integer> finalPlacementNodeIds = new ArrayList<>();

        // --- 1. Get definition details for the current level ---
        String defDetailsSql = "SELECT nodenamescript, docincludecondition FROM dm_deftreenode WHERE iddeftreenode = ?";
        String nodeNameScriptFromDB = null;
        String docIncludeCondition = null;
        try (PreparedStatement ps = conn.prepareStatement(defDetailsSql)) {
            ps.setInt(1, currentDefTreeNodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nodeNameScriptFromDB = rs.getString("nodenamescript");
                    docIncludeCondition = rs.getString("docincludecondition");
                } else {
                    System.err.println("ERROR: Definition node " + currentDefTreeNodeId + " not found for tree " + treeId);
                    return finalPlacementNodeIds; // Return empty list
                }
            }
        }

        // --- 2. GATEKEEPER CHECK: See if the document belongs in this branch at all ---
        boolean docCanEnterThisBranch = false;
        // An empty or true condition always allows entry
        if (docIncludeCondition == null || docIncludeCondition.trim().isEmpty() || docIncludeCondition.equalsIgnoreCase("true")) {
            docCanEnterThisBranch = true;
        } else if (docIncludeCondition.equalsIgnoreCase("false")) {
            docCanEnterThisBranch = false;
        } else {
            // For meaningful conditions, execute the check against the DB
            docCanEnterThisBranch = checkDocIncludeCondition(conn, docIncludeCondition, idDoc, attributeMap, currentDefTreeNodeId);
        }


        if (!docCanEnterThisBranch) {
            // If the document fails the condition, it cannot be placed here OR in any of its children.
            // Stop processing this entire branch immediately by returning an empty list.
            return finalPlacementNodeIds;
        }

        // --- 3. Create the physical node for this level ---
        int arrayNodeForThisDefinitionLevel;
        if (currentDefTreeNodeId == getRootDefTreeNodeId(conn, treeId)) {
            arrayNodeForThisDefinitionLevel = 0; // Root is always at index 0
        } else {
            String actualNodeNameForThisDoc = getNodeNameForDocumentAtLevel(conn, nodeNameScriptFromDB, attributeMap, idDoc, currentDefTreeNodeId);
            if (actualNodeNameForThisDoc == null || actualNodeNameForThisDoc.isEmpty()) {
                return finalPlacementNodeIds;
            }
            long idNodeName = dbManager.saveNodeName(conn, actualNodeNameForThisDoc);
            arrayNodeForThisDefinitionLevel = findOrInsertArrayTreeNode(arrayTree, currentParentArrayNodeIdInTree, idNodeName, actualNodeNameForThisDoc, "DYNAMIC", currentDefTreeNodeId);
        }

        // --- 4. Explore children definitions ---
        List<Integer> childDefIds = getChildDefIds(conn, treeId, currentDefTreeNodeId);
        boolean placedInChildBranch = false;
        if (!childDefIds.isEmpty()) {
            for (int childDefId : childDefIds) {
                List<Integer> placementIdsFromChild = placeDocumentInTreeRecursive(conn, arrayTree, childDefId, arrayNodeForThisDefinitionLevel, idDoc, attributeMap, treeId);
                if (!placementIdsFromChild.isEmpty()) {
                    finalPlacementNodeIds.addAll(placementIdsFromChild);
                    placedInChildBranch = true;
                }
            }
        }

        // --- 5. Final Placement: If it wasn't placed deeper, place it here ---
        if (!placedInChildBranch) {
            finalPlacementNodeIds.add(arrayNodeForThisDefinitionLevel);
        }

        return finalPlacementNodeIds;
    }

    private int findOrInsertArrayTreeNode(ArrayTree arrayTree, int parentArrayNodeId, long idNodeName, String actualName, String type, int defId) {
        int existingNode = -1;
        for (int i = 0; i < arrayTree.getMaxNodes(); i++) {
            ArrayTree.TreeNode node = arrayTree.getNode(i);
            if (node != null && node.parentId == parentArrayNodeId && node.idNodeName == idNodeName) {
                existingNode = i;
                break;
            }
        }
        if (existingNode == -1) {
            existingNode = arrayTree.insertNode(parentArrayNodeId, idNodeName, 0);
            // System.out.println("Inserted new " + type + " ArrayTree node: " + existingNode + " (name: '" + actualName + "', idNodeName: " + idNodeName + ") under parent " + parentArrayNodeId + " (for def " + defId + ")");
        } else {
            // System.out.println("Found existing " + type + " ArrayTree node: " + existingNode + " (name: '" + actualName + "', idNodeName: " + idNodeName + ") under parent " + parentArrayNodeId + " (for def " + defId + ")");
        }
        return existingNode;
    }

    private String evaluateStaticScript(Connection conn, String script) throws SQLException {
        if (script != null && script.toLowerCase().startsWith("select '") && script.endsWith("'")) {
            try (PreparedStatement ps = conn.prepareStatement(script)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            } catch (SQLException e) {
                System.err.println("WARN: Failed to execute static name script [" + script + "]: " + e.getMessage());
                if (script.toLowerCase().startsWith("select '") && script.endsWith("'") && script.length() > 8) {
                    return script.substring(8, script.length() - 1);
                }
            }
        }
        return null;
    }

    private List<Integer> getChildDefIds(Connection conn, int treeId, int parentDefNodeId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT iddeftreenode FROM dm_deftreenode WHERE idtree = ? AND iddefparenttreenode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treeId);
            ps.setInt(2, parentDefNodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("iddeftreenode"));
            }
        }
        return ids;
    }

    private String getNodeNameForDocumentAtLevel(Connection conn, String nodeNameScriptFromDB,
                                                 Map<String, String> attributeMap,
                                                 long idDoc, int defId) throws SQLException {
        if (nodeNameScriptFromDB == null || nodeNameScriptFromDB.trim().isEmpty()) {
            return null;
        }

        try (PreparedStatement ps = conn.prepareStatement(nodeNameScriptFromDB)) {
            int paramCount = 0;
            try {
                paramCount = ps.getParameterMetaData().getParameterCount();
            } catch (SQLException metaEx) {
                paramCount = (int) nodeNameScriptFromDB.chars().filter(ch -> ch == '?').count();
            }

            if (paramCount == 1) {
                ps.setLong(1, idDoc);
            } else if (paramCount > 1) {
                System.err.println("WARN: Def " + defId + ": DB nodeNameScript has " + paramCount + " params. Only binding idDoc if it's the sole param. Script: [" + nodeNameScriptFromDB + "]");
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String nodeName = rs.getString(1);
                    return (nodeName == null || nodeName.trim().isEmpty()) ? null : nodeName;
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR executing DB nodeNameScript for def " + defId + " [" + nodeNameScriptFromDB + "] with idDoc " + idDoc + ": " + e.getMessage());
            throw e;
        }
    }

    private boolean checkDocIncludeCondition(Connection conn, String conditionSql, long idDoc, Map<String, String> attributeMap, int defId) throws SQLException {
        // Handle the simple cases of 'true', 'false', or empty/null
        if (conditionSql == null || conditionSql.trim().isEmpty() || conditionSql.equalsIgnoreCase("true")) {
            return true;
        }
        if (conditionSql.equalsIgnoreCase("false")) {
            return false;
        }

        // This wrapper provides the "d" alias and the single '?' parameter for the 02_nodedefinition.sql.
        String sql = "SELECT CASE WHEN EXISTS (SELECT 1 FROM dm_doc d WHERE d.iddoc = ? AND (" + conditionSql + ")) THEN true ELSE false END";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Bind the document ID to the one and only '?' placeholder.
            ps.setLong(1, idDoc);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR evaluating docIncludeCondition for def " + defId + " [Full SQL: " + sql.replace("?", String.valueOf(idDoc)) + "]: " + e.getMessage());
            throw e;
        }

        return false; // Default to false if something goes wrong.
    }

    private boolean isMeaningfulCondition(String condition) {
        return condition != null && !condition.trim().isEmpty() &&
                !condition.equalsIgnoreCase("false") && !condition.equalsIgnoreCase("true");
    }

    private int getRootDefTreeNodeId(Connection conn, int treeId) throws SQLException {
        String findRootDefSql = "SELECT iddeftreenode FROM dm_deftreenode WHERE idtree = ? AND iddefparenttreenode = 0";
        try (PreparedStatement ps = conn.prepareStatement(findRootDefSql)) {
            ps.setInt(1, treeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("iddeftreenode");
                }
            }
        }
        return -1;
    }

    //----deleteDocument---------------------------------------------------------------------------------------------------
    public void deleteDocument(long idDoc) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            Map<Integer, Integer> treeAndNodeMap = getDocumentNodeLocations(conn, idDoc);

            docCreator.deleteDocument(conn, idDoc);
            System.out.println("Successfully deleted document data for idDoc: " + idDoc);

            for (Map.Entry<Integer, Integer> entry : treeAndNodeMap.entrySet()) {
                int treeId = entry.getKey();
                int nodeId = entry.getValue();
                System.out.println("--- Cleaning up Tree " + treeId + " starting from Node " + nodeId + " ---");
                cascadeDeleteEmptyNodes(conn, treeId, nodeId);
            }
            System.out.println("Successfully cleaned up all trees for document idDoc: " + idDoc);

            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                try {
                    System.err.println("Rolling back transaction due to error: " + e.getMessage());
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error during transaction rollback: " + ex.getMessage());
                    e.addSuppressed(ex);
                }
            }
            System.err.println("Full error during deleteDocument:");
            e.printStackTrace(System.err);
        }
    }

    //----helpers for deleteDocument----------------------------------------------------------------------------------------
    private Map<Integer, Integer> getDocumentNodeLocations(Connection conn, long idDoc) throws SQLException {
        Map<Integer, Integer> locations = new HashMap<>();
        String sql = "SELECT idtree, ixnode FROM dm_docnode WHERE iddoc = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDoc);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    locations.put(rs.getInt("idtree"), rs.getInt("ixnode"));
                }
            }
        }
        return locations;
    }

    //----cascade delete all empty nodes---------------------------------------------------------------------------------
    private void cascadeDeleteEmptyNodes(Connection conn, int treeId, int startingNodeId) throws SQLException {

        ArrayTree arrayTree = new ArrayTree();
        byte[] treeContentBytes = dbManager.getTreeContent(conn, treeId);
        int nextFreeIxForTree = dbManager.getTreeNextFreeNodeIndex(conn, treeId);

        // If empty then throw no content
        if (treeContentBytes == null || treeContentBytes.length == 0) {
            System.err.println("Tree " + treeId + " is empty. No nodes to delete.");
            return;
        }
        arrayTree.fromByteArray(treeContentBytes);
        arrayTree.setNextFreeIndex(nextFreeIxForTree);

        int currentNodeId = startingNodeId;
        String deleteXpathSql = "DELETE FROM dm_nodexpath WHERE idnodexpath = ?";

        try (PreparedStatement ps = conn.prepareStatement(deleteXpathSql)) {

            //go level up to the parent node
            while (currentNodeId >= 0) { //deleting even root (index 0)
                ArrayTree.TreeNode currentNode = arrayTree.getNode(currentNodeId);
                if (currentNode == null) {
                    System.err.println("ERROR: Node " + currentNodeId + " not found in ArrayTree for tree " + treeId + ". Cannot delete empty nodes.");
                    break;
                }
                //decrease the number of docCount
                if (currentNodeId == startingNodeId) {
                    currentNode.docCount--;
                }

                //check if current node is empty (0 children 0 docs)
                if (currentNode.docCount <= 0 && currentNode.nodeCount <= 0) {
                    int parentNodeId = currentNode.parentId;
                    long idNodeNameToLog = currentNode.idNodeName; //for logging purposes
                    long idNodeXPathToDelete = currentNode.idNodeXPath;

                    String nodeName = dbManager.getNodeNameById(conn, currentNode.idNodeName);//purely cosmetic for logging

                    //delete the node from the ArrayTree
                    arrayTree.deleteNode(currentNodeId);
                    System.out.println("Deleted empty node " + currentNodeId + " (name: '" + nodeName + "') from tree " + treeId);

                    if (idNodeXPathToDelete > 0) {
                        ps.setLong(1, idNodeXPathToDelete);
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected > 0) {
                            System.out.println("--> Deleted associated NodeXPath with ID: " + idNodeXPathToDelete);
                        }
                    }

                    //moveup to the parent node
                    currentNodeId = parentNodeId;
                } else {
                    System.out.println("Node " + currentNodeId + " (name: '" + dbManager.getNodeNameById(conn, currentNode.idNodeName) + "') is not empty. Ending.");
                    break;
                }
            }
            dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
            System.out.println("Updated tree " + treeId + " after deleting empty nodes. NextFreeIndex: " + arrayTree.getNextFreeIndex());
        }
    }
    //--------------------------------------------------------------------------------------------------------------
    /**
     * Public method to print the structure of a given tree.
     * @param treeId The ID of the tree to print.
     */
    public void printTreeStructure(int treeId) {
        System.out.println("\n=======================================================");
        System.out.println("          TREE STRUCTURE FOR TREE ID: " + treeId);
        System.out.println("=======================================================");

        try (Connection conn = getConnection()) {
            ArrayTree arrayTree = new ArrayTree();
            byte[] treeContentBytes = dbManager.getTreeContent(conn, treeId);
            if (treeContentBytes == null || treeContentBytes.length == 0) {
                System.out.println("Tree is empty or does not exist.");
                return;
            }
            arrayTree.fromByteArray(treeContentBytes);

            // Start the recursive printing from the root node (index 0)
            printNodeRecursive(conn, arrayTree, 0, "");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to print tree structure for treeId: " + treeId, e);
        }
        System.out.println("=======================================================\n");
    }

    /**
     * Recursively prints a node and its children.
     * @param conn The database connection.
     * @param tree The ArrayTree being traversed.
     * @param nodeId The index of the current node to print.
     * @param indent The string used for indentation to show hierarchy.
     */
    private void printNodeRecursive(Connection conn, ArrayTree tree, int nodeId, String indent) throws SQLException {
        ArrayTree.TreeNode node = tree.getNode(nodeId);
        if (node == null || node.idNodeName == 0) { // Skip unused or uninitialized nodes
            return;
        }

        // Print the current node's name
        String nodeName = dbManager.getNodeNameById(conn, node.idNodeName);
        System.out.println(indent + "+-- " + nodeName + " [NodeID: " + nodeId + ", Docs: " + node.docCount + "]");

        // Print the documents within this node
        List<String> docNames = getDocumentNamesInNode(conn, tree.getTreeId(), nodeId); // Assuming getTreeId() exists or is passed
        for (String docName : docNames) {
            System.out.println(indent + "  |   - " + docName);
        }

        // Recursively call for all children of the current node
        for (int i = 0; i < tree.getMaxNodes(); i++) {
            ArrayTree.TreeNode childNode = tree.getNode(i);
            // A node is a child if its parentId matches the current nodeId
            if (childNode != null && childNode.parentId == nodeId && i != nodeId) { // i != nodeId prevents infinite loops for self-parented root
                printNodeRecursive(conn, tree, i, indent + "  |");
            }
        }
    }

    /**
     * Retrieves the names of all documents linked to a specific node in a specific tree.
     * @param conn The database connection.
     * @param treeId The ID of the tree.
     * @param nodeId The index of the node in the ArrayTree.
     * @return A list of document names.
     */
    private List<String> getDocumentNamesInNode(Connection conn, int treeId, int nodeId) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT d.docname FROM dm_doc d JOIN dm_docnode dn ON d.iddoc = dn.iddoc WHERE dn.idtree = ? AND dn.ixnode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treeId);
            ps.setInt(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("docname"));
                }
            }
        }
        return names;
    }
    //----updateDocument---------------------------------------------------------------------------------------------------
    public void updateDocument(long idDoc,Integer idDocType, Blob docContent, String docFormat,
                               List<String> docAttrValues, List<String> docAttrNames, List<String> attrTypes) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            deleteDocument(idDoc);
            addDocument(idDocType, docContent, docFormat, docAttrValues, docAttrNames, attrTypes);

            System.out.println("Successfully reprocessed all trees for updated document idDoc: " + idDoc);

            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                try {
                    System.err.println("Rolling back transaction due to error: " + e.getMessage());
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error during transaction rollback: " + ex.getMessage());
                    e.addSuppressed(ex);
                }
            }
            System.err.println("Full error during updateDocument:");
            e.printStackTrace(System.err);
        }
    }
}