package ruzicka.handlers;

import ruzicka.creators.DocCreator;
import ruzicka.databaseOprations.DatabaseManager;
import ruzicka.treeSupport.ArrayTree;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
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

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/DMSdb";
    private static final String DB_USER = "bladebud";
    private static final String DB_PASSWORD = "44DM5";

    private DatabaseManager dbManager;
    private DocCreator docCreator;

    public DocHandler() {
        this.dbManager = new DatabaseManager();
        this.docCreator = new DocCreator();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

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
     * Processes a single document for a specific tree by recursively placing it.
     * @param conn Active database connection.
     * @param idDoc ID of the document.
     * @param treeId ID of the tree.
     * @param attributeMap Map of the document's attributes.
     * @param idDocType ID of the document's type.
     * @throws SQLException If database errors occur.
     */
    private void processDocumentForTree(Connection conn, long idDoc, int treeId,
                                        Map<String, String> attributeMap, int idDocType) throws SQLException {

        int rootDefNodeIdForThisTree = getRootDefTreeNodeId(conn, treeId);

        if (rootDefNodeIdForThisTree == -1) {
            System.err.println("CRITICAL: No root definition node (idDefParentTreeNode=0) found for treeId: " + treeId + ". Cannot process this tree for doc " + idDoc);
            return;
        }

        // Load or initialize ArrayTree for this tree
        ArrayTree arrayTree = new ArrayTree();
        byte[] treeContentBytes = dbManager.getTreeContent(conn, treeId);
        int nextFreeIxForTree = dbManager.getTreeNextFreeNodeIndex(conn, treeId);

        if (treeContentBytes != null && treeContentBytes.length > 0) {
            System.out.println("Loading ArrayTree for tree " + treeId + " from " + treeContentBytes.length + " bytes. NextFreeIndex from DB: " + nextFreeIxForTree);
            arrayTree.fromByteArray(treeContentBytes);
            arrayTree.setNextFreeIndex(nextFreeIxForTree);
        } else {
            System.out.println("Initializing new ArrayTree for tree " + treeId);
            arrayTree.initArrayTree();
        }

        // Ensure ArrayTree's root node (index 0) is named according to the tree's *defined* root node's script.
        // This makes ArrayTree[0] directly represent the conceptual root of the defined tree structure.
        if (arrayTree.getNode(0) != null && arrayTree.getNode(0).idNodeName == 0) {
            String rootNodeNameScript = null;
            String rootNodeNameSql = "SELECT nodenamescript FROM dm_deftreenode WHERE iddeftreenode = ?";
            try (PreparedStatement ps = conn.prepareStatement(rootNodeNameSql)) {
                ps.setInt(1, rootDefNodeIdForThisTree); // Use the actual root definition ID
                try(ResultSet rs = ps.executeQuery()){
                    if(rs.next()) rootNodeNameScript = rs.getString(1);
                }
            }
            String actualRootNodeName = getNodeNameForDocumentAtLevel(conn, rootNodeNameScript, attributeMap, idDoc, rootDefNodeIdForThisTree);
            if (actualRootNodeName == null || actualRootNodeName.isEmpty()) {
                actualRootNodeName = "_Tree" + treeId + "_DefinedRoot_"; // Fallback name if script yields nothing
                System.err.println("WARN: Root definition script for tree " + treeId + " (def " + rootDefNodeIdForThisTree + ") yielded no name. Using fallback: " + actualRootNodeName);
            }
            arrayTree.getNode(0).idNodeName = dbManager.saveNodeName(conn, actualRootNodeName);
            arrayTree.getNode(0).idNodeXPath = dbManager.saveNodeXPath(conn, "/" + actualRootNodeName);
            System.out.println("Named ArrayTree physical root (index 0) for tree " + treeId + " as: " + actualRootNodeName + " (from def " + rootDefNodeIdForThisTree + ")");
        }

        // Start recursive placement.
        // The rootDefNodeIdForThisTree corresponds to ArrayTree node 0.
        // Children of rootDefNodeIdForThisTree will be placed as children of ArrayTree node 0.
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Starting recursive placement. RootDefID: " + rootDefNodeIdForThisTree + " (corresponds to ArrayTree Node 0).");
        int finalNodeIdInArrayTree = placeDocumentInTreeRecursive(conn, arrayTree, rootDefNodeIdForThisTree, 0, idDoc, attributeMap, treeId);

        System.out.println(">>> Doc " + idDoc + ", Tree " + treeId + ": Placement result from recursion: finalNodeIdInArrayTree = " + finalNodeIdInArrayTree);

        // **CRITICAL CHECK**: If finalNodeIdInArrayTree is 0, it means the document did not meet the
        // include condition of the tree's root definition node (or any of its children).
        // In this specific case, we might decide NOT to create dm_docnode/dm_docxpath entries,
        // as it means the document doesn't belong in the tree's defined structure.
        // However, if ArrayTree node 0 *is* a valid placement target (e.g. root def condition true), then proceed.
        // The `placeDocumentInTreeRecursive` returns the parent if the current def's condition is false.
        // So, if the root def's condition is false, it would return the parent of 0 (which is not well-defined here,
        // conceptually "outside the tree"). The current `placeDocumentInTreeRecursive` needs to handle this.
        // The simplest check: did the document actually land in a node *created or designated by a definition*
        // whose include condition was met?

        // If finalNodeIdInArrayTree is still 0, we need to re-check if doc *actually* belongs in node 0
        // based on rootDefNodeIdForThisTree's own include condition.
        if (finalNodeIdInArrayTree == 0) {
            String rootNodeDocIncludeCondition = null;
            String rootDefDetailsSql = "SELECT docincludecondition FROM dm_deftreenode WHERE iddeftreenode = ?";
            try (PreparedStatement ps = conn.prepareStatement(rootDefDetailsSql)) {
                ps.setInt(1, rootDefNodeIdForThisTree);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) rootNodeDocIncludeCondition = rs.getString("docincludecondition");
                }
            }
            boolean belongsInRootNodeZero = false;
            if (isMeaningfulCondition(rootNodeDocIncludeCondition)) {
                belongsInRootNodeZero = checkDocIncludeCondition(conn, rootNodeDocIncludeCondition, idDoc, attributeMap, rootDefNodeIdForThisTree);
            } else if (rootNodeDocIncludeCondition != null && rootNodeDocIncludeCondition.equalsIgnoreCase("true")) {
                belongsInRootNodeZero = true;
            }
            // Special case: if root has no children and condition is null/empty, it might be a catch-all
            else if (getChildDefIds(conn, treeId, rootDefNodeIdForThisTree).isEmpty() &&
                    (rootNodeDocIncludeCondition == null || rootNodeDocIncludeCondition.trim().isEmpty())) {
                belongsInRootNodeZero = true;
            }


            if (!belongsInRootNodeZero) {
                System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Recursive placement resulted in ArrayTree node 0, " +
                        "but document does not meet the include condition of root definition " + rootDefNodeIdForThisTree +
                        ". Document is NOT considered part of this tree's defined structure.");
                // Save ArrayTree state as it might have been modified by other documents or previous attempts.
                dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
                return; // Do not create dm_docnode or dm_docxpath
            }
            System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Recursive placement resulted in ArrayTree node 0, " +
                    "and document MEETS the include condition of root definition " + rootDefNodeIdForThisTree + ".");
        }


        ArrayTree.TreeNode docFinalNodeInArrayTree = arrayTree.getNode(finalNodeIdInArrayTree);
        if (docFinalNodeInArrayTree == null) { // Should be caught by the check above if finalNodeIdInArrayTree is invalid
            System.err.println("CRITICAL: Final ArrayTree node " + finalNodeIdInArrayTree + " is null (after re-check) for tree " + treeId + ", doc " + idDoc + ". Aborting.");
            return;
        }

        if (docFinalNodeInArrayTree.idNodeXPath == 0) {
            String xpathStr = arrayTree.generateXpath(finalNodeIdInArrayTree, conn, this.dbManager);
            // ... (rest of post-placement processing) ...
            System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Generated XPath for ArrayTree node " + finalNodeIdInArrayTree + ": " + xpathStr);
            long idNodeXPath = dbManager.saveNodeXPath(conn, xpathStr);
            docFinalNodeInArrayTree.idNodeXPath = idNodeXPath;
        }

        dbManager.insertDocXPath(conn, idDoc, docFinalNodeInArrayTree.idNodeXPath);
        docFinalNodeInArrayTree.docCount++;
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Linked doc to XPath ID " + docFinalNodeInArrayTree.idNodeXPath + ". ArrayTree Node " + finalNodeIdInArrayTree + " docCount: " + docFinalNodeInArrayTree.docCount);

        dbManager.insertDocNode(conn, idDoc, finalNodeIdInArrayTree, treeId);
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Inserted into dm_docnode: (idDoc=" + idDoc + ", ixNode=" + finalNodeIdInArrayTree + ", idTree=" + treeId + ")");

        dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
        System.out.println("Doc " + idDoc + ", Tree " + treeId + ": Updated DM_Tree content. NextFreeIndex for ArrayTree: " + arrayTree.getNextFreeIndex());
    }


    /**
     * Recursively places a document into the ArrayTree structure.
     * The `currentParentArrayNodeIdInTree` is the ArrayTree node that acts as the parent
     * for any nodes created based on the `currentDefTreeNodeId`.
     * If `currentDefTreeNodeId` is the tree's root definition, then `currentParentArrayNodeIdInTree`
     * should be ArrayTree's physical root (index 0), and `arrayNodeForThisDefinitionLevel` will also be 0.
     */
    private int placeDocumentInTreeRecursive(Connection conn, ArrayTree arrayTree,
                                             int currentDefTreeNodeId, int currentParentArrayNodeIdInTree,
                                             long idDoc, Map<String, String> attributeMap, int treeId) throws SQLException {

        // ... (Fetch nodeNameScriptFromDB and docIncludeCondition for currentDefTreeNodeId
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

        // This is the ArrayTree node that corresponds to the currentDefTreeNodeId.
        int arrayNodeForThisDefinitionLevel;

        // If currentDefTreeNodeId is THE root definition for this tree,
        // then its corresponding ArrayTree node is index 0.
        if (currentDefTreeNodeId == getRootDefTreeNodeId(conn, treeId)) {
            arrayNodeForThisDefinitionLevel = 0;
            // Name/XPath for arrayTree.getNode(0) should have been set in processDocumentForTree
            // System.out.println("Def " + currentDefTreeNodeId + " (Tree Root Def): Processing against ArrayTree node 0.");
        } else {
            // For non-root definitions, determine/create an ArrayTree node under currentParentArrayNodeIdInTree.
            String actualNodeNameForThisDoc = getNodeNameForDocumentAtLevel(conn, nodeNameScriptFromDB, attributeMap, idDoc, currentDefTreeNodeId);

            if (actualNodeNameForThisDoc != null && !actualNodeNameForThisDoc.isEmpty()) {
                long idNodeName = dbManager.saveNodeName(conn, actualNodeNameForThisDoc);
                arrayNodeForThisDefinitionLevel = findOrInsertArrayTreeNode(arrayTree, currentParentArrayNodeIdInTree, idNodeName, actualNodeNameForThisDoc, "DYNAMIC", currentDefTreeNodeId);
            } else if (nodeNameScriptFromDB != null && nodeNameScriptFromDB.toLowerCase().startsWith("select '")) { // Static name
                String staticName = evaluateStaticScript(conn, nodeNameScriptFromDB);
                if (staticName != null && !staticName.isEmpty()) {
                    long idStaticNodeName = dbManager.saveNodeName(conn, staticName);
                    arrayNodeForThisDefinitionLevel = findOrInsertArrayTreeNode(arrayTree, currentParentArrayNodeIdInTree, idStaticNodeName, staticName, "STATIC", currentDefTreeNodeId);
                } else { // Static script failed
                    // System.out.println("Def " + currentDefTreeNodeId + ": Static script failed. No node formed. Returning parent " + currentParentArrayNodeIdInTree);
                    return currentParentArrayNodeIdInTree;
                }
            } else { // No dynamic name, not static - this definition doesn't form a node for this doc.
                // System.out.println("Def " + currentDefTreeNodeId + ": No ArrayTree node formed. Returning parent " + currentParentArrayNodeIdInTree);
                return currentParentArrayNodeIdInTree;
            }
        }

        // Try to place document into children of the current definition node.
        // The parent for these children in the ArrayTree is arrayNodeForThisDefinitionLevel.
        List<Integer> childDefIds = getChildDefIds(conn, treeId, currentDefTreeNodeId);
        if (!childDefIds.isEmpty()) {
            for (int childDefId : childDefIds) {
                int placementByChild = placeDocumentInTreeRecursive(conn, arrayTree, childDefId, arrayNodeForThisDefinitionLevel, idDoc, attributeMap, treeId);
                if (placementByChild != arrayNodeForThisDefinitionLevel) {
                    return placementByChild;
                }
            }
        }

        // No child placed it deeper OR this is a leaf definition.
        // Check if the document belongs in the node for THIS definition level (arrayNodeForThisDefinitionLevel).
        boolean docBelongsAtThisNode = false;
        if (isMeaningfulCondition(docIncludeCondition)) {
            docBelongsAtThisNode = checkDocIncludeCondition(conn, docIncludeCondition, idDoc, attributeMap, currentDefTreeNodeId);
        } else if (docIncludeCondition != null && docIncludeCondition.equalsIgnoreCase("true")) {
            docBelongsAtThisNode = true;
        } else if (childDefIds.isEmpty() && (docIncludeCondition == null || docIncludeCondition.trim().isEmpty())) {
            // Leaf node with no specific condition (not "false") can act as a catch-all for this path.
            System.out.println("Def " + currentDefTreeNodeId + " (ArrayNode " + arrayNodeForThisDefinitionLevel +", Leaf with no/empty/non-false condition): Assuming doc " + idDoc + " belongs.");
            docBelongsAtThisNode = true;
        }
        // Note: if docIncludeCondition is explicitly "false", docBelongsAtThisNode remains false.

        if (docBelongsAtThisNode) {
            // System.out.println("Doc " + idDoc + " final placement at ArrayTree node " + arrayNodeForThisDefinitionLevel + " (name: " + (arrayTree.getNode(arrayNodeForThisDefinitionLevel)!=null? dbManager.getNodeNameById(conn, arrayTree.getNode(arrayNodeForThisDefinitionLevel).idNodeName) : "ERR_NULL_NODE") + ", def " + currentDefTreeNodeId + ") based on its include condition.");
            return arrayNodeForThisDefinitionLevel;
        } else {
            // System.out.println("Doc " + idDoc + " does not meet include condition for def " + currentDefTreeNodeId + " (ArrayNode " + arrayNodeForThisDefinitionLevel + "). Effective placement remains at " + currentParentArrayNodeIdInTree);
            return currentParentArrayNodeIdInTree;
        }
    }

    // --- Helper Methods (findOrInsertArrayTreeNode, evaluateStaticScript, getChildDefIds, getNodeNameForDocumentAtLevel, checkDocIncludeCondition, isMeaningfulCondition, getRootDefTreeNodeId) ---
    // findOrInsertArrayTreeNode
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
            System.out.println("Inserted new " + type + " ArrayTree node: " + existingNode + " (name: '" + actualName + "', idNodeName: " + idNodeName + ") under parent " + parentArrayNodeId + " (for def " + defId + ")");
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
                System.err.println("WARN: Failed to execute static name script ["+script+"]: " + e.getMessage());
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
        try(PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treeId);
            ps.setInt(2, parentDefNodeId);
            try(ResultSet rs = ps.executeQuery()) {
                while(rs.next()) ids.add(rs.getInt("iddeftreenode"));
            }
        }
        return ids;
    }

    // getNodeNameForDocumentAtLevel
    private String getNodeNameForDocumentAtLevel(Connection conn, String nodeNameScriptFromDB,
                                                 Map<String, String> attributeMap,
                                                 long idDoc, int defId) throws SQLException {
        if (nodeNameScriptFromDB == null || nodeNameScriptFromDB.trim().isEmpty()) {
            return null;
        }

        try (PreparedStatement ps = conn.prepareStatement(nodeNameScriptFromDB)) {
            int paramCount = 0;
            try { paramCount = ps.getParameterMetaData().getParameterCount(); }
            catch (SQLException metaEx) { paramCount = (int) nodeNameScriptFromDB.chars().filter(ch -> ch == '?').count(); }

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
}