package com.mongo.skunkworks.clusteringengine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class ClusteringResult {

    @JsonProperty("docs")
    List<Document> documents;

    @JsonProperty("clusters")
    List<DocumentCluster> firstLevelClusters;

    @JsonProperty("searchTime")
    long searchTime;

    @JsonProperty("clusterTime")
    long clusterTime;
}
