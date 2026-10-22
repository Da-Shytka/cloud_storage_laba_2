package ru.study.s3gallery.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
public class ObjectDownloadController {

    private static final Set<String> PREVIEW_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/avif",
            "image/bmp");

    private final S3Client s3Client;

    public ObjectDownloadController(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @GetMapping("/api/buckets/{bucket}/objects/content")
    public ResponseEntity<StreamingResponseBody> downloadObject(
            @PathVariable("bucket") String bucket,
            @RequestParam("key") String key,
            @RequestParam(name = "download", defaultValue = "true")
            boolean download) {
        if (key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Не указан ключ объекта.");
        }

        String fileName = key.substring(key.lastIndexOf('/') + 1)
                .replaceAll("\\p{Cntrl}", "_")
                .replace('\\', '_');

        if (fileName.isBlank()) {
            fileName = "file";
        }

        try {
            var object = s3Client.getObject(request -> request
                    .bucket(bucket)
                    .key(key));

            String storedType = object.response().contentType();
            String mediaType = storedType == null
                    ? ""
                    : storedType.split(";", 2)[0]
                            .trim()
                            .toLowerCase(Locale.ROOT);

            boolean preview = !download
                    && PREVIEW_TYPES.contains(mediaType);

            MediaType responseType = preview
                    ? MediaType.parseMediaType(mediaType)
                    : MediaType.APPLICATION_OCTET_STREAM;

            ContentDisposition.Builder dispositionBuilder = preview
                    ? ContentDisposition.inline()
                    : ContentDisposition.attachment();

            String disposition = dispositionBuilder
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString();

            StreamingResponseBody body = output -> {
                try (object) {
                    object.transferTo(output);
                }
            };

            return ResponseEntity.ok()
                    .contentType(responseType)
                    .contentLength(object.response().contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(body);
        } catch (NoSuchKeyException | NoSuchBucketException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Бакет или файл не найден.");
        } catch (SdkException exception) {
            System.err.println(
                    "S3 download error type: "
                    + exception.getClass().getSimpleName());

            if (exception instanceof S3Exception s3Error) {
                System.err.println(
                        "S3 HTTP status: " + s3Error.statusCode());

                if (s3Error.awsErrorDetails() != null) {
                    System.err.println(
                            "S3 error code: "
                            + s3Error.awsErrorDetails().errorCode());
                }
            } else if (exception.getCause() != null) {
                System.err.println(
                        "S3 cause type: "
                        + exception.getCause().getClass().getSimpleName());
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Не удалось получить файл. "
                    + "Проверьте подключение и права доступа к S3.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleDownloadError(
            ResponseStatusException exception) {
        String message = exception.getReason();

        if (message == null) {
            message = "Ошибка получения файла.";
        }

        return ResponseEntity.status(exception.getStatusCode())
                .contentType(MediaType.parseMediaType(
                        "application/json;charset=UTF-8"))
                .body(Map.of("error", message));
    }
}