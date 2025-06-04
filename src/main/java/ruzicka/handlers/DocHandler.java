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
import java.util.HashMap; // Explicit import
import java.util.List;
import java.util.Map;

/**
 * @author Adam
 * @since 2025-04-28
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

            Map<String, String> attributeMap = new HashMap<>();
            for (int i = 0; i < docAttrNames.size(); i++) {
                if (docAttrValues.get(i) != null && !docAttrValues.get(i).isEmpty()) { // Only add non-empty attributes
                    attributeMap.put(docAttrNames.get(i), docAttrValues.get(i));
                }
            }

            String treeQuery = "SELECT idtree FROM dm_tree";
            try (PreparedStatement treeStmt = conn.prepareStatement(treeQuery)) {
                try (ResultSet treeRs = treeStmt.executeQuery()) {
                    while (treeRs.next()) {
                        int treeId = treeRs.getInt("idtree");
                        System.out.println("Processing document " + idDoc + " for tree " + treeId);
                        processDocumentForTree(conn, idDoc, treeId, attributeMap, idDocType);
                    }
                }
            }
            conn.commit();
            System.out.println("Successfully added document " + idDoc + " and processed for trees.");
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("Transaction rolled back for document addition.");
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
            }
            e.printStackTrace();
            throw new RuntimeException("Failed to add document and update trees: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processDocumentForTree(Connection conn, long idDoc, int treeId,
                                        Map<String, String> attributeMap, int idDocType) throws SQLException {
        ArrayTree arrayTree = new ArrayTree();
        byte[] treeContentBytes = dbManager.getTreeContent(conn, treeId);
        int nextFreeIxForTree = dbManager.getTreeNextFreeNodeIndex(conn, treeId);

        if (treeContentBytes != null && treeContentBytes.length > 0) {
            System.out.println("Loading ArrayTree for tree " + treeId + " from " + treeContentBytes.length + " bytes.");
            arrayTree.fromByteArray(treeContentBytes);
            arrayTree.setNextFreeIndex(nextFreeIxForTree);
        } else {
            System.out.println("Initializing new ArrayTree for tree " + treeId);
            arrayTree.initArrayTree();
            if (arrayTree.getNode(0) != null && arrayTree.getNode(0).idNodeName == 0) {
                // Ensure root has a name. The name "Root" is conventional.
                // If your dm_deftreenode for root uses a script like 'SELECT ''SpecificRootName''', that would be better.
                String rootDefNodeName = getStaticNodeNameFromScript(conn, treeId, 0); // Helper to get root name
                if (rootDefNodeName == null) rootDefNodeName = "RootTree" + treeId; // Fallback

                arrayTree.getNode(0).idNodeName = dbManager.saveNodeName(conn, rootDefNodeName);
                arrayTree.getNode(0).idNodeXPath = dbManager.saveNodeXPath(conn, "/" + rootDefNodeName);
            }
        }

        int rootDefTreeNodeId = getRootDefTreeNodeId(conn, treeId);

        int finalNodeIdInArrayTree = 0;
        if (rootDefTreeNodeId != -1) {
            System.out.println("Starting document placement from root definition node " + rootDefTreeNodeId + " for tree " + treeId);
            // The initial parentArrayNodeId for the root definition is the ArrayTree's root (index 0)
            finalNodeIdInArrayTree = placeDocumentInTreeRecursive(conn, arrayTree, rootDefTreeNodeId, 0, idDoc, attributeMap, treeId);
        } else {
            System.out.println("No root definition node for tree " + treeId +". Document " + idDoc + " defaults to ArrayTree node 0.");
            finalNodeIdInArrayTree = 0; // Place in ArrayTree root if no definition
        }

        System.out.println("Document " + idDoc + " provisionally placed in ArrayTree node " + finalNodeIdInArrayTree + " for tree " + treeId);

        ArrayTree.TreeNode docFinalNode = arrayTree.getNode(finalNodeIdInArrayTree);
        if (docFinalNode == null) {
            throw new IllegalStateException("Final node " + finalNodeIdInArrayTree + " in ArrayTree is null for tree " + treeId + ". This should not happen.");
        }

        if (docFinalNode.idNodeXPath == 0) { // If XPath not yet set for this node
            String xpathStr = arrayTree.generateXpath(finalNodeIdInArrayTree, conn, this.dbManager);
            System.out.println("Generated XPath for node " + finalNodeIdInArrayTree + ": " + xpathStr);
            long idNodeXPath = dbManager.saveNodeXPath(conn, xpathStr);
            docFinalNode.idNodeXPath = idNodeXPath;
        }

        dbManager.insertDocXPath(conn, idDoc, docFinalNode.idNodeXPath);
        docFinalNode.docCount++;
        System.out.println("Linked doc " + idDoc + " to XPath ID " + docFinalNode.idNodeXPath + ". Node " + finalNodeIdInArrayTree + " docCount: " + docFinalNode.docCount);

        dbManager.updateTree(conn, treeId, arrayTree.toByteArray(), arrayTree.getNextFreeIndex());
        System.out.println("Updated tree " + treeId + " content in database. NextFreeIndex: " + arrayTree.getNextFreeIndex());
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
        return -1; // Not found
    }

    // Helper to get a static node name if the script is like 'SELECT ''NodeName'''
    private String getStaticNodeNameFromScript(Connection conn, int treeId, int defParentNodeId) throws SQLException {
        String scriptSql = "SELECT nodenamescript FROM dm_deftreenode WHERE idtree = ? AND iddefparenttreenode = ?";
        String nodeNameScript = null;
        try (PreparedStatement ps = conn.prepareStatement(scriptSql)){
            ps.setInt(1, treeId);
            ps.setInt(2, defParentNodeId);
            try(ResultSet rs = ps.executeQuery()){
                if(rs.next()){
                    nodeNameScript = rs.getString(1);
                }
            }
        }
        if (nodeNameScript != null && nodeNameScript.toLowerCase().startsWith("select '") && nodeNameScript.endsWith("'")) {
            try (PreparedStatement psName = conn.prepareStatement(nodeNameScript)) {
                try (ResultSet rsName = psName.executeQuery()) {
                    return rsName.next() ? rsName.getString(1) : null;
                }
            } catch (SQLException e) { /* ignore, will return null */ }
        }
        return null;
    }


    private int placeDocumentInTreeRecursive(Connection conn, ArrayTree arrayTree,
                                             int currentDefTreeNodeId, int currentParentArrayNodeId,
                                             long idDoc, Map<String, String> attributeMap, int treeId) throws SQLException {

        // 1. Get definition details: nodeNameScript and docIncludeCondition
        String defDetailsSql = "SELECT nodenamescript, docincludecondition FROM dm_deftreenode WHERE iddeftreenode = ?";
        String nodeNameScript = null;
        String docIncludeCondition = null;
        try (PreparedStatement ps = conn.prepareStatement(defDetailsSql)) {
            ps.setInt(1, currentDefTreeNodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nodeNameScript = rs.getString("nodenamescript");
                    docIncludeCondition = rs.getString("docincludecondition");
                } else {
                    throw new SQLException("Definition node not found by iddeftreenode: " + currentDefTreeNodeId);
                }
            }
        }

        // 2. Determine the actual node name for THIS DOCUMENT at THIS LEVEL
        // This uses the nodeNameScript to figure out *which attribute* of the document defines the name here.
        String actualNodeNameForThisDoc = getNodeNameForDocumentAtLevel(conn, nodeNameScript, attributeMap, idDoc, currentDefTreeNodeId);

        int nextLevelParentArrayNodeId = currentParentArrayNodeId; // By default, stay at current parent

        if (actualNodeNameForThisDoc != null && !actualNodeNameForThisDoc.isEmpty()) {
            long idNodeName = dbManager.saveNodeName(conn, actualNodeNameForThisDoc);
            // Find or create this node in ArrayTree under currentParentArrayNodeId
            int existingNodeInArrayTree = -1;
            for (int i = 0; i < arrayTree.getMaxNodes(); i++) { // Search existing nodes
                ArrayTree.TreeNode node = arrayTree.getNode(i);
                if (node != null && node.parentId == currentParentArrayNodeId && node.idNodeName == idNodeName) {
                    existingNodeInArrayTree = i;
                    break;
                }
            }

            if (existingNodeInArrayTree == -1) { // Node doesn't exist, create it
                nextLevelParentArrayNodeId = arrayTree.insertNode(currentParentArrayNodeId, idNodeName, 0);
                System.out.println("Inserted new ArrayTree node: " + nextLevelParentArrayNodeId + " (name: '" + actualNodeNameForThisDoc + "') under parent " + currentParentArrayNodeId + " (for def " + currentDefTreeNodeId + ")");
            } else { // Node already exists
                nextLevelParentArrayNodeId = existingNodeInArrayTree;
                System.out.println("Found existing ArrayTree node: " + nextLevelParentArrayNodeId + " (name: '" + actualNodeNameForThisDoc + "') under parent " + currentParentArrayNodeId + " (for def " + currentDefTreeNodeId + ")");
            }
        } else {
            // The document does not define a specific sub-node name at this level (e.g., missing attribute)
            // OR this definition level is for a static grouping node (e.g., 'SELECT ''Customers''').
            // If nodeNameScript was 'SELECT ''Customers''', actualNodeNameForThisDoc would be "Customers".
            // If nodeNameScript was to get 'Zakaznik' but doc has no 'Zakaznik', it's null.
            // In this case, nextLevelParentArrayNodeId remains currentParentArrayNodeId for child definitions,
            // UNLESS this level itself is a static node that needs to be created.
            if (nodeNameScript != null && nodeNameScript.toLowerCase().startsWith("select '")) { // Static node name
                String staticName = null;
                try (PreparedStatement psStatic = conn.prepareStatement(nodeNameScript)) {
                    try(ResultSet rsStatic = psStatic.executeQuery()){ if(rsStatic.next()) staticName = rsStatic.getString(1); }
                } catch (SQLException e) { System.err.println("Could not eval static script " + nodeNameScript + ": " + e.getMessage());}

                if (staticName != null && !staticName.isEmpty()) {
                    long idStaticNodeName = dbManager.saveNodeName(conn, staticName);
                    int existingStaticNode = -1;
                    for(int i=0; i < arrayTree.getMaxNodes(); i++){
                        ArrayTree.TreeNode node = arrayTree.getNode(i);
                        if(node != null && node.parentId == currentParentArrayNodeId && node.idNodeName == idStaticNodeName){
                            existingStaticNode = i; break;
                        }
                    }
                    if(existingStaticNode == -1){
                        nextLevelParentArrayNodeId = arrayTree.insertNode(currentParentArrayNodeId, idStaticNodeName, 0);
                        System.out.println("Inserted new STATIC ArrayTree node: " + nextLevelParentArrayNodeId + " (name: '" + staticName + "') under parent " + currentParentArrayNodeId);
                    } else {
                        nextLevelParentArrayNodeId = existingStaticNode;
                        System.out.println("Found existing STATIC ArrayTree node: " + nextLevelParentArrayNodeId + " (name: '" + staticName + "') under parent " + currentParentArrayNodeId);
                    }
                } else {
                    System.out.println("Def " + currentDefTreeNodeId + ": Doc " + idDoc + " did not yield a dynamic node name, and script is not a simple static name. Children will be considered under parent " + currentParentArrayNodeId);
                }
            } else {
                System.out.println("Def " + currentDefTreeNodeId + ": Doc " + idDoc + " did not yield a dynamic node name. Children will be considered under parent " + currentParentArrayNodeId);
            }
        }

        // 3. Check if the document itself belongs AT THIS NEWLY DETERMINED NODE (nextLevelParentArrayNodeId)
        boolean docBelongsAtThisLevelNode = checkDocIncludeCondition(conn, docIncludeCondition, idDoc, attributeMap, currentDefTreeNodeId);
        // System.out.println("Doc " + idDoc + " include condition for def " + currentDefTreeNodeId + " (node " + nextLevelParentArrayNodeId + ") is: " + docBelongsAtThisLevelNode);

        // 4. Recurse for child definitions
        String childDefsSql = "SELECT iddeftreenode FROM dm_deftreenode WHERE idtree = ? AND iddefparenttreenode = ?";
        List<Integer> childDefIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(childDefsSql)) {
            ps.setInt(1, treeId);
            ps.setInt(2, currentDefTreeNodeId); // Children of the *current definition node*
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    childDefIds.add(rs.getInt("iddeftreenode"));
                }
            }
        }

        int finalPlacementNodeId = -1; // Track if any child definition places the document

        if (!childDefIds.isEmpty()) {
            for (int childDefId : childDefIds) {
                // Recursive call: the "parent" for the next level in ArrayTree is `nextLevelParentArrayNodeId`
                int placementByChild = placeDocumentInTreeRecursive(conn, arrayTree, childDefId, nextLevelParentArrayNodeId, idDoc, attributeMap, treeId);

                // If placementByChild is different from nextLevelParentArrayNodeId, it means the document
                // was placed deeper by that child definition.
                if (placementByChild != nextLevelParentArrayNodeId) {
                    finalPlacementNodeId = placementByChild; // Document taken by a child path
                    break; // Assuming first child path that takes the document wins. Adjust if not.
                }
            }
        }

        if (finalPlacementNodeId != -1) {
            // Document was placed by a deeper child definition.
            return finalPlacementNodeId;
        } else {
            // No child definition placed the document further down.
            // So, if the document matches the include condition for *this* level's node, it belongs here.
            if (docBelongsAtThisLevelNode) {
                System.out.println("Doc " + idDoc + " final placement at ArrayTree node " + nextLevelParentArrayNodeId + " (based on def " + currentDefTreeNodeId + " and its include condition).");
                return nextLevelParentArrayNodeId;
            } else {
                // Document does not match this level's include condition and no children took it.
                // This means it effectively "stays" at the parent level *before* this definition was applied.
                // System.out.println("Doc " + idDoc + " did not match include condition for def " + currentDefTreeNodeId + " (node "+nextLevelParentArrayNodeId+") and no children took it. Effective placement remains at: " + currentParentArrayNodeId);
                return currentParentArrayNodeId;
            }
        }
    }


    // This method now needs to interpret nodeNameScript to determine WHICH document attribute to use, or if it's a static name.
    private String getNodeNameForDocumentAtLevel(Connection conn, String nodeNameScriptFromDB,
                                                 Map<String, String> attributeMap, /* attributeMap is now less directly used by this func */
                                                 long idDoc, int defId) throws SQLException {

        if (nodeNameScriptFromDB == null || nodeNameScriptFromDB.trim().isEmpty()) {
            System.out.println("Def " + defId + ": No nodeNameScript provided from DB. No node name derived.");
            return null;
        }

//        System.out.println("Def " + defId + ": Executing DB nodeNameScript: [" + nodeNameScriptFromDB + "]");

        try (PreparedStatement ps = conn.prepareStatement(nodeNameScriptFromDB)) {
            int paramCount = 0;
            try {
                paramCount = ps.getParameterMetaData().getParameterCount();
            } catch (SQLException metaEx) {
                // Fallback if getParameterMetaData fails (e.g. for scripts without '?' or driver issues)
                paramCount = (int) nodeNameScriptFromDB.chars().filter(ch -> ch == '?').count();
                if (paramCount > 0) {
                    System.out.println("Def " + defId + ": Guessed " + paramCount + " parameters from '?' count in script: [" + nodeNameScriptFromDB + "]");
                }
            }

            if (paramCount > 0) {
                // If the script has parameters, assume it's one of the newly modified scripts
                // expecting idDoc as its primary parameter.
                if (paramCount == 1) { // Common case for our modified scripts
                    ps.setLong(1, idDoc);
                    System.out.println("Def " + defId + ": Binding idDoc=" + idDoc + " to parameter 1 of the DB script.");
                } else {
                    // If your scripts for some definition level expect more than one parameter,
                    // you'll need more sophisticated logic here to determine what to bind.
                    System.err.println("WARN: Def " + defId + ": DB nodeNameScript has " + paramCount +
                            " parameters. Current logic only binds idDoc for single-parameter scripts. " +
                            "Script: [" + nodeNameScriptFromDB + "]");
                    // You might throw an error here or attempt a default binding if applicable.
                    // For now, it will proceed and likely fail if other params are unbound.
                }
            } else {
                // Script has no parameters, assume it's static (e.g., SELECT 'Root')
                System.out.println("Def " + defId + ": DB script has no parameters, executing as static.");
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String nodeName = rs.getString(1); // Get the first column
                    if (nodeName == null || nodeName.trim().isEmpty()) {
                        System.out.println("Def " + defId + ": DB script executed but returned null or empty node name for doc " + idDoc + ".");
                        return null;
                    }
                    System.out.println("Def " + defId + ": DB script yielded node name: '" + nodeName + "' for doc " + idDoc);
                    return nodeName;
                } else {
                    // This means the script (e.g., for Zakaznik or Rok for this idDoc) returned no rows.
                    // This is a valid scenario if the document doesn't have that attribute value,
                    // or if the attribute value is empty and your DB query for attributes filters out empty/nulls.
                    System.out.println("Def " + defId + ": DB script executed but returned no rows for doc " + idDoc + ". This means no specific node name for this doc at this level (e.g., doc lacks the 'Zakaznik' or 'Rok' attribute value).");
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR executing DB nodeNameScript for def " + defId + " [" + nodeNameScriptFromDB + "]: " + e.getMessage());
            throw e; // Re-throw to ensure transaction handling (rollback) works correctly
        }
    }


    // checkDocIncludeCondition remains mostly the same, ensures it doesn't break transaction on error
    private boolean checkDocIncludeCondition(Connection conn, String conditionSql, long idDoc, Map<String, String> attributeMap, int defId) throws SQLException {
        if (conditionSql == null || conditionSql.trim().isEmpty() || conditionSql.equalsIgnoreCase("false")) {
            return false;
        }
        if (conditionSql.equalsIgnoreCase("true")) {
            return true;
        }
        // System.out.println("Evaluating docIncludeCondition for def " + defId + ": [" + conditionSql + "]");

        String finalSql = conditionSql;
        finalSql = finalSql.replaceAll(":\\w+\\b", "TRUE");
        finalSql = finalSql.replaceAll("\\b(dm_doc|DM_DOC)\\.iddoc\\b", "d.iddoc");

        String checkSql = "SELECT EXISTS (SELECT 1 FROM dm_doc d WHERE d.iddoc = ? AND (" + finalSql + "))";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setLong(1, idDoc);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            // If the condition itself is malformed or causes a DB error,
            // re-throw to abort the transaction, as the state is unknown.
            System.err.println("ERROR evaluating docIncludeCondition [" + conditionSql + "] for def " + defId + ": " + e.getMessage());
            throw e;
        }
    }
}