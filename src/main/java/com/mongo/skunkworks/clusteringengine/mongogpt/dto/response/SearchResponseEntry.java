package com.mongo.skunkworks.clusteringengine.mongogpt.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

import static java.util.stream.Collectors.joining;

@Data
public class SearchResponseEntry {

    @JsonProperty("document_id")
    String id;

    @JsonProperty("document_name")
    String name;

    @JsonProperty("document_url")
    String url;

    @JsonProperty("chunks")
    List<Chunk> chunks;

    public String getText() {
        var sb = new StringBuilder(name + "\n");
        for (Chunk chunk : chunks) {
            sb.append(chunk.getText()).append("\n");
        }
        return sb.toString();
    }

    public String getSnippet() {
        return "<br>" + chunks.stream().map(Chunk::getText).collect(joining("<br><br>")) + "<br>";
    }
}
