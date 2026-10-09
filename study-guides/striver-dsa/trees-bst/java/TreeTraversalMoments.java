import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Demonstrates the three meaningful processing moments in one recursive DFS. */
public final class TreeTraversalMoments {
    private TreeTraversalMoments() {}

    public static final class Node {
        public final int value;
        public Node left;
        public Node right;

        public Node(int value) {
            this.value = value;
        }
    }

    public record Orders(
            List<Integer> preorder,
            List<Integer> inorder,
            List<Integer> postorder) {}

    public static Orders collect(Node root) {
        List<Integer> preorder = new ArrayList<>();
        List<Integer> inorder = new ArrayList<>();
        List<Integer> postorder = new ArrayList<>();
        collect(root, preorder, inorder, postorder);
        return new Orders(
                List.copyOf(preorder),
                List.copyOf(inorder),
                List.copyOf(postorder));
    }

    private static void collect(
            Node node,
            List<Integer> preorder,
            List<Integer> inorder,
            List<Integer> postorder) {
        if (node == null) {
            return;
        }

        preorder.add(node.value);             // enter: before either child
        collect(node.left, preorder, inorder, postorder);
        inorder.add(node.value);              // between left and right
        collect(node.right, preorder, inorder, postorder);
        postorder.add(node.value);            // exit: after both children
    }

    public static List<Integer> levelOrder(Node root) {
        if (root == null) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            result.add(node.value);
            if (node.left != null) {
                queue.add(node.left);
            }
            if (node.right != null) {
                queue.add(node.right);
            }
        }
        return List.copyOf(result);
    }

    /** Empty tree has height -1; a leaf has height 0. */
    public static int heightInEdges(Node node) {
        if (node == null) {
            return -1;
        }
        return 1 + Math.max(heightInEdges(node.left), heightInEdges(node.right));
    }
}
