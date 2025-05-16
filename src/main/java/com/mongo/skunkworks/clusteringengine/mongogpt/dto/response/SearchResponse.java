package com.mongo.skunkworks.clusteringengine.mongogpt.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {

    @JsonProperty("response")
    List<SearchResponseEntry> response;
}
