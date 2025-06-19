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

    private void processDocumentForTree(Connection conn, long idDoc, int treeId,
                                        Map<String, String> attributeMap, int idDocType) throws SQLException {

        int rootDefNodeIdForThisTree = getRootDefTreeNodeId(conn, treeId);

        if (rootDefNodeIdForThisTree == -1) {
            System.err.println("CRITICAL: No root definition node (idDefParentTreeNode=0) found for treeId: " + treeId + ". Cannot process this tree for doc " + idDoc);
            return;
        }

        ArrayTree arrayTree = new ArrayTree();
        byte[] treeContentBytes = dbManager.getTreeContent(conn, treeId);
        int nextFreeIxForTree = dbManager.getTreeNextFreeNodeIndex(conn, treeId);

        if (treeContentBytes != null && treeContentBytes.length > 0) {
            // System.out.println("Loading ArrayTree for tree " + treeId + " from " + treeContentBytes.length + " bytes. NextFreeIndex from DB: " + nextFreeIxForTree);
            arrayTree.fromByteArray(treeContentBytes);
            arrayTree.setNextFreeIndex(nextFreeIxForTree);
        } else {
            // System.out.println("Initializing new ArrayTree for tree " + treeId);
            arrayTree.initArrayTree();
        }

        if (arrayTree.getNode(0) != null && arrayTree.getNode(0).idNodeName == 0) {
            String rootNodeNameScript = null;
            String rootNodeNameSql = "SELECT nodenamescript FROM dm_deftreenode WHERE iddeftreenode = ?";
            try (PreparedStatement ps = conn.prepareStatement(rootNodeNameSql)) {
                ps.setInt(1, rootDefNodeIdForThisTree);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) rootNodeNameScript = rs.getString(1);
                    else {
                        System.err.println("CRITICAL: Could not fetch nodenamescript for root definition " + rootDefNodeIdForThisTree + " of tree " + treeId);
                        return; // Cannot proceed if root definition itself is missing details
                    }
                }
            }
            String actualRootNodeName = getNodeNameForDocumentAtLevel(conn, rootNodeNameScript, attributeMap, idDoc, rootDefNodeIdForThisTree);
            if (actualRootNodeName == null || actualRootNodeName.isEmpty()) {
                actualRootNodeName = "_Tree" + treeId + "_DefinedRoot_";
                System.err.println("WARN: Root definition script for tree " + treeId + " (def " + rootDefNodeIdForThisTree + ") yielded no name for doc " + idDoc + ". Using fallback: " + actualRootNodeName);
            }
            arrayTree.getNode(0).idNodeName = dbManager.saveNodeName(conn, actualRootNodeName);
            arrayTree.getNode(0).idNodeXPath = dbManager.saveNodeXPath(conn, "/" + actualRootNodeName);
            // System.out.println("Named ArrayTree physical root (index 0) for tree " + treeId + " as: " + actualRootNodeName + " (from def " + rootDefNodeIdForThisTree + ")");
        }

        // System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Starting recursive placement. RootDefID: " + rootDefNodeIdForThisTree + " (corresponds to ArrayTree Node 0).");
        int finalNodeIdInArrayTree = placeDocumentInTreeRecursive(conn, arrayTree, rootDefNodeIdForThisTree, 0, idDoc, attributeMap, treeId);

//         System.out.println(">>> Doc " + idDoc + ", Tree " + treeId + ": Placement result from recursion: finalNodeIdInArrayTree = " + finalNodeIdInArrayTree);

        // After recursion, if finalNodeIdInArrayTree is still 0, it means the document belongs in the
        // ArrayTree node 0 (which represents the tree's defined root), OR it didn't meet the root's own include condition.
        // The recursive call already handles the include condition check for the root definition level.
        // If `placeDocumentInTreeRecursive` returns 0 for the root definition call, it means the document
        // belongs in ArrayTree[0] (if its include condition passed) or it "bounced" from it (if its condition failed).
        // The parent of ArrayTree[0] is conceptually "outside this tree structure".
        // So, we need to verify if it *truly* belongs in node 0.

        boolean createLinkForThisTree = false;
        if (finalNodeIdInArrayTree == 0) { // Candidate for root node placement
            String rootNodeDocIncludeCondition = null;
            String rootDefDetailsSql = "SELECT docincludecondition FROM dm_deftreenode WHERE iddeftreenode = ?";
            try (PreparedStatement ps = conn.prepareStatement(rootDefDetailsSql)) {
                ps.setInt(1, rootDefNodeIdForThisTree);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) rootNodeDocIncludeCondition = rs.getString("docincludecondition");
                }
            }
            if (isMeaningfulCondition(rootNodeDocIncludeCondition)) {
                createLinkForThisTree = checkDocIncludeCondition(conn, rootNodeDocIncludeCondition, idDoc, attributeMap, rootDefNodeIdForThisTree);
            } else if (rootNodeDocIncludeCondition != null && rootNodeDocIncludeCondition.equalsIgnoreCase("true")) {
                createLinkForThisTree = true;
            } else if (getChildDefIds(conn, treeId, rootDefNodeIdForThisTree).isEmpty() &&
                    (rootNodeDocIncludeCondition == null || rootNodeDocIncludeCondition.trim().isEmpty())) {
                createLinkForThisTree = true; // Leaf root with no restrictive condition
            }
            if (!createLinkForThisTree) {
                System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Did not meet include condition for root definition " + rootDefNodeIdForThisTree + " (ArrayTree node 0). Skipping linkage for this tree.");
            }
        } else { // finalNodeIdInArrayTree is > 0, meaning it was placed in a descendant node.
            createLinkForThisTree = true;
        }

        if (!createLinkForThisTree) {
            dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex()); // Still save tree state
            return; // Do not create dm_docnode or dm_docxpath
        }

        // Proceed with linking if createLinkForThisTree is true
        ArrayTree.TreeNode docFinalNodeInArrayTree = arrayTree.getNode(finalNodeIdInArrayTree);
        if (docFinalNodeInArrayTree == null) {
            System.err.println("CRITICAL: Final ArrayTree node " + finalNodeIdInArrayTree + " is null for tree " + treeId + ", doc " + idDoc + ". Aborting.");
            return;
        }

        if (docFinalNodeInArrayTree.idNodeXPath == 0) {
            String xpathStr = arrayTree.generateXpath(finalNodeIdInArrayTree, conn, this.dbManager);
            // System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Generated XPath for ArrayTree node " + finalNodeIdInArrayTree + ": " + xpathStr);
            long idNodeXPath = dbManager.saveNodeXPath(conn, xpathStr);
            docFinalNodeInArrayTree.idNodeXPath = idNodeXPath;
        }

        dbManager.insertDocXPath(conn, idDoc, docFinalNodeInArrayTree.idNodeXPath);
        docFinalNodeInArrayTree.docCount++;
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Linked doc to XPath ID " + docFinalNodeInArrayTree.idNodeXPath + ". ArrayTree Node " + finalNodeIdInArrayTree + " docCount: " + docFinalNodeInArrayTree.docCount);

        dbManager.insertDocNode(conn, idDoc, finalNodeIdInArrayTree, treeId);
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Inserted into dm_docnode: (idDoc=" + idDoc + ", ixNode=" + finalNodeIdInArrayTree + ", idTree=" + treeId + ")");

        dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
        // System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Updated DM_Tree content. NextFreeIndex for ArrayTree: " + arrayTree.getNextFreeIndex());
    }


    private int placeDocumentInTreeRecursive(Connection conn, ArrayTree arrayTree,
                                             int currentDefTreeNodeId, int currentParentArrayNodeIdInTree,
                                             long idDoc, Map<String, String> attributeMap, int treeId) throws SQLException {

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
                    System.err.println("ERROR: Definition node " + currentDefTreeNodeId + " not found for tree " + treeId + " during recursion.");
                    return currentParentArrayNodeIdInTree;
                }
            }
        }

        int arrayNodeForThisDefinitionLevel;

        if (currentDefTreeNodeId == getRootDefTreeNodeId(conn, treeId)) {
            arrayNodeForThisDefinitionLevel = 0;
            // Name/XPath for arrayTree.getNode(0) was set in processDocumentForTree.
            // System.out.println("Def " + currentDefTreeNodeId + " (Tree Root Def): Corresponds to ArrayTree node 0.");
        } else {
            String actualNodeNameForThisDoc = getNodeNameForDocumentAtLevel(conn, nodeNameScriptFromDB, attributeMap, idDoc, currentDefTreeNodeId);
            if (actualNodeNameForThisDoc != null && !actualNodeNameForThisDoc.isEmpty()) {
                long idNodeName = dbManager.saveNodeName(conn, actualNodeNameForThisDoc);
                arrayNodeForThisDefinitionLevel = findOrInsertArrayTreeNode(arrayTree, currentParentArrayNodeIdInTree, idNodeName, actualNodeNameForThisDoc, "DYNAMIC", currentDefTreeNodeId);
            } else if (nodeNameScriptFromDB != null && nodeNameScriptFromDB.toLowerCase().startsWith("select '")) {
                String staticName = evaluateStaticScript(conn, nodeNameScriptFromDB);
                if (staticName != null && !staticName.isEmpty()) {
                    long idStaticNodeName = dbManager.saveNodeName(conn, staticName);
                    arrayNodeForThisDefinitionLevel = findOrInsertArrayTreeNode(arrayTree, currentParentArrayNodeIdInTree, idStaticNodeName, staticName, "STATIC", currentDefTreeNodeId);
                } else {
                    return currentParentArrayNodeIdInTree;
                }
            } else {
                return currentParentArrayNodeIdInTree;
            }
        }

        List<Integer> childDefIds = getChildDefIds(conn, treeId, currentDefTreeNodeId);
        if (!childDefIds.isEmpty()) {
            for (int childDefId : childDefIds) {
                int placementByChild = placeDocumentInTreeRecursive(conn, arrayTree, childDefId, arrayNodeForThisDefinitionLevel, idDoc, attributeMap, treeId);
                if (placementByChild != arrayNodeForThisDefinitionLevel) {
                    return placementByChild;
                }
            }
        }

        boolean docBelongsAtThisNode = false;
        if (isMeaningfulCondition(docIncludeCondition)) {
            docBelongsAtThisNode = checkDocIncludeCondition(conn, docIncludeCondition, idDoc, attributeMap, currentDefTreeNodeId);
        } else if (docIncludeCondition != null && docIncludeCondition.equalsIgnoreCase("true")) {
            docBelongsAtThisNode = true;
        } else if (childDefIds.isEmpty() && (docIncludeCondition == null || docIncludeCondition.trim().isEmpty())) {
            // System.out.println("Def " + currentDefTreeNodeId + " (ArrayNode " + arrayNodeForThisDefinitionLevel +", Leaf with no/empty/non-false condition): Assuming doc " + idDoc + " belongs.");
            docBelongsAtThisNode = true;
        }

        if (docBelongsAtThisNode) {
            // System.out.println("Doc " + idDoc + " final placement at ArrayTree node " + arrayNodeForThisDefinitionLevel + " (def " + currentDefTreeNodeId + ") based on its include condition.");
            return arrayNodeForThisDefinitionLevel;
        } else {
            return currentParentArrayNodeIdInTree;
        }
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
        if (!isMeaningfulCondition(conditionSql)) {
            return (conditionSql != null && conditionSql.equalsIgnoreCase("true"));
        }
        String finalSql = conditionSql.replaceAll(":\\w+\\b", "TRUE");
        finalSql = finalSql.replaceAll("\\b(dm_doc|DM_DOC)\\.iddoc\\b", "d.iddoc");

        String checkSql = "SELECT EXISTS (SELECT 1 FROM dm_doc d WHERE d.iddoc = ? AND (" + finalSql + "))";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setLong(1, idDoc);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            System.err.println("ERROR evaluating docIncludeCondition for def " + defId + " [" + conditionSql + "] with idDoc " + idDoc + ": " + e.getMessage());
            throw e;
        }
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
    public void deleteDocument(long idDoc){
        Connection conn = null;
        try{
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
            while (currentNodeId > 0) { //not deleting the root node (index 0)
                ArrayTree.TreeNode currentNode = arrayTree.getNode(currentNodeId);
                if (currentNode == null){
                    System.err.println("ERROR: Node " + currentNodeId + " not found in ArrayTree for tree " + treeId + ". Cannot delete empty nodes.");
                    break;
                }
                //decrease the number of docCount
                if (currentNodeId == startingNodeId){
                    currentNode.docCount--;
                }

                //check if current node is empty (0 children 0 docs)
                if (currentNode.docCount <= 0 && currentNode.nodeCount <=0){
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
                }else {
                    System.out.println("Node " + currentNodeId + " (name: '" + dbManager.getNodeNameById(conn, currentNode.idNodeName) + "') is not empty. Ending.");
                    break;
                }
            }
            dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
            System.out.println("Updated tree " + treeId + " after deleting empty nodes. NextFreeIndex: " + arrayTree.getNextFreeIndex());
        }
    }
}