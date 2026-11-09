package com.example.lambda.adapters;

import com.amazonaws.services.lambda.runtime.Context;
import com.example.lambda.application.GenerateWeeklyReportUseCase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyReportLambdaHandlerTest {

    @Test
    void invokesUseCaseOnceAndReturnsNoPayload() {
        var useCase = new RecordingUseCase(null);
        var handler = new WeeklyReportLambdaHandler(useCase);

        Void result = handler.handleRequest(Map.of("source", "aws.scheduler"), context());

        assertNull(result);
        assertEquals(1, useCase.executions);
    }

    @Test
    void acceptsAnEmptyEventBecauseTheUseCaseDoesNotDependOnPayload() {
        var useCase = new RecordingUseCase(null);
        var handler = new WeeklyReportLambdaHandler(useCase);

        assertNull(handler.handleRequest(null, context()));
        assertEquals(1, useCase.executions);
    }

    @Test
    void propagatesTheOriginalFailureWithoutRetryingOrReturningSuccess() {
        var failure = new IllegalStateException("Publication failed");
        var useCase = new RecordingUseCase(failure);
        var handler = new WeeklyReportLambdaHandler(useCase);

        var thrown = assertThrows(IllegalStateException.class,
                () -> handler.handleRequest(Map.of(), context()));

        assertSame(failure, thrown);
        assertEquals(1, useCase.executions);
    }

    private Context context() {
        return (Context) Proxy.newProxyInstance(Context.class.getClassLoader(),
                new Class<?>[]{Context.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getAwsRequestId")) {
                        return "test-request-id";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    // O objetivo e testar o contrato do handler. O execute e substituido para
    // nao consultar DynamoDB, gerar arquivos ou publicar no S3 neste teste unitario.
    private static class RecordingUseCase extends GenerateWeeklyReportUseCase {
        private final RuntimeException failure;
        private int executions;

        private RecordingUseCase(RuntimeException failure) {
            super(null, null, null, null);
            this.failure = failure;
        }

        @Override
        public void execute() {
            executions++;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
