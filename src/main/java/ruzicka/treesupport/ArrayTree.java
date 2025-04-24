package ruzicka.treesupport;

/**
 * @author Adam
 * @since 2025-04-22
 */
public class ArrayTree {
    //-----------------------------------------------------------------------------------------------
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

    //----init Array Tree-----------------------------------------------------------------------------------------------
    public void initArrayTree() {

        nodes[0] = new TreeNode(0, 0, 0);

        for (int i = 1; i < maxNodes; i++) {
            nodes[i] = new TreeNode(i + 1, 0, 0); // Free list pointer
        }
        nodes[maxNodes - 1].parentId = -1;
        nextFreeIndex = 1;
    }

    //----expand Array Tree-----------------------------------------------------------------------------------------------
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
}
