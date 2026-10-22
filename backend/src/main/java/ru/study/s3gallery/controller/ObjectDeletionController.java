package ru.study.s3gallery.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.study.s3gallery.service.ObjectDeletionService;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@RestController
@RequestMapping(
        value = "/api/buckets/{bucket}/objects",
        produces = "application/json;charset=UTF-8")
public class ObjectDeletionController {

    private final ObjectDeletionService objectDeletionService;

    public ObjectDeletionController(
            ObjectDeletionService objectDeletionService) {
        this.objectDeletionService = objectDeletionService;
    }

    @DeleteMapping
    public ResponseEntity<?> deleteObject(
            @PathVariable("bucket") String bucket,
            @RequestParam("key") String key,
            @RequestParam(name = "confirmed", defaultValue = "false")
            boolean confirmed) {
        if (!confirmed) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Требуется подтверждение безвозвратного удаления "
                            + "файла и всех его версий."));
        }

        if (key.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Не указан ключ объекта."));
        }

        try {
            objectDeletionService.deleteObject(bucket, key, confirmed);

            return ResponseEntity.ok(Map.of(
                    "key", key,
                    "message", "Файл и все его версии удалены."));
        } catch (NoSuchBucketException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Бакет не найден."));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                            "Не удалось безопасно определить версии файла. "
                            + "Удаление остановлено."));
        } catch (SdkException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Не удалось завершить удаление файла. "
                            + "Часть версий могла быть удалена. "
                            + "Проверьте состояние объекта и права доступа к S3."));
        }
    }
}