package com.example.lambda.ports;

public interface ReportPublisher {

    void publish(byte[] report, String fileName);
}
