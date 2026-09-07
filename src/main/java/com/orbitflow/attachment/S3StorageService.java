package com.orbitflow.attachment;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Object storage with graceful degradation:
 * <ul>
 *   <li>Production: MinIO/S3 via pre-signed PUT/GET URLs.</li>
 *   <li>Tests / S3 unavailable: local filesystem under {@code java.io.tmpdir/orbitflow-attachments}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {
    private final S3Client s3;

    @Value("${orbitflow.s3.bucket:orbitflow-attachments}")
    private String bucket = "orbitflow-attachments";

    private final Map<String, byte[]> memoryStore = new ConcurrentHashMap<>();

    private Path localDir() {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "orbitflow-attachments");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    public String storageKey(UUID attachmentId, String fileName) {
        String safe = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "attachments/" + attachmentId + "/" + safe;
    }

    /** Pre-signed PUT URL; falls back to a local upload URL when S3 is unreachable. */
    public String presignedPutUrl(String storageKey, String contentType, Duration ttl) {
        try {
            ensureBucket();
            var req = software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                    .endpointOverride(s3.serviceClientConfiguration().endpointOverride().orElseThrow())
                    .credentialsProvider(s3.serviceClientConfiguration().credentialsProvider())
                    .region(s3.serviceClientConfiguration().region())
                    .build();
            var put = PutObjectRequest.builder().bucket(bucket).key(storageKey).contentType(contentType).build();
            var presigned = req.presignPutObject(b -> b.putObjectRequest(put).signatureDuration(ttl));
            req.close();
            return presigned.url().toString();
        } catch (Exception e) {
            log.debug("S3 presign PUT unavailable, using local fallback: {}", e.toString());
            return "local://" + storageKey;
        }
    }

    public String presignedGetUrl(String storageKey, Duration ttl) {
        try {
            var req = software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                    .endpointOverride(s3.serviceClientConfiguration().endpointOverride().orElseThrow())
                    .credentialsProvider(s3.serviceClientConfiguration().credentialsProvider())
                    .region(s3.serviceClientConfiguration().region())
                    .build();
            var get = GetObjectRequest.builder().bucket(bucket).key(storageKey).build();
            var presigned = req.presignGetObject(b -> b.getObjectRequest(get).signatureDuration(ttl));
            req.close();
            return presigned.url().toString();
        } catch (Exception e) {
            return "local://" + storageKey;
        }
    }

    public void storeLocal(String storageKey, byte[] bytes) {
        memoryStore.put(storageKey, bytes);
        try {
            Path p = localDir().resolve(storageKey.replace("/", "_"));
            Files.write(p, bytes);
        } catch (IOException ignored) {}
    }

    public boolean exists(String storageKey) {
        if (memoryStore.containsKey(storageKey)) return true;
        if (Files.exists(localDir().resolve(storageKey.replace("/", "_")))) return true;
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long size(String storageKey) {
        if (memoryStore.containsKey(storageKey)) return memoryStore.get(storageKey).length;
        try {
            Path p = localDir().resolve(storageKey.replace("/", "_"));
            if (Files.exists(p)) return Files.size(p);
        } catch (IOException ignored) {}
        try {
            return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build()).contentLength();
        } catch (Exception e) {
            return -1;
        }
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
