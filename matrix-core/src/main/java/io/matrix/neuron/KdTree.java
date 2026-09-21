package io.matrix.neuron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RUN 420 — K-Dimensional tree for nearest-neighbour queries.
 * <p>Recursively subdivides the k-D space along alternating axes. Builds in
 * O(n log n) median-split; queries in O(log n) for well-distributed points.
 * <p>Pure function: no Random. Cyclic axis selection (depth % k). CONSTITUTION I-safe.
 */
public final class KdTree {

    private final double[][] pts;
    private final Node root;
    private final int size;

    private KdTree(double[][] pts, Node root) {
        this.pts = pts;
        this.root = root;
        this.size = pts.length;
    }

    public int size() { return size; }

    public static KdTree of(double[][] points) {
        int n = points.length;
        if (n == 0) return new KdTree(points, null);
        int k = points[0].length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        // Sort indices by axis 0 to bootstrap
        Arrays.sort(idx, (a, b) -> Double.compare(points[a][0], points[b][0]));
        Node root = build(points, idx, 0, idx.length, 0, k);
        return new KdTree(points, root);
    }

    private static Node build(double[][] points, Integer[] idx, int lo, int hi, int depth, int k) {
        if (lo >= hi) return null;
        int axis = depth % k;
        int mid = (lo + hi) / 2;
        Arrays.sort(idx, lo, hi, (a, b) -> Double.compare(points[a][axis], points[b][axis]));
        Node node = new Node(idx[mid], axis);
        node.left = build(points, idx, lo, mid, depth + 1, k);
        node.right = build(points, idx, mid + 1, hi, depth + 1, k);
        return node;
    }

    /** k-nearest neighbours by index. */
    public int[] nearest(double[] target, int howMany) {
        if (root == null) return new int[0];
        int[] result = new int[howMany];
        double[] resultDist = new double[howMany];
        int[] found = {0};
        Arrays.fill(resultDist, Double.POSITIVE_INFINITY);
        nearestRec(root, target, howMany, result, resultDist, found);
        int[] out = new int[found[0]];
        System.arraycopy(result, 0, out, 0, found[0]);
        return out;
    }

    private void nearestRec(Node n, double[] t, int k, int[] result, double[] dist, int[] found) {
        if (n == null) return;
        double d = sqDist(pts[n.idx], t);
        if (found[0] < k || d < dist[found[0] - 1]) {
            int pos = found[0] < k ? found[0] : found[0] - 1;
            while (pos > 0 && dist[pos - 1] > d) {
                dist[pos] = dist[pos - 1];
                result[pos] = result[pos - 1];
                pos--;
            }
            dist[pos] = d;
            result[pos] = n.idx;
            if (found[0] < k) found[0]++;
        }
        double axisDiff = t[n.axis] - pts[n.idx][n.axis];
        Node near = axisDiff < 0 ? n.left : n.right;
        Node far = axisDiff < 0 ? n.right : n.left;
        nearestRec(near, t, k, result, dist, found);
        if (found[0] < k || axisDiff * axisDiff < dist[found[0] - 1]) {
            nearestRec(far, t, k, result, dist, found);
        }
    }

    private static double sqDist(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) { double d = a[i] - b[i]; s += d * d; }
        return s;
    }

    private static class Node {
        final int idx;
        final int axis;
        Node left, right;
        Node(int idx, int axis) { this.idx = idx; this.axis = axis; }
    }
}
