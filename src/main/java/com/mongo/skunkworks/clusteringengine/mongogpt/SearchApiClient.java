package com.mongo.skunkworks.clusteringengine.mongogpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongo.skunkworks.clusteringengine.auth.KanopyClient;
import com.mongo.skunkworks.clusteringengine.mongogpt.dto.request.SearchRequest;
import com.mongo.skunkworks.clusteringengine.mongogpt.dto.response.SearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.StopWatch;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static java.net.URI.create;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class SearchApiClient {
    private static final String MONGO_GPT_HOST = "https://mongogpt.corp.mongodb.com";
    private static final String SEARCH_API_URL = "/api/v1/search";
    private static final String KANOPY_HTTP_HEADER = "X-Kanopy-Authorization";
    private static final String TECHDOCS_COLLECTION = "techdocs";

    private static final String LOCAL_SEARCH_RESPONSE_CACHE_FOLDER_NAME = "search-response-cache";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final Path localCacheFolder = Paths.get(LOCAL_SEARCH_RESPONSE_CACHE_FOLDER_NAME);

    static {
        if (!Files.exists(localCacheFolder)) {
            try {
                Files.createDirectory(localCacheFolder);
            } catch (Exception e) {
                throw new ExceptionInInitializerError("Failed to create local cache folder: " + e.getMessage());
            }
        }
    }

    public static SearchResponse search(String query, int limit) throws Exception {
        query = query.trim();
        String base64EncodedQuery = Base64.getEncoder().encodeToString(query.getBytes());
        var cacheFolder = localCacheFolder.resolve(base64EncodedQuery);
        var cacheFile = cacheFolder.resolve(limit + ".json");
        if (cacheFile.toFile().exists()) {
            return OBJECT_MAPPER.readValue(cacheFile.toFile(), SearchResponse.class);
        }
        var token = KanopyClient.getToken();
        var url = MONGO_GPT_HOST + SEARCH_API_URL;
        var searchRequest = SearchRequest.builder().question(query).scoreThreshold(0.1).limit(limit).collection(TECHDOCS_COLLECTION).build();
        var requestBody = OBJECT_MAPPER.writeValueAsString(searchRequest);
        var request = HttpRequest.newBuilder()
            .uri(create(url))
            .header(KANOPY_HTTP_HEADER, "Bearer " + token)
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .POST(ofString(requestBody))
            .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new RuntimeException("Failed to search: " + response.body());
        }
        var responseBody = response.body();
        if (!cacheFolder.toFile().exists()) {
            Files.createDirectory(cacheFolder);
        }
        Files.write(cacheFile, responseBody.getBytes());
        return OBJECT_MAPPER.readValue(responseBody, SearchResponse.class);
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 2; i++) {
            var stopWatch = new StopWatch();
            stopWatch.start();
            var response = SearchApiClient.search("sharding", 20);
            System.out.println(response);
            stopWatch.stop();
            System.out.println("Done. Time spent: " + stopWatch.getTotalTimeMillis());
        }
    }
}
