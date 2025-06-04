package ruzicka.treeSupport;

import ruzicka.databaseOprations.DatabaseManager; // For type hint, not direct use unless passed
import java.nio.ByteBuffer;
import java.sql.Connection; // For generateXPath
import java.sql.SQLException; // For generateXPath

public class ArrayTree {
    private int maxNodes = 5; // Initial size
    private TreeNode[] nodes = new TreeNode[maxNodes];
    private int nextFreeIndex = 1; // 0 is typically root, free list starts at 1
    private int currentNodeIndex = 0; // Can be used to track current context during traversal

    public static class TreeNode {
        public int parentId;
        public long idNodeName;
        public long idNodeXPath;
        int nodeCount; // Number of direct child nodes
        public int docCount;  // Number of documents directly associated with this node

        public TreeNode(int parentId, long idNodeName, long idNodeXPath) {
            this.parentId = parentId;
            this.idNodeName = idNodeName;
            this.idNodeXPath = idNodeXPath;
            this.nodeCount = 0;
            this.docCount = 0;
        }
    }

    public void initArrayTree() {
        nodes = new TreeNode[maxNodes]; // Ensure fresh array if called multiple times
        // Root node at index 0
        nodes[0] = new TreeNode(0, 0, 0); // parentId 0 means it's the root (or use -1)
        // idNodeName and idNodeXPath 0 means not yet set

        // Initialize free list
        for (int i = 1; i < maxNodes; i++) {
            nodes[i] = new TreeNode(i + 1, 0, 0); // parentId points to next free node
        }
        if (maxNodes > 1) { // Avoid index out of bounds if maxNodes is 1
            nodes[maxNodes - 1].parentId = -1; // -1 marks end of free list
            nextFreeIndex = 1;
        } else { // Only root node
            nextFreeIndex = -1; // No free nodes if maxNodes is 1 (or 0)
        }
        currentNodeIndex = 0; // Start at root
    }

    private void expandArray() {
        int oldSize = maxNodes;
        int newSize = maxNodes + 5; // Expand by a fixed amount
        TreeNode[] newNodesArray = new TreeNode[newSize];
        System.arraycopy(nodes, 0, newNodesArray, 0, oldSize);

        // Link new free nodes to the end of the old free list
        // Find the last node of the current free list to link the new segment
        if (nextFreeIndex == -1) { // If no free nodes were left
            nextFreeIndex = oldSize; // New free list starts at oldSize
        } else {
            int currentFree = nextFreeIndex;
            int previousFree = -1;
            while(currentFree != -1 && currentFree < oldSize) { // Iterate only within old bounds
                if (nodes[currentFree] == null) { // Should not happen in a consistent free list
                    System.err.println("Error: Null node encountered in free list during expansion at index: " + currentFree);
                    // Attempt to recover or throw error
                    nextFreeIndex = oldSize; // Fallback: start new free list from oldSize
                    break;
                }
                previousFree = currentFree;
                currentFree = nodes[currentFree].parentId;
            }
            if (previousFree != -1 && previousFree < oldSize) { // If free list was not empty
                nodes[previousFree].parentId = oldSize; // Link last old free node to first new free node
            } else if (nextFreeIndex != -1 && nextFreeIndex >= oldSize) {
                // This means nextFreeIndex was already pointing into an expanded (but not yet initialized) area.
                // This state is unusual. For safety, we re-initialize the new segment.
                nextFreeIndex = oldSize;
            } else if (nextFreeIndex == -1 && oldSize > 0){ // No free nodes, but array existed.
                nextFreeIndex = oldSize;
            } else if (oldSize == 0) { // Array was initially empty
                nextFreeIndex = 0; // Or 1 if 0 is always root
            }
        }


        for (int i = oldSize; i < newSize; i++) {
            newNodesArray[i] = new TreeNode(i + 1, 0, 0);
        }
        newNodesArray[newSize - 1].parentId = -1; // End of new free list segment

        nodes = newNodesArray;
        maxNodes = newSize;
        // nextFreeIndex is now the start of the newly added segment if old list was exhausted, or remains the head of combined list.
    }


    public byte[] toByteArray() {
        // Each node: parentId (int), idNodeName (long), idNodeXPath (long), nodeCount (int), docCount (int)
        // 4 + 8 + 8 + 4 + 4 = 28 bytes per node
        ByteBuffer buf = ByteBuffer.allocate(maxNodes * 28);
        for (int i = 0; i < maxNodes; i++) {
            if (nodes[i] != null) {
                buf.putInt(nodes[i].parentId);
                buf.putLong(nodes[i].idNodeName);
                buf.putLong(nodes[i].idNodeXPath);
                buf.putInt(nodes[i].nodeCount);
                buf.putInt(nodes[i].docCount);
            } else {
                // Should not happen if array is properly initialized/expanded
                // Write zeros for null nodes to maintain structure if absolutely necessary,
                // but it indicates an issue.
                buf.putInt(0);
                buf.putLong(0L);
                buf.putLong(0L);
                buf.putInt(0);
                buf.putInt(0);
            }
        }
        return buf.array();
    }

    public void fromByteArray(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) {
            initArrayTree(); // Initialize if byte array is empty
            return;
        }
        // Determine maxNodes from byteArray length
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
        // nextFreeIndex needs to be restored separately from DM_Tree.ixFreeNode by the caller
    }

    public int insertNode(int parentId, long idNodeName, long idNodeXPath) {
        if (nextFreeIndex == -1) {
            expandArray();
            if (nextFreeIndex == -1) { // Still no free index after expansion (should not happen)
                throw new IllegalStateException("Failed to expand array or find free index.");
            }
        }

        int newNodeId = nextFreeIndex;
        TreeNode newNode = nodes[newNodeId]; // Get the node from the free list
        nextFreeIndex = newNode.parentId;   // Advance free list pointer

        // Initialize the new node
        newNode.parentId = parentId;
        newNode.idNodeName = idNodeName;
        newNode.idNodeXPath = idNodeXPath;
        newNode.nodeCount = 0;
        newNode.docCount = 0;

        // Update parent's child count if parent is valid and not the node itself
        if (parentId >= 0 && parentId < maxNodes && nodes[parentId] != null && parentId != newNodeId) {
            nodes[parentId].nodeCount++;
        }
        return newNodeId;
    }

    public void deleteNode(int nodeId) {
        if (nodeId < 0 || nodeId >= maxNodes || nodes[nodeId] == null) {
            System.err.println("Attempt to delete invalid node ID: " + nodeId);
            return;
        }
        // TODO: Recursive deletion of children or re-parenting might be needed.
        // For now, simple deletion.

        TreeNode nodeToDelete = nodes[nodeId];
        int parentId = nodeToDelete.parentId;

        if (parentId >= 0 && parentId < maxNodes && nodes[parentId] != null && parentId != nodeId) {
            nodes[parentId].nodeCount--;
        }

        // Add to free list
        nodeToDelete.parentId = nextFreeIndex;
        nodeToDelete.idNodeName = 0;
        nodeToDelete.idNodeXPath = 0;
        nodeToDelete.nodeCount = 0;
        nodeToDelete.docCount = 0;
        nextFreeIndex = nodeId;
    }

    public String generateXpath(int nodeId, Connection conn, DatabaseManager dbManager) throws SQLException {
        if (nodeId < 0 || nodeId >= maxNodes || nodes[nodeId] == null) {
            throw new IllegalArgumentException("generateXpath: Node with ID " + nodeId + " is invalid or null. MaxNodes: " + maxNodes);
        }

        // Handle root node (index 0) explicitly
        if (nodeId == 0) {
            TreeNode rootNode = nodes[0];
            if (rootNode.idNodeName != 0) {
                String rootName = dbManager.getNodeNameById(conn, rootNode.idNodeName);
                return "/" + (rootName == null ? "" : rootName);
            } else if (rootNode.idNodeXPath != 0) { // If name not set, but XPath ID is (e.g. from persistence)
                return dbManager.getNodeXPathById(conn, rootNode.idNodeXPath);
            }
            return "/"; // Default for unnamed, un-XPathed root
        }

        StringBuilder xpath = new StringBuilder();
        int currentId = nodeId;

        while (currentId >= 0 && currentId < maxNodes && nodes[currentId] != null) {
            TreeNode currentNode = nodes[currentId];
            if (currentNode.idNodeName != 0) {
                String nodeName = dbManager.getNodeNameById(conn, currentNode.idNodeName);
                xpath.insert(0, "/" + (nodeName == null ? "ERROR_NULL_NAME" : nodeName));
            } else {
                // Node has no name, this part of path will be problematic or indicate unnamed segment
                // For example, if a node only has an XPath ID but no name ID
                if(currentNode.idNodeXPath != 0 && currentId == nodeId) { // If it's the target node and has XPath directly
                    return dbManager.getNodeXPathById(conn, currentNode.idNodeXPath);
                }
                xpath.insert(0, "/_UNNAMED_NODE_ID_" + currentId + "_"); // Placeholder for unnamed node
                System.err.println("Warning: Node " + currentId + " in XPath has no idNodeName.");
            }

            if (currentId == 0 || currentNode.parentId == currentId) { // Reached root or self-parented node
                break;
            }
            currentId = currentNode.parentId;
        }

        if (xpath.length() == 0) { // Should only happen if initial nodeId was problematic and not root
            if (nodeId == 0) return "/"; // Safety for root
            throw new IllegalStateException("Could not generate XPath for nodeId " + nodeId + ". Path is empty.");
        }

        return xpath.toString();
    }


    // Getters and Setters
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
        // Potentially re-initialize or adjust nodes array if size changes significantly
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