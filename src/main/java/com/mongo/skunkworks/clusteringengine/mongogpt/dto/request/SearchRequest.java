package com.mongo.skunkworks.clusteringengine.mongogpt.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SearchRequest {

    @JsonProperty("question")
    String question;

    @JsonProperty("document_collection_name")
    String collection;

    @JsonProperty("max_chunks")
    @Builder.Default
    Integer limit = 20;

    @JsonProperty("score_threshold")
    @Builder.Default
    Double scoreThreshold = 0.8;
}
