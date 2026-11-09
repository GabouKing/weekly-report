package com.example.lambda.adapters;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class S3ReportPublisherTest {
    @Test
    void sendsBinaryContentAndXlsxMetadataUsingSameKeyOnRepeatedPublication() {
        Map<String, byte[]> objects = new HashMap<>();
        AtomicReference<PutObjectRequest> sentRequest = new AtomicReference<>();
        S3Client client = client((request, body) -> {
            sentRequest.set(request);
            try (var input = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), input.readAllBytes());
            }
            return PutObjectResponse.builder().eTag("test-etag").build();
        });
        var publisher = new S3ReportPublisher(client, "weekly-reports-local");
        String fileName = "Relatório - 2026-11-01-2026-11-08.xlsx";

        publisher.publish(new byte[]{80, 75, 0, 1}, fileName);
        byte[] updated = {80, 75, 0, 2};
        publisher.publish(updated, fileName);

        assertEquals("weekly-reports-local", sentRequest.get().bucket());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                sentRequest.get().contentType());
        assertNull(sentRequest.get().aclAsString());
        assertEquals(1, objects.size());
        assertArrayEquals(updated, objects.get("reports/" + fileName));
    }

    @Test
    void propagatesS3FailureToCaller() {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(403);
        builder.message("Access denied");
        var failure = builder.build();
        var publisher = new S3ReportPublisher(client((request, body) -> {
            throw failure;
        }), "weekly-reports-local");

        assertSame(failure, assertThrows(S3Exception.class,
                () -> publisher.publish(new byte[]{1}, "report.xlsx")));
    }

    @Test
    void rejectsInvalidNamesBeforeCallingS3() {
        AtomicInteger calls = new AtomicInteger();
        var publisher = new S3ReportPublisher(client((request, body) -> {
            calls.incrementAndGet();
            return PutObjectResponse.builder().build();
        }), "weekly-reports-local");

        for (String name : new String[]{null, "", " ", "reports/file.xlsx", "reports\\file.xlsx"}) {
            assertThrows(IllegalArgumentException.class, () -> publisher.publish(new byte[]{1}, name));
        }
        assertEquals(0, calls.get());
    }

    private S3Client client(PutOperation operation) {
        return (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("putObject") && arguments[1] instanceof RequestBody body) {
                        return operation.put((PutObjectRequest) arguments[0], body);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private interface PutOperation {
        PutObjectResponse put(PutObjectRequest request, RequestBody body) throws Exception;
    }
}
