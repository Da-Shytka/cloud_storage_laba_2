package ru.study.s3gallery.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.MultipartUpload;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Service
public class BucketDeletionService {

    private final S3Client s3Client;

    public BucketDeletionService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void deleteBucket(String bucketName, boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException(
                    "Подтвердите удаление бакета и всего его содержимого.");
        }

        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("Не указано имя бакета.");
        }

        List<MultipartUpload> uploads =
                s3Client.listMultipartUploadsPaginator(
                                request -> request.bucket(bucketName))
                        .stream()
                        .flatMap(page -> page.uploads().stream())
                        .toList();

        List<ObjectIdentifier> objects = collectObjects(bucketName);

        for (MultipartUpload upload : uploads) {
            s3Client.abortMultipartUpload(request -> request
                    .bucket(bucketName)
                    .key(upload.key())
                    .uploadId(upload.uploadId()));
        }

        for (ObjectIdentifier object : objects) {
            s3Client.deleteObject(request -> request
                    .bucket(bucketName)
                    .key(object.key())
                    .versionId(object.versionId()));
        }

        s3Client.deleteBucket(request -> request.bucket(bucketName));
    }

    private List<ObjectIdentifier> collectObjects(String bucketName) {
        List<ObjectIdentifier> objects = new ArrayList<>();

        BucketVersioningStatus status = s3Client.getBucketVersioning(
                request -> request.bucket(bucketName)).status();

        if (status == BucketVersioningStatus.ENABLED
                || status == BucketVersioningStatus.SUSPENDED) {

            for (var page : s3Client.listObjectVersionsPaginator(
                    request -> request.bucket(bucketName))) {

                for (var version : page.versions()) {
                    objects.add(ObjectIdentifier.builder()
                            .key(version.key())
                            .versionId(version.versionId())
                            .build());
                }

                for (var marker : page.deleteMarkers()) {
                    objects.add(ObjectIdentifier.builder()
                            .key(marker.key())
                            .versionId(marker.versionId())
                            .build());
                }
            }
        } else if (status == null) {
            for (var page : s3Client.listObjectsV2Paginator(
                    request -> request.bucket(bucketName))) {

                for (var object : page.contents()) {
                    objects.add(ObjectIdentifier.builder()
                            .key(object.key())
                            .build());
                }
            }
        } else {
            throw new IllegalStateException(
                    "Не удалось определить режим версионирования бакета.");
        }

        return objects;
    }
}