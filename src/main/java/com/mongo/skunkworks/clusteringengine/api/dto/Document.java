package com.mongo.skunkworks.clusteringengine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongo.skunkworks.clusteringengine.mongogpt.dto.response.SearchResponseEntry;
import lombok.Value;

@Value
public class Document {

    @JsonProperty("title")
    String title;

    @JsonProperty("url")
    String url;

    @JsonProperty("snippet")
    String snippet;

    public Document(SearchResponseEntry searchResponseEntry) {
        this.title = searchResponseEntry.getName();
        this.url = searchResponseEntry.getUrl();
        this.snippet = searchResponseEntry.getSnippet();
    }
}
