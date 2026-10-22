package ru.study.s3gallery.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Service
public class ObjectDeletionService {

    private final S3Client s3Client;

    public ObjectDeletionService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void deleteObject(
            String bucket,
            String key,
            boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException(
                    "Подтвердите безвозвратное удаление файла и всех его версий.");
        }

        if (bucket == null || bucket.isBlank()
                || key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Не указаны бакет или ключ объекта.");
        }

        BucketVersioningStatus status = s3Client.getBucketVersioning(
                request -> request.bucket(bucket)).status();

        if (status == null) {
            s3Client.deleteObject(request -> request
                    .bucket(bucket)
                    .key(key));
            return;
        }

        if (status != BucketVersioningStatus.ENABLED
                && status != BucketVersioningStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Не удалось определить режим версионирования бакета.");
        }

        List<ObjectIdentifier> versions = new ArrayList<>();

        for (var page : s3Client.listObjectVersionsPaginator(
                request -> request.bucket(bucket).prefix(key))) {

            for (var version : page.versions()) {
                if (key.equals(version.key())) {
                    versions.add(ObjectIdentifier.builder()
                            .key(key)
                            .versionId(version.versionId())
                            .build());
                }
            }

            for (var marker : page.deleteMarkers()) {
                if (key.equals(marker.key())) {
                    versions.add(ObjectIdentifier.builder()
                            .key(key)
                            .versionId(marker.versionId())
                            .build());
                }
            }
        }

        for (ObjectIdentifier version : versions) {
            if (version.versionId() == null
                    || version.versionId().isBlank()) {
                throw new IllegalStateException(
                        "S3 не вернул идентификатор версии. Удаление остановлено.");
            }
        }

        for (ObjectIdentifier version : versions) {
            s3Client.deleteObject(request -> request
                    .bucket(bucket)
                    .key(key)
                    .versionId(version.versionId()));
        }
    }
}