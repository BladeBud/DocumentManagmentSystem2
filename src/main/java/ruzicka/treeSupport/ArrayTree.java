package ruzicka.treeSupport;

import ruzicka.databaseOprations.DatabaseManager;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Represents a tree structure using an array-based implementation.
 * Provides methods for node manipulation, serialization, and XPath generation.
 */
public class ArrayTree {
    private int maxNodes = 5;
    private TreeNode[] nodes = new TreeNode[maxNodes];
    private int nextFreeIndex = 1;
    private int currentNodeIndex = 0;

    /**
     * Represents a single node in the ArrayTree.
     */
    public static class TreeNode {
        public int parentId;        // Index of the parent node; 0 or -1 for root/special cases.
        public long idNodeName;     // ID from DM_NodeName table.
        public long idNodeXPath;    // ID from DM_NodeXPath table.
        public int nodeCount;       // Number of direct child nodes in this ArrayTree instance.
        public int docCount;        // Number of documents directly associated with this node.

        /**
         * Constructs a TreeNode.
         *
         * @param parentId    Index of the parent.
         * @param idNodeName  ID of the node's name.
         * @param idNodeXPath ID of the node's XPath.
         */
        public TreeNode(int parentId, long idNodeName, long idNodeXPath) {
            this.parentId = parentId;
            this.idNodeName = idNodeName;
            this.idNodeXPath = idNodeXPath;
            this.nodeCount = 0;
            this.docCount = 0;
        }
    }

    /**
     * Initializes the ArrayTree with a root node and a free list.
     * The root node is at index 0.
     */
    public void initArrayTree() {
        nodes = new TreeNode[maxNodes];
        nodes[0] = new TreeNode(0, 0, 0); // Root node. parentId 0 can mean self-parented root.

        for (int i = 1; i < maxNodes; i++) {
            nodes[i] = new TreeNode(i + 1, 0, 0); // parentId points to next free node.
        }
        if (maxNodes > 1) {
            nodes[maxNodes - 1].parentId = -1; // -1 marks end of free list.
            nextFreeIndex = 1;
        } else {
            nextFreeIndex = -1;
        }
        currentNodeIndex = 0;
    }

    /**
     * Expands the internal array holding tree nodes when the free list is exhausted.
     */
    private void expandArray() {
        int oldSize = maxNodes;
        int newSize = maxNodes + 5;
        TreeNode[] newNodesArray = new TreeNode[newSize];
        System.arraycopy(nodes, 0, newNodesArray, 0, oldSize);

        int lastFreeLink = -1;
        if (nextFreeIndex == -1) { // If old free list was empty
            lastFreeLink = oldSize; // New free list starts here
        } else { // Find end of old free list to append new free nodes
            int current = nextFreeIndex;
            while (nodes[current].parentId != -1 && nodes[current].parentId < oldSize) { // Traverse old free list
                current = nodes[current].parentId;
            }
            // 'current' is now the last node of the old free list segment
            nodes[current].parentId = oldSize; // Link it to the start of the new segment
            lastFreeLink = nextFreeIndex; // Original head of free list remains the same unless it was empty
        }

        for (int i = oldSize; i < newSize; i++) {
            newNodesArray[i] = new TreeNode(i + 1, 0, 0);
        }
        newNodesArray[newSize - 1].parentId = -1; // End of new free list segment.

        nodes = newNodesArray;
        maxNodes = newSize;
        if (nextFreeIndex == -1) nextFreeIndex = lastFreeLink; // Restore head if old list was empty
    }


    /**
     * Serializes the current state of the ArrayTree into a byte array.
     * Node structure: parentId (int), idNodeName (long), idNodeXPath (long), nodeCount (int), docCount (int).
     *
     * @return Byte array representation of the tree.
     */
    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(maxNodes * 28); // 4+8+8+4+4 = 28 bytes per node
        for (int i = 0; i < maxNodes; i++) {
            if (nodes[i] != null) {
                buf.putInt(nodes[i].parentId);
                buf.putLong(nodes[i].idNodeName);
                buf.putLong(nodes[i].idNodeXPath);
                buf.putInt(nodes[i].nodeCount);
                buf.putInt(nodes[i].docCount);
            } else { // Should ideally not happen if properly managed
                buf.putInt(0);
                buf.putLong(0L);
                buf.putLong(0L);
                buf.putInt(0);
                buf.putInt(0);
            }
        }
        return buf.array();
    }

    /**
     * Deserializes a byte array into the ArrayTree structure.
     *
     * @param byteArray The byte array containing the serialized tree.
     *                  The `nextFreeIndex` must be restored separately from DM_Tree.ixFreeNode.
     */
    public void fromByteArray(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) {
            initArrayTree();
            return;
        }
        this.maxNodes = byteArray.length / 28;
        this.nodes = new TreeNode[this.maxNodes];
        ByteBuffer buf = ByteBuffer.wrap(byteArray);

        for (int i = 0; i < this.maxNodes; i++) {
            int parentId = buf.getInt();
            long idNodeName = buf.getLong();
            long idNodeXPath = buf.getLong();
            int nodeCount = buf.getInt();
            int docCount = buf.getInt();

            nodes[i] = new TreeNode(parentId, idNodeName, idNodeXPath);
            nodes[i].nodeCount = nodeCount;
            nodes[i].docCount = docCount;
        }
        // NOTE: nextFreeIndex is NOT part of the byte array.
        // It must be loaded from DM_Tree.ixFreeNode and set via setNextFreeIndex().
    }

    /**
     * Inserts a new node into the tree.
     *
     * @param parentId    Index of the parent node for the new node.
     * @param idNodeName  ID of the name for the new node.
     * @param idNodeXPath ID of the XPath for the new node (can be 0 if not yet determined).
     *
     * @return The index of the newly inserted node.
     *
     * @throws IllegalStateException if array expansion fails.
     */
    public int insertNode(int parentId, long idNodeName, long idNodeXPath) {
        if (nextFreeIndex == -1) {
            expandArray();
            if (nextFreeIndex == -1) {
                throw new IllegalStateException("Failed to expand array or find free index.");
            }
        }

        int newNodeId = nextFreeIndex;
        TreeNode newNodeToUse = nodes[newNodeId];
        nextFreeIndex = newNodeToUse.parentId; // Move to next free node in the list

        // Re-initialize the node taken from the free list
        newNodeToUse.parentId = parentId;
        newNodeToUse.idNodeName = idNodeName;
        newNodeToUse.idNodeXPath = idNodeXPath;
        newNodeToUse.nodeCount = 0;
        newNodeToUse.docCount = 0;

        if (parentId >= 0 && parentId < maxNodes && nodes[parentId] != null && parentId != newNodeId) {
            nodes[parentId].nodeCount++;
        }
        return newNodeId;
    }

    /**
     * Deletes a node from the tree by adding it to the free list.
     * Note: This is a simple delete; it does not handle recursive deletion of children or re-parenting.
     *
     * @param nodeId The index of the node to delete.
     */
    public void deleteNode(int nodeId) {
        if (nodeId < 0 || nodeId >= maxNodes || nodes[nodeId] == null) {
            System.err.println("Attempt to delete invalid node ID: " + nodeId);
            return;
        }

        TreeNode nodeToDelete = nodes[nodeId];
        int parentIdx = nodeToDelete.parentId;

        // Decrement parent's child count if applicable
        if (parentIdx >= 0 && parentIdx < maxNodes && nodes[parentIdx] != null && parentIdx != nodeId) {
            nodes[parentIdx].nodeCount--;
        }

        // Add node to the head of the free list
        nodeToDelete.parentId = nextFreeIndex; // Points to the old head of the free list
        nodeToDelete.idNodeName = 0;
        nodeToDelete.idNodeXPath = 0;
        nodeToDelete.nodeCount = 0;
        nodeToDelete.docCount = 0;
        nextFreeIndex = nodeId; // This node is now the new head of the free list
    }

    /**
     * Generates an XPath string for a given node ID.
     * Requires database access to resolve node name IDs.
     *
     * @param nodeId    The index of the node for which to generate the XPath.
     * @param conn      The active database connection.
     * @param dbManager An instance of DatabaseManager to fetch node names.
     *
     * @return The generated XPath string.
     *
     * @throws SQLException             If a database error occurs or node names cannot be resolved.
     * @throws IllegalArgumentException If nodeId is invalid.
     */
    public String generateXpath(int nodeId, Connection conn, DatabaseManager dbManager) throws SQLException {
        if (nodeId < 0 || nodeId >= maxNodes || nodes[nodeId] == null) {
            throw new IllegalArgumentException("generateXpath: Node with ID " + nodeId + " is invalid or null. MaxNodes: " + maxNodes);
        }

        TreeNode targetNode = nodes[nodeId];
        // If the node itself has a fully formed XPath ID, prefer that.
        if (targetNode.idNodeXPath != 0) {
            try {
                // Attempt to fetch pre-stored XPath. This might be from a previous generation.
                // This assumes idNodeXPath stores the ID of the *full* path.
                // If idNodeXPath is only for the node's own segment, this logic needs change.
                return dbManager.getNodeXPathById(conn, targetNode.idNodeXPath);
            } catch (SQLException e) {
                System.err.println("WARN: Could not fetch pre-stored XPath for idNodeXPath " + targetNode.idNodeXPath + ". Will attempt to generate. Error: " + e.getMessage());
            }
        }

        StringBuilder xpath = new StringBuilder();
        int currentId = nodeId;

        // Traverse up to the root (node 0 or self-parented node)
        while (currentId >= 0 && currentId < maxNodes && nodes[currentId] != null) {
            TreeNode currentNode = nodes[currentId];
            if (currentNode.idNodeName != 0) {
                String nodeName = dbManager.getNodeNameById(conn, currentNode.idNodeName);
                xpath.insert(0, "/" + (nodeName == null ? "_ERR_NAME_" : nodeName));
            } else if (currentId == 0) { // Root node without a specific name
            } else {
                xpath.insert(0, "/_UNNAMED_ID_" + currentId + "_");
                System.err.println("Warning: Node " + currentId + " in XPath generation has no idNodeName.");
            }

            if (currentId == 0 || currentNode.parentId == currentId) { // Reached root or self-parented node
                break;
            }
            if (currentNode.parentId < 0 || currentNode.parentId >= maxNodes) { // Invalid parent, stop
                System.err.println("Warning: Node " + currentId + " has invalid parentId " + currentNode.parentId + " during XPath generation.");
                break;
            }
            currentId = currentNode.parentId;
        }

        if (xpath.length() == 0) {
            return "/"; // Default for empty path (e.g. if root itself is requested and unnamed)
        }
        return xpath.toString();
    }

    //----Getters and Setters--------------------------------------------------------------------------------------------------
    public TreeNode[] getNodes() {
        return nodes;
    }

    public TreeNode getNode(int index) {
        if (index >= 0 && index < maxNodes) {
            return nodes[index];
        }
        return null;
    }

    public int getMaxNodes() {
        return maxNodes;
    }

    public ArrayTree setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
        return this;
    }

    public int getNextFreeIndex() {
        return nextFreeIndex;
    }

    public ArrayTree setNextFreeIndex(int nextFreeIndex) {
        this.nextFreeIndex = nextFreeIndex;
        return this;
    }

    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public ArrayTree setCurrentNodeIndex(int currentNodeIndex) {
        this.currentNodeIndex = currentNodeIndex;
        return this;
    }
}