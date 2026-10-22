package ru.study.s3gallery.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.study.s3gallery.service.BucketDeletionService;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@RestController
@RequestMapping(
        value = "/api/buckets",
        produces = "application/json;charset=UTF-8")
public class BucketController {

    private final S3Client s3Client;
    private final BucketDeletionService bucketDeletionService;

    public BucketController(
            S3Client s3Client,
            BucketDeletionService bucketDeletionService) {
        this.s3Client = s3Client;
        this.bucketDeletionService = bucketDeletionService;
    }

    @GetMapping
    public ResponseEntity<?> listBuckets() {
        try {
            List<String> names = s3Client.listBuckets()
                    .buckets()
                    .stream()
                    .map(Bucket::name)
                    .sorted()
                    .toList();

            return ResponseEntity.ok(names);
        } catch (SdkException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Не удалось получить список бакетов. "
                            + "Проверьте подключение и права доступа к S3."));
        }
    }

    @PostMapping
    public ResponseEntity<?> createBucket(
            @RequestBody CreateBucketBody request) {
        if (request == null || !isValidBucketName(request.name())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Имя бакета не соответствует правилам S3."));
        }

        String name = request.name();

        try {
            s3Client.createBucket(builder -> builder.bucket(name));

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "name", name,
                            "message", "Бакет создан."));
        } catch (BucketAlreadyExistsException
                | BucketAlreadyOwnedByYouException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                            "Бакет с таким именем уже существует."));
        } catch (SdkException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Не удалось создать бакет. "
                            + "Проверьте подключение и права доступа к S3."));
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteBucket(
            @PathVariable("name") String name,
            @RequestParam(name = "confirmed", defaultValue = "false")
            boolean confirmed) {
        if (!confirmed) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Требуется подтверждение удаления бакета "
                            + "и всего его содержимого."));
        }

        if (!isValidBucketName(name)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Имя бакета не соответствует правилам S3."));
        }

        try {
            bucketDeletionService.deleteBucket(name, confirmed);

            return ResponseEntity.ok(Map.of(
                    "name", name,
                    "message", "Бакет и всё его содержимое удалены."));
        } catch (NoSuchBucketException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Бакет не найден."));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                            "Не удалось определить режим версионирования. "
                            + "Удаление остановлено."));
        } catch (SdkException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Не удалось завершить удаление бакета. "
                            + "Часть содержимого могла быть удалена. "
                            + "Проверьте состояние бакета и права доступа к S3."));
        }
    }

    private boolean isValidBucketName(String name) {
        return name != null
                && name.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                && !name.startsWith("xn--")
                && !name.contains("..")
                && !name.contains("-.")
                && !name.contains(".-")
                && !name.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }

    public record CreateBucketBody(String name) {
    }
}