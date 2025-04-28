package ruzicka.treeSupport;

import ruzicka.databaseOprations.DatabaseManager;

import java.nio.ByteBuffer;

/**
 * @author Adam
 * @since 2025-04-22
 */
public class ArrayTree {
    //------------------------------------------------------------------------------------------------------------------
    DatabaseManager dbmanager = new DatabaseManager();
    //------------------------------------------------------------------------------------------------------------------
    private int maxNodes = 5;
    private TreeNode[] nodes = new TreeNode[maxNodes];
    private int nextFreeIndex = 1;
    //TODO: toto nemusi byt optimalni pro dlouhodobe spousteni, vyponani, zapinani
    private int currentNodeIndex = 0;

    //----Tree Node class-----------------------------------------------------------------------------------------------
    public static class TreeNode {
        int parentId;
        long idNodeName;
        long idNodeXPath;
        int nodeCount;
        int docCount;

        public TreeNode(int parentId, long idNodeName, long idNodeXPath) {
            this.parentId = parentId;
            this.idNodeName = idNodeName;
            this.idNodeXPath = idNodeXPath;
        }
    }
//----ArrayTree---------------------------------------------------------------------------------------------------------
    //----init Array Tree-----------------------------------------------------------------------------------------------

    /**
     * Initializes the array tree.
     */
    public void initArrayTree() {

        nodes[0] = new TreeNode(0, 0, 0);

        for (int i = 1; i < maxNodes; i++) {
            nodes[i] = new TreeNode(i + 1, 0, 0); // Free list pointer
        }
        nodes[maxNodes - 1].parentId = -1;
        nextFreeIndex = 1;
    }

    //----expand Array Tree---------------------------------------------------------------------------------------------

    /**
     * Expands the array by 5 nodes. Coneects freenode to last node of the old set. So it should be called before reaching the end.
     */
    private void expandArray() {
        int oldSize = maxNodes;
        maxNodes += 5;
        TreeNode[] newNodes = new TreeNode[maxNodes];
        System.arraycopy(nodes, 0, newNodes, 0, oldSize);

        for (int i = oldSize; i < maxNodes; i++) {
            newNodes[i] = new TreeNode(i + 1, 0, 0);
        }
        newNodes[maxNodes - 1].parentId = -1;
        nextFreeIndex = oldSize;
        nodes = newNodes;
    }

    //----toByteArray---------------------------------------------------------------------------------------------------

    /**
     * "encodes" tree content to bytearray
     *
     * @return byte array representing the tree content
     */
    public byte[] toByteArray() {
        int totalSize = maxNodes * 28;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        for (TreeNode node : nodes) {
            if (node != null) {
                buf.putInt((int) node.parentId);
                buf.putInt((int) node.nodeCount);
                buf.putLong((long) node.idNodeName);
                buf.putLong((long) node.idNodeXPath);
                buf.putInt((int) node.docCount);
            }
        }

        return buf.array();
    }

    //----fromByteArray-------------------------------------------------------------------------------------------------

    /**
     * "decodes" bytearray to tree content
     *
     * @param byteArray
     */
    public void fromByteArray(byte[] byteArray) {
        ByteBuffer buf = ByteBuffer.wrap(byteArray);
        for (int i = 0; i < maxNodes; i++) {
            if (nodes[i] != null) {
                int parentId = buf.getInt();
                int nodeCount = buf.getInt();
                long idNodeName = buf.getLong();
                long idNodeXPath = buf.getLong();
                int docCount = buf.getInt();

                nodes[i] = new TreeNode(parentId, idNodeName, idNodeXPath);
                nodes[i].nodeCount = nodeCount;
                nodes[i].docCount = docCount;
            }
        }
    }
    //----Node Operations--------------------------------------------------------------------------------------------------
    //----Insert Node---------------------------------------------------------------------------------------------------

    /**
     * Inserts a new node into the tree structure and returns the ID of the newly created node.
     * If necessary, the array is expanded.
     * The new node is linked to the specified parent node, and parent node's node count is incremented.
     *
     * @param parentId    the ID of the parent node to which the new node will be attached
     * @param idNodeName  the identifier for the name of the new node
     * @param idNodeXPath the identifier for the XPath of the new node
     *
     * @return the ID of the newly created node
     */
    public int insertNode(int parentId, long idNodeName, long idNodeXPath) {
        if (nextFreeIndex == -1) {
            expandArray();
        }

        int newNodeId = nextFreeIndex;
        TreeNode freeNode = nodes[newNodeId];
        nextFreeIndex = freeNode.parentId;

        nodes[newNodeId] = new TreeNode(parentId, idNodeName, idNodeXPath);
        if (parentId != newNodeId) {
            nodes[parentId].nodeCount++;
        }
        return newNodeId;
    }

    //----Delete Node---------------------------------------------------------------------------------------------------

    /**
     * Deletes a node specified by its ID from the tree structure.
     * The node is detached from its parent, and the relevant properties. All set to 0.
     *
     * @param nodeId the ID of the node to be deleted
     */

    public void deleteNode(int nodeId) {
        // checks if the node even exists
        if (nodeId < 0 || nodeId > maxNodes || nodes[nodeId] == null) {
            return;
        }

        int parentId = nodes[nodeId].parentId;
        if (parentId != nodeId && nodes[parentId] != null) {
            nodes[parentId].nodeCount--;
            //TODO: pridat rekurzivitu nebo nejaky clearence na mazani kolik je treba dokud je prazdno
        }

        nodes[nodeId].parentId = nextFreeIndex;
        nodes[nodeId].idNodeName = 0;
        nodes[nodeId].idNodeXPath = 0;
        nodes[nodeId].nodeCount = 0;
        nodes[nodeId].docCount = 0;

        nextFreeIndex = nodeId;
    }
//----Xpath operations--------------------------------------------------------------------------------------------------
    //----generate Xpath------------------------------------------------------------------------------------------------
    public String generateXpath(int nodeId) {
        if (nodeId < 0 || nodeId > maxNodes || nodes[nodeId] == null) {
            throw new IllegalArgumentException("Node with ID " + nodeId + " is invalid. Either does not exist or is out of bounds.");
        }

        //todo check if 0 is root or 1 is root
        if (nodeId == 0) {
            return "/root";
        }

        StringBuilder xpath = new StringBuilder();
        int currentId = nodeId;
        while (currentId != 0) {
            TreeNode node = nodes[currentId];
            if (node.idNodeName == 0) break;
            String nodeName = dbmanager.getNodeNameById(node.idNodeName);
            xpath.insert(0, "/" + nodeName);
            currentId = node.parentId;
        }return xpath.toString();
    }
//----Getters and Setters-----------------------------------------------------------------------------------------------

    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public ArrayTree setCurrentNodeIndex(int currentNodeIndex) {
        this.currentNodeIndex = currentNodeIndex;
        return this;
    }

    public int getNextFreeIndex() {
        return nextFreeIndex;
    }

    public ArrayTree setNextFreeIndex(int nextFreeIndex) {
        this.nextFreeIndex = nextFreeIndex;
        return this;
    }

    public TreeNode[] getNodes() {
        return nodes;
    }

    public int getMaxNodes() {
        return maxNodes;
    }

    public ArrayTree setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
        return this;
    }
}
