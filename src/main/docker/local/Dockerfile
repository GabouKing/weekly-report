FROM localstack/localstack:2026.3.0

COPY init-resources.sh /etc/localstack/init/ready.d/init-resources.sh
COPY evaluations.json /opt/weekly-reports/evaluations.json
RUN sed -i 's/\r$//' /etc/localstack/init/ready.d/init-resources.sh \
    && chmod +x /etc/localstack/init/ready.d/init-resources.sh
