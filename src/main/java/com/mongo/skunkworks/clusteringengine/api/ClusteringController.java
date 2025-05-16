package com.mongo.skunkworks.clusteringengine.api;

import com.mongo.skunkworks.clusteringengine.algorithm.SuffixTree;
import com.mongo.skunkworks.clusteringengine.api.dto.ClusteringResult;
import com.mongo.skunkworks.clusteringengine.api.dto.Document;
import com.mongo.skunkworks.clusteringengine.mongogpt.SearchApiClient;
import com.mongo.skunkworks.clusteringengine.mongogpt.dto.response.SearchResponseEntry;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Set;

import static java.nio.file.Files.lines;
import static java.util.stream.Collectors.toSet;

@RestController
public class ClusteringController {

    private final Set<String> stopWords;

    public ClusteringController(ResourceLoader resourceLoader) throws IOException {
        try (var lines = lines(resourceLoader.getResource("classpath:stopwords.txt").getFile().toPath())) {
            stopWords = lines.collect(toSet());
        }
    }

    @PostMapping("/clusters")
    public ClusteringResult cluster(@RequestParam String query, @RequestParam(required = false, defaultValue = "100") int limit) throws Exception {
        var stopWatch = new StopWatch();
        stopWatch.start();
        var searchResponse = SearchApiClient.search(query, limit).getResponse();
        var documents = searchResponse.stream().map(Document::new).toList();
        stopWatch.stop();
        var searchTime = stopWatch.getTotalTimeMillis();

        var texts = searchResponse.stream().map(SearchResponseEntry::getText).toList().toArray(new String[0]);

        stopWatch = new StopWatch();
        stopWatch.start();
        var suffixTree = new SuffixTree(stopWords, texts);
        var clusters = suffixTree.cluster();
        stopWatch.stop();
        var clusterTime = stopWatch.getTotalTimeMillis();

        return new ClusteringResult(documents, clusters, searchTime, clusterTime);
    }
}
