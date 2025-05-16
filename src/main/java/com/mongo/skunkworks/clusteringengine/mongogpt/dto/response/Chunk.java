package com.mongo.skunkworks.clusteringengine.mongogpt.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Chunk {

    @JsonProperty("chunk_index")
    int index;

    @JsonProperty("chunk_id")
    String id;

    @JsonProperty("text")
    String text;

    @JsonProperty("score")
    double score;
}
