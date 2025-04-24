package ruzicka.treeSupport;

import java.nio.ByteBuffer;

/**
 * @author Adam
 * @since 2025-04-22
 */
public class ArrayTree {
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
        int totalSize = maxNodes * 14;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
//TODO: zkontrolvoat short int long jak to ma byt
        for (TreeNode node : nodes) {
            if (node != null) {
                buf.putShort((short) node.parentId);
                buf.putShort((short) node.nodeCount);
                buf.putInt((int) node.idNodeName);
                buf.putInt((int) node.idNodeXPath);
                buf.putShort((short) node.docCount);
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
                short parentId = buf.getShort();
                short nodeCount = buf.getShort();
                int idNodeName = buf.getInt();
                int idNodeXPath = buf.getInt();
                short docCount = buf.getShort();

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


}
