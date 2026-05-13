package com.erik.messenger.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3Service {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * This method runs automatically when the service starts.
     * It ensures the S3 bucket exists in LocalStack.
     */
    @PostConstruct
    public void init() {
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                System.out.println("S3 Bucket found: " + bucketName);
                return;
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    System.out.println("S3 Bucket not found. Creating bucket: " + bucketName);
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                    return;
                }
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " - LocalStack not ready yet: " + e.getMessage());
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new RuntimeException("Could not connect to S3 / LocalStack after " + maxRetries + " attempts");
    }

    public String uploadImage(MultipartFile file, Long chatId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        String objectKey = "chat_" + chatId + "/" + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        return objectKey;
    }

    public String generatePresignedUrl(String objectKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60))
                .getObjectRequest(b -> b.bucket(bucketName).key(objectKey))
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }
}