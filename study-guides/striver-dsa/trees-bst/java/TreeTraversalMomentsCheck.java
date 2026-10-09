import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class TreeTraversalMomentsCheck {
    private static long assertions;

    public static void main(String[] args) {
        explicitCases();
        randomizedOracleChecks();
        System.out.println("PASS TreeTraversalMomentsCheck assertions=" + assertions);
    }

    private static void explicitCases() {
        checkOrders(null, List.of(), List.of(), List.of(), List.of(), -1);

        TreeTraversalMoments.Node one = new TreeTraversalMoments.Node(7);
        checkOrders(one, List.of(7), List.of(7), List.of(7), List.of(7), 0);

        TreeTraversalMoments.Node root = sampleTree();
        checkOrders(
                root,
                List.of(1, 2, 4, 5, 3, 6),
                List.of(4, 2, 5, 1, 3, 6),
                List.of(4, 5, 2, 6, 3, 1),
                List.of(1, 2, 3, 4, 5, 6),
                2);

        TreeTraversalMoments.Node chain = new TreeTraversalMoments.Node(1);
        chain.right = new TreeTraversalMoments.Node(2);
        chain.right.right = new TreeTraversalMoments.Node(3);
        chain.right.right.right = new TreeTraversalMoments.Node(4);
        checkOrders(
                chain,
                List.of(1, 2, 3, 4),
                List.of(1, 2, 3, 4),
                List.of(4, 3, 2, 1),
                List.of(1, 2, 3, 4),
                3);
    }

    private static TreeTraversalMoments.Node sampleTree() {
        TreeTraversalMoments.Node root = new TreeTraversalMoments.Node(1);
        root.left = new TreeTraversalMoments.Node(2);
        root.right = new TreeTraversalMoments.Node(3);
        root.left.left = new TreeTraversalMoments.Node(4);
        root.left.right = new TreeTraversalMoments.Node(5);
        root.right.right = new TreeTraversalMoments.Node(6);
        return root;
    }

    private static void randomizedOracleChecks() {
        Random random = new Random(0x7AEE2026L);
        for (int trial = 0; trial < 10_000; trial++) {
            int size = random.nextInt(65);
            TreeTraversalMoments.Node root = randomTree(size, random);
            TreeTraversalMoments.Orders actual = TreeTraversalMoments.collect(root);

            checkEquals(iterativePreorder(root), actual.preorder(), "random preorder");
            checkEquals(iterativeInorder(root), actual.inorder(), "random inorder");
            checkEquals(iterativePostorder(root), actual.postorder(), "random postorder");
            checkEquals(iterativeLevelOrder(root), TreeTraversalMoments.levelOrder(root), "random BFS");
            checkEquals(iterativeHeight(root), TreeTraversalMoments.heightInEdges(root), "random height");
            checkEquals(size, distinctIdentityCount(root), "random reachable count");
        }
    }

    private static TreeTraversalMoments.Node randomTree(int size, Random random) {
        if (size == 0) {
            return null;
        }
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(i - size / 2);
        }
        Collections.shuffle(values, random);

        TreeTraversalMoments.Node root = new TreeTraversalMoments.Node(values.get(0));
        List<TreeTraversalMoments.Node> open = new ArrayList<>();
        open.add(root);
        for (int i = 1; i < size; i++) {
            TreeTraversalMoments.Node child = new TreeTraversalMoments.Node(values.get(i));
            while (true) {
                TreeTraversalMoments.Node parent = open.get(random.nextInt(open.size()));
                boolean preferLeft = random.nextBoolean();
                if (preferLeft && parent.left == null) {
                    parent.left = child;
                    break;
                }
                if (!preferLeft && parent.right == null) {
                    parent.right = child;
                    break;
                }
                if (parent.left == null) {
                    parent.left = child;
                    break;
                }
                if (parent.right == null) {
                    parent.right = child;
                    break;
                }
                open.remove(parent);
            }
            open.add(child);
        }
        return root;
    }

    private static List<Integer> iterativePreorder(TreeTraversalMoments.Node root) {
        if (root == null) return List.of();
        List<Integer> result = new ArrayList<>();
        ArrayDeque<TreeTraversalMoments.Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeTraversalMoments.Node node = stack.pop();
            result.add(node.value);
            if (node.right != null) stack.push(node.right);
            if (node.left != null) stack.push(node.left);
        }
        return result;
    }

    private static List<Integer> iterativeInorder(TreeTraversalMoments.Node root) {
        List<Integer> result = new ArrayList<>();
        ArrayDeque<TreeTraversalMoments.Node> stack = new ArrayDeque<>();
        TreeTraversalMoments.Node current = root;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current);
                current = current.left;
            }
            current = stack.pop();
            result.add(current.value);
            current = current.right;
        }
        return result;
    }

    private static List<Integer> iterativePostorder(TreeTraversalMoments.Node root) {
        if (root == null) return List.of();
        List<Integer> reversed = new ArrayList<>();
        ArrayDeque<TreeTraversalMoments.Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeTraversalMoments.Node node = stack.pop();
            reversed.add(node.value);
            if (node.left != null) stack.push(node.left);
            if (node.right != null) stack.push(node.right);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static List<Integer> iterativeLevelOrder(TreeTraversalMoments.Node root) {
        if (root == null) return List.of();
        List<Integer> result = new ArrayList<>();
        ArrayDeque<TreeTraversalMoments.Node> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TreeTraversalMoments.Node node = queue.remove();
            result.add(node.value);
            if (node.left != null) queue.add(node.left);
            if (node.right != null) queue.add(node.right);
        }
        return result;
    }

    private static int iterativeHeight(TreeTraversalMoments.Node root) {
        if (root == null) return -1;
        ArrayDeque<TreeTraversalMoments.Node> queue = new ArrayDeque<>();
        queue.add(root);
        int height = -1;
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            height++;
            for (int i = 0; i < levelSize; i++) {
                TreeTraversalMoments.Node node = queue.remove();
                if (node.left != null) queue.add(node.left);
                if (node.right != null) queue.add(node.right);
            }
        }
        return height;
    }

    private static int distinctIdentityCount(TreeTraversalMoments.Node root) {
        if (root == null) return 0;
        Set<TreeTraversalMoments.Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<TreeTraversalMoments.Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeTraversalMoments.Node node = stack.pop();
            check(seen.add(node), "cycle or shared child detected");
            if (node.left != null) stack.push(node.left);
            if (node.right != null) stack.push(node.right);
        }
        return seen.size();
    }

    private static void checkOrders(
            TreeTraversalMoments.Node root,
            List<Integer> preorder,
            List<Integer> inorder,
            List<Integer> postorder,
            List<Integer> levelOrder,
            int height) {
        TreeTraversalMoments.Orders actual = TreeTraversalMoments.collect(root);
        checkEquals(preorder, actual.preorder(), "explicit preorder");
        checkEquals(inorder, actual.inorder(), "explicit inorder");
        checkEquals(postorder, actual.postorder(), "explicit postorder");
        checkEquals(levelOrder, TreeTraversalMoments.levelOrder(root), "explicit BFS");
        checkEquals(height, TreeTraversalMoments.heightInEdges(root), "explicit height");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void checkEquals(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
