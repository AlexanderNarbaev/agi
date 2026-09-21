package io.matrix.neuron;

import java.util.HashMap;
import java.util.Map;

/**
 * RUN 433 — Trie (prefix tree) over string keys.
 * <p>Insert + find + startsWith + longestCommonPrefix. Each node is a
 * map from {@code char → child}. Pure function but mutable (no thread safety).
 * CONSTITUTION I-safe (no Random).
 */
public final class Trie {

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        boolean isLeaf;
    }

    private final Node root = new Node();

    public void insert(String word) {
        Node n = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            n = n.children.computeIfAbsent(c, k -> new Node());
        }
        n.isLeaf = true;
    }

    public boolean contains(String word) {
        Node n = root;
        for (int i = 0; i < word.length(); i++) {
            n = n.children.get(word.charAt(i));
            if (n == null) return false;
        }
        return n.isLeaf;
    }

    public boolean startsWith(String prefix) {
        Node n = root;
        for (int i = 0; i < prefix.length(); i++) {
            n = n.children.get(prefix.charAt(i));
            if (n == null) return false;
        }
        return true;
    }

    /**
     * Longest common prefix of all keys inserted. Useful for autocompletion.
     */
    public String longestCommonPrefix() {
        StringBuilder sb = new StringBuilder();
        Node walk = root;
        while (walk.children.size() == 1 && !walk.isLeaf) {
            char c = walk.children.keySet().iterator().next();
            sb.append(c);
            walk = walk.children.get(c);
        }
        return sb.toString();
    }

    public int size() { return nodeCount(root); }

    private int nodeCount(Node n) {
        if (n == null) return 0;
        int s = 1;
        for (Node c : n.children.values()) s += nodeCount(c);
        return s;
    }
}
