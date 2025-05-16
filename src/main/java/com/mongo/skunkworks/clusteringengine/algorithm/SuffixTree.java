package com.mongo.skunkworks.clusteringengine.algorithm;

import com.mongo.skunkworks.clusteringengine.api.dto.DocumentCluster;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

public class SuffixTree {
    public static final String END_SENTINEL = "$";
    public static final int MIN_LABEL_LENGTH = 2;
    public static final int MIN_LABEL_DIFF = 1;
    public static final int MIN_DIFF_FACTOR = 1;
    public static final int MIN_CLUSTER_CAPACITY = 1;
    public static final int MIN_FIRST_LEVEL_CLUSTER_CAPACITY = 2;
    public static final int MIN_FIRST_LEVEL_NODE_LENGTH = 1;
    public static final double MAX_CLUSTER_CAPACITY_RATIO = 0.5;
    public static final int MAX_LABEL_LENGTH = 20;
    public static final int MAX_FIRST_LEVEL_CLUSTER_COUNT = 60;

    private final Set<String> stopWords;
    public Set<String> commonLabels = new HashSet<>();

    public class Node {
        int docIndex;
        int sentenceIndex;
        int startIndex;
        int length;
        String label;
        public Node(int docIndex, int sentenceIndex, int startIndex, int length) {
            this.docIndex = docIndex;
            this.sentenceIndex = sentenceIndex;
            this.startIndex = startIndex;
            this.length = length;
        }
        @Override
        public String toString() {
            if (label != null) {
                return label;
            }
            StringBuilder sb = new StringBuilder();
            String[] words = context[docIndex][sentenceIndex];
            for (int offset = 0; offset < length; offset++) {
                if (offset > 0) {
                    sb.append(" ");
                }
                sb.append(words[startIndex + offset]);
            }
            label = sb.toString();
            return label;
        }
        public String getWord(int index) {
            return context[docIndex][sentenceIndex][index];
        }
    }

    private void recalculateContainingDocs(InternalNode node) {
        BitSet bs = new BitSet();
        for (int i = 0; i < context.length; i++) {
            bs.set(i);
        }
        String[] words = context[node.docIndex][node.sentenceIndex];
        for (int offset = 0; offset < node.length; offset++) {
            String word = words[node.startIndex + offset];
            Node aNode = map.get(word + " ");
            if (aNode instanceof InternalNode) {
                bs.and(((InternalNode) aNode).docIndexes);
            }
        }
        node.docIndexes = bs;
    }
    public class InternalNode extends Node implements Comparable<InternalNode> {
        BitSet docIndexes;
        InternalNode suffixLink;
        InternalNode prefixLink;
        boolean isValid = true;
        List<InternalNode> prefixes;
        List<InternalNode> suffixes;

        public InternalNode(int docIndex, int sentenceIndex, int startIndex, int length) {
            super(docIndex, sentenceIndex, startIndex, length);
        }
        public DocumentCluster toCluster() {
            String label = toString();
            List<DocumentCluster> subClusters = new ArrayList<>((prefixes == null ? 0 : prefixes.size()) + (suffixes == null ? 0 : suffixes.size()));

            if (prefixes != null) {
                for (InternalNode prefix : prefixes) {
                    if (!prefix.isValid || !labelIsValid(prefix, true)) {
                        continue;
                    }
                    if (prefix.docsCount() < MIN_CLUSTER_CAPACITY) {
                        continue;
                    }
                    String subLabel = prefix.toString();
                    if (subLabel.length() >= label.length() + MIN_LABEL_DIFF) {
                        DocumentCluster subCluster = prefix.toCluster();
                        String displayLabel = subLabel.substring(0, subLabel.length() - label.length());
                        if (displayLabel.length() <= MAX_LABEL_LENGTH) {
                            subClusters.add(subCluster);
                        }
                    }
                }
            }
            if (suffixes != null) {
                for (InternalNode suffix : suffixes) {
                    if (!suffix.isValid || !labelIsValid(suffix, false)) {
                        continue;
                    }
                    recalculateContainingDocs(suffix);
                    if (suffix.docsCount() < MIN_CLUSTER_CAPACITY) {
                        continue;
                    }
                    String subLabel = suffix.toString();
                    if (subLabel.length() >= label.length() + MIN_LABEL_DIFF) {
                        DocumentCluster subCluster = suffix.toCluster();
                        String displayLabel = subLabel.substring(label.length());
                        if (displayLabel.length() <= MAX_LABEL_LENGTH) {
                            subClusters.add(subCluster);
                        }
                    }
                }
            }
            DocumentCluster cluster = new DocumentCluster(label, docIndexes);
            if (!subClusters.isEmpty()) {
                Collections.sort(subClusters);
                BitSet allClusters = new BitSet();
                for (DocumentCluster subCluster : subClusters) {
                    allClusters.or(subCluster.getDocIndexes());
                }
                BitSet others = (BitSet) docIndexes.clone();
                others.andNot(allClusters);
                if (others.cardinality() > 0) {
                    DocumentCluster otherCluster = new DocumentCluster("Others", others);
                    subClusters.add(otherCluster);
                }
                cluster.setSubClusters(subClusters);
            }
            return cluster;
        }
        public int docsCount() {
            return docIndexes.cardinality();
        }
        @Override
        public int compareTo(InternalNode that) {
            return that.docsCount() - this.docsCount();
        }
    }

    private final String[][][] context;
    private final Map<String, Node> map = new HashMap<>();

    public SuffixTree(Set<String> stopWords, String[] texts) {
        this.stopWords = stopWords;
        context = new String[texts.length][][];
        for (int textIdx = 0; textIdx < texts.length; textIdx++) {
            var sentences = splitIntoSentences(texts[textIdx]);
            context[textIdx] = new String[sentences.length][];
            for (int sentenceIndex = 0; sentenceIndex < sentences.length; sentenceIndex++) {
                String sentence = sentences[sentenceIndex];
                var words = splitIntoWords(sentence);
                context[textIdx][sentenceIndex] = new String[words.length + 1];
                System.arraycopy(words, 0, context[textIdx][sentenceIndex], 0, words.length);
                context[textIdx][sentenceIndex][words.length] = END_SENTINEL;
            }
        }
    }

    private static String[] splitIntoSentences(String document) {
        return document.split("[^\\w\\s]+");
    }

    private static String[] splitIntoWords(String sentence) {
        return sentence.trim().toLowerCase().split("[\\s]+");
    }

    public List<DocumentCluster> cluster() {
        buildSuffixTree();
        return postProcess();
    }
    private void buildSuffixTree() {
        StringBuilder keyBuilder = new StringBuilder();
        // for every document
        for (int docIndex = 0; docIndex < context.length; docIndex++) {
            String[][] sentences = context[docIndex];
            // for every sentence
            for (int sentenceIndex = 0; sentenceIndex < sentences.length; sentenceIndex++) {
                String[] sentence = sentences[sentenceIndex];
                // for every suffix
                InternalNode lastInternalNode = null;
                for (int startIndex = 0; startIndex < sentence.length; startIndex++) {
                    int matchedWordsCount = 0;
                    InternalNode internalNode = null;

                    keyBuilder.setLength(0);

                    if (lastInternalNode != null && lastInternalNode.suffixLink != null) { // follow suffixLink to speed up inserting
                        internalNode = lastInternalNode.suffixLink;
                        matchedWordsCount = internalNode.length;
                        for (int i = 0; i < matchedWordsCount; i++) {
                            keyBuilder.append(sentence[startIndex + i]).append(" ");
                        }
                        internalNode.docIndexes.set(docIndex);
                    }

                    // start matching leaf nodes or internal nodes
                    while (startIndex + matchedWordsCount < sentence.length) {
                        String key = keyBuilder + sentence[startIndex + matchedWordsCount] + " ";
                        Node node = map.get(key);
                        if (node == null) {
                            break;
                        }
                        String[] nodeSentence = context[node.docIndex][node.sentenceIndex];
                        int nodeStartIndex = node.startIndex;
                        keyBuilder.append(sentence[startIndex + matchedWordsCount]).append(" ");
                        matchedWordsCount++;
                        // try to match as much as possible
                        while (matchedWordsCount < node.length
                                && startIndex + matchedWordsCount < sentence.length
                                && !nodeSentence[nodeStartIndex + matchedWordsCount].equals(END_SENTINEL)
                                && sentence[startIndex + matchedWordsCount].equals(nodeSentence[nodeStartIndex + matchedWordsCount])
                        ) {
                            keyBuilder.append(sentence[startIndex + matchedWordsCount]).append(" ");
                            matchedWordsCount++;
                        }

                        if (matchedWordsCount == node.length) { // total match
                            internalNode = (InternalNode) node;
                        } else { // partly match
                            InternalNode aNode = new InternalNode(docIndex, sentenceIndex, startIndex, matchedWordsCount);
                            BitSet aDocs = new BitSet();
                            if (node instanceof InternalNode) {
                                aDocs.or(((InternalNode) node).docIndexes);
                            } else {
                                aDocs.set(node.docIndex);
                            }
                            aNode.docIndexes = aDocs;
                            map.put(key, aNode);

                            aNode.prefixLink = internalNode;

                            internalNode = aNode;
                            if (!nodeSentence[nodeStartIndex + matchedWordsCount].equals(END_SENTINEL)) {
                                String aKey = keyBuilder + nodeSentence[nodeStartIndex + matchedWordsCount] + " ";
                                map.put(aKey, node);
                                if (node instanceof InternalNode) {
                                    ((InternalNode) node).prefixLink = aNode;
                                }
                            }
                        }
                        internalNode.docIndexes.set(docIndex);
                        if (lastInternalNode != null && lastInternalNode.suffixLink == null && lastInternalNode.length - 1 == internalNode.length) { // link suffixLink and prefixLink
                            lastInternalNode.suffixLink = internalNode;
                        }
                        if (matchedWordsCount < node.length) {
                            break;
                        }
                    }
                    // now matching process ended, start to insert new leaf node
                    // accommodate room in map for new leaf node
                    if (!sentence[startIndex + matchedWordsCount].equals(END_SENTINEL)) {
                        Node aNode = new Node(docIndex, sentenceIndex, startIndex, sentence.length - startIndex);
                        String aKey = keyBuilder + sentence[startIndex + matchedWordsCount] + " ";
                        map.put(aKey, aNode);
                    }
                    lastInternalNode = internalNode;
                }
            }
        }
    }


    private List<DocumentCluster> postProcess() {
        for (Node node : map.values()) {
            if (node instanceof InternalNode inode) {
                if (inode.length < MIN_FIRST_LEVEL_NODE_LENGTH) {
                    inode.isValid = false;
                }
                int docsCount = inode.docsCount();
                if (docsCount > context.length * MAX_CLUSTER_CAPACITY_RATIO) {
                    commonLabels.add(inode.toString());
                }
                if (inode.isValid) {
                    String label = inode.toString();
                    if (label.length() < MIN_LABEL_LENGTH) {
                        inode.isValid = false;
                    }
                }
                InternalNode prefixLink = inode.prefixLink;
                if (prefixLink != null && prefixLink.isValid) {
                    int prefixDocsCount = prefixLink.docsCount();
                    if (prefixDocsCount - docsCount < MIN_DIFF_FACTOR) {
                        prefixLink.isValid = false;
                    }
                }
                InternalNode suffixLink = inode.suffixLink;
                if (suffixLink != null && suffixLink.isValid) {
                    int suffixDocsCount = suffixLink.docsCount();
                    if (suffixDocsCount - docsCount < MIN_DIFF_FACTOR) {
                        suffixLink.isValid = false;
                    }
                }
            }
        }

        List<InternalNode> firstLevelNodes = new ArrayList<>();
        for (Node node : map.values()) {
            if (node instanceof InternalNode inode) {
                if (inode.isValid) {
                    InternalNode validNode = (InternalNode) node;
                    InternalNode prefixNode = validNode.prefixLink;
                    while (prefixNode != null && !prefixNode.isValid) {
                        prefixNode = prefixNode.prefixLink;
                    }
                    if (prefixNode != null) {
                        if (prefixNode.suffixes == null) {
                            prefixNode.suffixes = new LinkedList<>();
                        }
                        prefixNode.suffixes.add(validNode);
                    }
                    InternalNode suffixNode = validNode.suffixLink;
                    while (suffixNode != null && !suffixNode.isValid) {
                        suffixNode = suffixNode.suffixLink;
                    }
                    if (suffixNode != null) {
                        if (suffixNode.prefixes == null) {
                            suffixNode.prefixes = new LinkedList<>();
                        }
                        suffixNode.prefixes.add(validNode);
                    }
                    if (prefixNode == null && suffixNode == null) {
                        String label = validNode.toString();
                        if (label.length() >= MIN_LABEL_LENGTH) {
                            if (validNode.docsCount() <= context.length * MAX_CLUSTER_CAPACITY_RATIO && validNode.docsCount() >= MIN_FIRST_LEVEL_CLUSTER_CAPACITY) {
                                firstLevelNodes.add(validNode);
                            }
                        }
                    }
                }
            }

        }
        Collections.sort(firstLevelNodes);
        List<DocumentCluster> firstLevelClusters = new ArrayList<>();
        for (int i = 0; i < firstLevelNodes.size() && firstLevelClusters.size() < MAX_FIRST_LEVEL_CLUSTER_COUNT; i++) {
            InternalNode firstLevelNode = firstLevelNodes.get(i);
            if (labelIsValid(firstLevelNode, true) && labelIsValid(firstLevelNode, false)) {
                DocumentCluster firstLevelCluster = firstLevelNodes.get(i).toCluster();
                if (firstLevelCluster.getSubClusters() != null && !firstLevelCluster.getSubClusters().isEmpty()) {
                    firstLevelClusters.add(firstLevelCluster);
                }
            }
        }
        return firstLevelClusters;
    }

    private boolean labelIsValid(InternalNode node, boolean left) {
        String[] words = context[node.docIndex][node.sentenceIndex];
        String word = left ? words[node.startIndex] : words[node.startIndex + node.length - 1];
        return !stopWords.contains(word);
    }
    private void displayCluster(DocumentCluster cluster, int depth) {
        for (int i = 0; i < depth; i++) {
            System.out.print("\t");
        }
        System.out.println(cluster.getLabel() + "(" + cluster.docsCount() + ")");
        List<DocumentCluster> subClusters = cluster.getSubClusters();
        if (subClusters != null && !subClusters.isEmpty()) {
            for (DocumentCluster subCluster : cluster.getSubClusters()) {
                displayCluster(subCluster, depth + 1);
            }
        }
    }

    public static void main(String[] args) {
        String[] texts = {
                "spring data mongoDB",
                "mongoDB cluster",
                "redis data mongodb",
                "Mongodb cluster training"
        };
        SuffixTree suffixTree = new SuffixTree(emptySet(), texts);
        List<DocumentCluster> clusters = suffixTree.cluster();
        for (DocumentCluster cluster : clusters) {
            suffixTree.displayCluster(cluster, 0);
        }
    }
}
