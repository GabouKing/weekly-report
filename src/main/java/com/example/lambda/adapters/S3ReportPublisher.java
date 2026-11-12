package com.example.lambda.adapters;

import com.example.lambda.ports.ReportPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class S3ReportPublisher implements ReportPublisher {
    private static final Logger LOG = Logger.getLogger(S3ReportPublisher.class);
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final S3Client s3;
    private final String bucket;

    public S3ReportPublisher(S3Client s3, @ConfigProperty(name = "reports.bucket") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void publish(byte[] report, String fileName) {
        Objects.requireNonNull(report, "Report must not be null");
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("File name must not be blank or contain path separators");
        }

        // S3 usa uma chave de objeto, nao uma pasta real. O mesmo nome gera a
        // mesma chave: um novo envio substitui o objeto (ou cria uma versao, se habilitada).
        String key = "reports/" + fileName;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(XLSX_CONTENT_TYPE)
                .build();

        // O XLSX ja esta em memoria: nao precisamos abrir o arquivo local.
        // Sem ACL publica; as permissoes continuam definidas pelo bucket/IAM.
        // Falhas do SDK propagam para o caso de uso, sem registrar sucesso falso.
        LOG.infof("Iniciando publicacao no S3. bucket=%s chave=%s tamanhoBytes=%d", bucket, key, report.length);
        long startedAt = System.nanoTime();
        s3.putObject(request, RequestBody.fromBytes(report));
        LOG.infof("Relatorio publicado em s3://%s/%s tamanhoBytes=%d duracaoMs=%d",
                bucket, key, report.length,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}
