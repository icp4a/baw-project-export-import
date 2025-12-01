package com.ibm.baw.migrator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ibm.baw.migrator.model.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Client for interacting with IBM BAW Repository REST APIs
 */
public class BAWApiClient {
    private static final Logger logger = LoggerFactory.getLogger(BAWApiClient.class);
    private static final int MAX_RETRY_ATTEMPTS = 1; // Retry once on 401
    
    private final String baseUrl;
    private final String authHeader;
    private String csrfToken;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    /**
     * Constructor that automatically obtains a CSRF token
     */
    public BAWApiClient(String baseUrl, String username, String password) throws IOException {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String auth = username + ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = createInsecureHttpClient();
        
        // Automatically obtain CSRF token
        this.csrfToken = obtainCsrfToken();
        logger.info("Successfully obtained CSRF token");
    }

    /**
     * Create an HTTP client that trusts all SSL certificates (for self-signed certificates)
     */
    private CloseableHttpClient createInsecureHttpClient() {
        try {
            // Create SSL context that trusts all certificates
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(new TrustAllStrategy())
                    .build();

            // Create SSL socket factory with the custom SSL context
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE);

            // Create connection manager with the SSL socket factory
            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            // Build and return the HTTP client
            return HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            logger.warn("Failed to create insecure HTTP client, falling back to default: {}", e.getMessage());
            return HttpClients.createDefault();
        }
    }

    /**
     * Obtain a CSRF token from the /system/login endpoint
     */
    private String obtainCsrfToken() throws IOException {
        String url = baseUrl + "/bpm/system/login";
        logger.info("Obtaining CSRF token from: {}", url);
        
        HttpPost request = new HttpPost(url);
        request.setHeader("Authorization", authHeader);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        
        // Create login request body
        LoginRequest loginRequest = new LoginRequest();
        String requestBody = objectMapper.writeValueAsString(loginRequest);
        request.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody;
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response", e);
            }
            
            if (response.getCode() != 201 && response.getCode() != 200) {
                throw new IOException("Failed to obtain CSRF token. Status: " + response.getCode() +
                                    ", Response: " + responseBody);
            }
            
            CsrfTokenResponse tokenResponse = objectMapper.readValue(responseBody, CsrfTokenResponse.class);
            if (tokenResponse.getCsrfToken() == null || tokenResponse.getCsrfToken().isEmpty()) {
                throw new IOException("CSRF token is empty in response");
            }
            
            return tokenResponse.getCsrfToken();
        }
    }

    /**
     * Functional interface for HTTP request execution
     */
    @FunctionalInterface
    private interface HttpRequestExecutor<T> {
        T execute() throws IOException;
    }

    /**
     * Execute an HTTP request with automatic retry on 401 (CSRF token expiration)
     * If a 401 is received, obtains a new CSRF token and retries the request once.
     *
     * @param executor The function that executes the HTTP request
     * @param <T> The return type of the request
     * @return The result of the HTTP request
     * @throws IOException If the request fails after retry
     */
    private <T> T executeWithRetry(HttpRequestExecutor<T> executor) throws IOException {
        try {
            return executor.execute();
        } catch (IOException e) {
            // Check if this is a 401 error indicating CSRF token expiration
            if (e.getMessage() != null && e.getMessage().contains("Status: 403")) {
                logger.warn("Received 403 response, CSRF token may have expired. Obtaining new token and retrying...");
                
                // Obtain a new CSRF token
                this.csrfToken = obtainCsrfToken();
                logger.info("Successfully obtained new CSRF token, retrying request");
                
                // Retry the request with the new token
                return executor.execute();
            }
            
            // If not a 401 error, rethrow the original exception
            throw e;
        }
    }

    /**
     * Get all projects from the repository
     */
    public ProjectsResponse getProjects() throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/dba/studio/repo/projects?type=processapp,app,casesolution,general,decision,agent,content,digitalworker,automation_srvc";
            logger.info("Fetching projects from: {}", url);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("Accept", "application/json");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 200) {
                    throw new IOException("Failed to get projects. Status: " + response.getCode() + ", Response: " + responseBody);
                }
                
                return objectMapper.readValue(responseBody, ProjectsResponse.class);
            }
        });
    }

    /**
     * Get a specific project by ID
     */
    public Project getProject(String projectId) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/dba/studio/repo/projects/" + projectId;
            logger.info("Fetching project: {}", projectId);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("Accept", "application/json");
            request.setHeader("repositoryId", "platformRepo");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 200) {
                    throw new IOException("Failed to get project. Status: " + response.getCode() + ", Response: " + responseBody);
                }
                
                return objectMapper.readValue(responseBody, Project.class);
            }
        });
    }

    /**
     * Get all branches for a project
     */
    public BranchesResponse getBranches(String projectId) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/dba/studio/repo/projects/" + projectId + "/branches";
            logger.info("Fetching branches for project: {}", projectId);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("Accept", "application/json");
            request.setHeader("repositoryId", "platformRepo");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 200) {
                    throw new IOException("Failed to get branches. Status: " + response.getCode() + ", Response: " + responseBody);
                }
                
                return objectMapper.readValue(responseBody, BranchesResponse.class);
            }
        });
    }

    /**
     * Get all snapshots for a project branch
     */
    public SnapshotsResponse getSnapshots(String projectId, String branchName) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/dba/studio/repo/projects/" + projectId + "/branches/" + branchName + "/snapshots";
            logger.info("Fetching snapshots for project: {}, branch: {}", projectId, branchName);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("Accept", "application/json");
            request.setHeader("repositoryId", "platformRepo");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 200) {
                    throw new IOException("Failed to get snapshots. Status: " + response.getCode() + ", Response: " + responseBody);
                }
                
                return objectMapper.readValue(responseBody, SnapshotsResponse.class);
            }
        });
    }

    /**
     * Export a snapshot to a file
     */
    public File exportSnapshot(String projectId, String branchName, String snapshotName, File outputDir) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/dba/studio/repo/projects/" + projectId + "/branches/" + branchName +
                         "/snapshots/" + snapshotName + "/export";
            logger.info("Exporting snapshot: {} from project: {}, branch: {}", snapshotName, projectId, branchName);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("repositoryId", "platformRepo");
            request.setHeader("accept", "application/octet-stream");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getCode() != 200) {
                    String responseBody;
                    try {
                        responseBody = EntityUtils.toString(response.getEntity());
                    } catch (org.apache.hc.core5.http.ParseException e) {
                        throw new IOException("Failed to parse error response", e);
                    }
                    throw new IOException("Failed to export snapshot. Status: " + response.getCode() + ", Response: " + responseBody);
                }
                
                // Create output file
                String fileName = projectId + "_" + snapshotName + ".twx";
                File outputFile = new File(outputDir, fileName);
                
                // Write response to file
                try (InputStream inputStream = response.getEntity().getContent();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                logger.info("Exported snapshot to: {}", outputFile.getAbsolutePath());
                return outputFile;
            }
        });
    }

    /**
     * Import a project from a file
     */
    public Project importProject(File file) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/dba/studio/repo/projects/import";
            logger.info("Importing project from file: {}", file.getName());
            
            HttpPost request = new HttpPost(url);
            request.setHeader("Authorization", authHeader);
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            // Build multipart entity
            HttpEntity entity = MultipartEntityBuilder.create()
                    .addPart("import_file", new FileBody(file))
                    .build();
            request.setEntity(entity);
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 201 && response.getCode() != 200) {
                    throw new IOException("Failed to import project. Status: " + response.getCode() + ", Response: " + responseBody);
                }
                
                logger.info("Successfully imported project from: {}", file.getName());
                return objectMapper.readValue(responseBody, Project.class);
            }
        });
    }

    /**
     * Get snapshot details with dependencies
     * Uses the Artifact Management API endpoint
     */
    public Snapshot getSnapshotWithDependencies(String containerAcronym, String versionAcronym) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/artmgt/std/bpm/containers/" + containerAcronym +
                         "/versions/" + versionAcronym + "?optional_parts=dependencies";
            logger.info("Fetching snapshot with dependencies: container={}, version={}", containerAcronym, versionAcronym);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("Accept", "application/json");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 200) {
                    throw new IOException("Failed to get snapshot with dependencies. Status: " +
                                        response.getCode() + ", Response: " + responseBody);
                }
                
                return objectMapper.readValue(responseBody, Snapshot.class);
            }
        });
    }

    /**
     * Get the full dependency tree for a snapshot using the what_used endpoint
     * This is more efficient than recursive calls as it returns the complete tree
     */
    public WhatUsedResponse getWhatUsed(String containerAcronym, String versionAcronym) throws IOException {
        return executeWithRetry(() -> {
            String url = baseUrl + "/artmgt/std/bpm/containers/" + containerAcronym +
                         "/versions/" + versionAcronym + "/what_used?optional_parts=advanced_info";
            logger.info("Fetching dependency tree (what_used): container={}, version={}", containerAcronym, versionAcronym);
            
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", authHeader);
            request.setHeader("Accept", "application/json");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                request.setHeader("BPMCSRFToken", csrfToken);
            }
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (org.apache.hc.core5.http.ParseException e) {
                    throw new IOException("Failed to parse response", e);
                }
                
                if (response.getCode() != 200) {
                    throw new IOException("Failed to get what_used. Status: " +
                                        response.getCode() + ", Response: " + responseBody);
                }
                
                return objectMapper.readValue(responseBody, WhatUsedResponse.class);
            }
        });
    }

    /**
     * Close the HTTP client
     */
    public void close() throws IOException {
        httpClient.close();
    }
}

// Made with Bob
