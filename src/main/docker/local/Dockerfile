FROM localstack/localstack:2026.3.0

COPY init-resources.sh /etc/localstack/init/ready.d/init-resources.sh
COPY evaluations.json /opt/weekly-reports/evaluations.json
COPY lambda-environment.json /opt/weekly-reports/lambda-environment.json
COPY deploy-lambda.sh /opt/weekly-reports/deploy-lambda.sh
RUN sed -i 's/\r$//' /etc/localstack/init/ready.d/init-resources.sh \
    && sed -i 's/\r$//' /opt/weekly-reports/deploy-lambda.sh \
    && chmod +x /etc/localstack/init/ready.d/init-resources.sh /opt/weekly-reports/deploy-lambda.sh
