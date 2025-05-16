package com.mongo.skunkworks.clusteringengine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

@Data
public class DocumentCluster implements Comparable<DocumentCluster> {
    private String label;

    private BitSet docIndexes;

    private List<DocumentCluster> subClusters;

    @JsonProperty("label")
    public String getLabel() {
        return label;
    }

    @JsonProperty("docs")
    public List<Integer> getDocs() {
        List<Integer> result = new ArrayList<>();
        for (int i = docIndexes.nextSetBit(0); i >= 0; i = docIndexes.nextSetBit(i + 1)) {
            result.add(i);
        }
        return result;
    }

    @JsonProperty("children")
    public List<DocumentCluster> getChildren() {
        return subClusters;
    }

    public DocumentCluster(String label, BitSet docIndexes) {
        this.label = label;
        this.docIndexes = docIndexes;
    }

    public int docsCount() {
        return docIndexes.cardinality();
    }

    @Override
    public int compareTo(DocumentCluster that) {
        return that.docsCount() - this.docsCount();
    }
}
