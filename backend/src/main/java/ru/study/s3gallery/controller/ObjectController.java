package ru.study.s3gallery.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@RestController
@RequestMapping(
        value = "/api/buckets/{bucket}/objects",
        produces = "application/json;charset=UTF-8")
public class ObjectController {

    private final S3Client s3Client;

    public ObjectController(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @GetMapping
    public ResponseEntity<?> listObjects(
            @PathVariable("bucket") String bucket) {
        try {
            List<ObjectInfo> objects = s3Client.listObjectsV2Paginator(
                            request -> request.bucket(bucket))
                    .stream()
                    .flatMap(page -> page.contents().stream())
                    .map(object -> new ObjectInfo(
                            object.key(),
                            object.size(),
                            object.lastModified()))
                    .sorted(Comparator.comparing(ObjectInfo::key))
                    .toList();

            return ResponseEntity.ok(objects);
        } catch (NoSuchBucketException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Бакет не найден."));
        } catch (SdkException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Не удалось получить список объектов. "
                            + "Проверьте подключение и права доступа к S3."));
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadObject(
            @PathVariable("bucket") String bucket,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Выберите непустой файл."));
        }

        String originalName = file.getOriginalFilename();
        String fileName = originalName == null
                ? "file"
                : originalName.replace('\\', '/');

        fileName = fileName.substring(fileName.lastIndexOf('/') + 1)
                .replaceAll("\\p{Cntrl}", "_");

        if (fileName.isBlank()) {
            fileName = "file";
        }

        if (fileName.length() > 200) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Имя файла слишком длинное. Сократите его до 200 символов."));
        }

        String key = UUID.randomUUID() + "-" + fileName;

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        var request = software.amazon.awssdk.services.s3.model.PutObjectRequest
                .builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        try (InputStream input = file.getInputStream()) {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(input, file.getSize()));

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "key", key,
                            "message", "Файл загружен."));
        } catch (NoSuchBucketException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Бакет не найден."));
        } catch (SdkException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Не удалось загрузить файл. "
                            + "Проверьте подключение и права доступа к S3."));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error",
                            "Не удалось прочитать загружаемый файл."));
        }
    }

    public record ObjectInfo(
            String key,
            Long size,
            Instant lastModified) {
    }
}