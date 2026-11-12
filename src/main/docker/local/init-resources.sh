#!/bin/bash
set -euo pipefail

rm -f /tmp/weekly-reports-ready

if ! awslocal dynamodb describe-table --table-name "$EVALUATIONS_TABLE" >/dev/null 2>&1; then
    awslocal dynamodb create-table \
        --table-name "$EVALUATIONS_TABLE" \
        --attribute-definitions AttributeName=id,AttributeType=S \
        --key-schema AttributeName=id,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST
fi
awslocal dynamodb wait table-exists --table-name "$EVALUATIONS_TABLE"

if ! awslocal s3api head-bucket --bucket "$REPORTS_BUCKET" >/dev/null 2>&1; then
    awslocal s3api create-bucket --bucket "$REPORTS_BUCKET" --region us-east-1
fi
awslocal s3api put-public-access-block \
    --bucket "$REPORTS_BUCKET" \
    --public-access-block-configuration \
    'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'

awslocal dynamodb batch-write-item \
    --request-items file:///opt/weekly-reports/evaluations.json

bash /opt/weekly-reports/deploy-lambda.sh

touch /tmp/weekly-reports-ready
echo "Local resources ready: $EVALUATIONS_TABLE and $REPORTS_BUCKET"
