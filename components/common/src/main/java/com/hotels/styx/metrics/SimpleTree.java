package com.hotels.styx.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static java.util.Arrays.asList;

/**
 * A tree that can map dot-separated names to values.
 * If part of a name (i.e. the text between two dots, or before the first dot, or after the last dot)
 * is an asterisk only, it is a wildcard.
 */
public class SimpleTree<E> {
    private final Node<E> rootNode = new Node<>();

    public void add(String name, E value) {
        List<String> split = asList(name.split("\\."));

        Node<E> node = rootNode;
        for (int i = 0; i < split.size(); i++) {
            boolean last = i == split.size() - 1;

            node = node.addOrGetChild(split.get(i), last, value);
        }
    }

    public boolean exists(String name) {
        List<String> split = asList(name.split("\\."));

        return rootNode.matches(split);
    }

    public Optional<E> valueOf(String name) {
        List<String> split = asList(name.split("\\."));

        return rootNode.valueOf(split);
    }

    public void walk(BiConsumer<String, E> entryConsumer) {
        rootNode.walk("", entryConsumer);
    }

    public void walkNonWildcards(BiConsumer<String, E> entryConsumer) {
        rootNode.walkNonWildcards("", entryConsumer);
    }

    @Override
    public String toString() {
        return rootNode.toString();
    }

    private static class Node<E> {
        private boolean terminalNode;
        private Map<String, Node<E>> children;
        private E value;
        // note: a node can be terminal and also have children
        // terminal nodes are just those that match names

        Node<E> addOrGetChild(String childName, boolean terminal, E value) {
            // we do it here instead of in the constructor to save us from creating a load of hashmaps that will never be used
            if (children == null) {
                children = new HashMap<>();
            }

            Node<E> node = children.get(childName);

            if (node == null) {
                node = new Node<>();
                children.put(childName, node);
            }

            // don't set false if already set to true by a previous call
            if (terminal) {
                node.terminalNode = true;
                node.value = value;
            }

            return node;
        }

        boolean matches(List<String> namePartsRemaining) {
            if (namePartsRemaining.isEmpty()) {
                return terminalNode;
            }

            if (children == null) {
                return false;
            }

            String nextNamePart = namePartsRemaining.get(0);
            List<String> subsequentNameParts = namePartsRemaining.subList(1, namePartsRemaining.size());

            Node exactChild = children.get(nextNamePart);

            boolean exact = exactChild != null && exactChild.matches(subsequentNameParts);

            if (exact) {
                return true;
            }

            Node wildcardChild = children.get("*");

            return wildcardChild != null && wildcardChild.matches(subsequentNameParts);
        }

        Optional<E> valueOf(List<String> namePartsRemaining) {
            if (namePartsRemaining.isEmpty()) {
                return Optional.ofNullable(value);
            }

            if (children == null) {
                return Optional.empty();
            }

            String nextNamePart = namePartsRemaining.get(0);
            List<String> subsequentNameParts = namePartsRemaining.subList(1, namePartsRemaining.size());

            Node<E> exactChild = children.get(nextNamePart);

            Optional<E> onExact = Optional.ofNullable(exactChild)
                    .flatMap(child -> child.valueOf(subsequentNameParts));

            if (onExact.isPresent()) {
                return onExact;
            }

            Node<E> wildcardChild = children.get("*");

            return Optional.ofNullable(wildcardChild)
                    .flatMap(child -> child.valueOf(subsequentNameParts));
        }

        void walk(String name, BiConsumer<String, E> entryConsumer) {
            if (terminalNode) {
                entryConsumer.accept(name, value);
            }

            if (children != null) {
                children.forEach((key, node) ->
                        node.walk(prefix(name) + key, entryConsumer));
            }
        }

        void walkNonWildcards(String name, BiConsumer<String, E> entryConsumer) {
            if (terminalNode) {
                entryConsumer.accept(name, value);
            }

            if (children != null) {
                children.forEach((key, node) -> {
                    if (!"*".equals(key)) {
                        node.walkNonWildcards(prefix(name) + key, entryConsumer);
                    }
                });
            }
        }

        @NotNull
        private static String prefix(String name) {
            return name.isEmpty() ? "" : name + ".";
        }

        @Override
        public String toString() {
            // TODO more intuitive output
            return "(terminal=" + terminalNode
                    + ",children=" + (children == null ? "{}" : children)
                    + ",value=" + value
                    + ")";
        }
    }
}
