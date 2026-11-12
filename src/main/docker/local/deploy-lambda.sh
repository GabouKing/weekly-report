#!/bin/bash
set -euo pipefail

function_name=weekly-reports
package=/opt/weekly-reports/build/function.zip
configuration=/opt/weekly-reports/lambda-environment.json

if [ ! -s "$package" ]; then
    echo "Pacote function.zip ausente. Execute .\mvnw.cmd clean package -DskipTests na raiz e reinicie o LocalStack." >&2
    exit 1
fi

# A listagem falha explicitamente se o servico nao estiver acessivel; nao
# confundimos um erro de conexao com a ausencia da funcao.
existing=$(awslocal lambda list-functions \
    --query "Functions[?FunctionName=='$function_name'].FunctionName" --output text)

if [ -z "$existing" ]; then
    awslocal lambda create-function \
        --function-name "$function_name" \
        --runtime java21 \
        --handler io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest \
        --role arn:aws:iam::000000000000:role/weekly-reports-local \
        --memory-size 1024 \
        --timeout 120 \
        --zip-file "fileb://$package" \
        --environment "file://$configuration" >/dev/null
    awslocal lambda wait function-active-v2 --function-name "$function_name"
    echo "Lambda local criada: $function_name"
else
    # Espera uma eventual operacao anterior antes de atualizar codigo/configuracao.
    awslocal lambda wait function-active-v2 --function-name "$function_name"
    awslocal lambda wait function-updated-v2 --function-name "$function_name"
    awslocal lambda update-function-code \
        --function-name "$function_name" --zip-file "fileb://$package" >/dev/null
    awslocal lambda wait function-updated-v2 --function-name "$function_name"
    awslocal lambda update-function-configuration \
        --function-name "$function_name" \
        --runtime java21 \
        --handler io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest \
        --memory-size 1024 --timeout 120 \
        --environment "file://$configuration" >/dev/null
    awslocal lambda wait function-updated-v2 --function-name "$function_name"
    echo "Lambda local atualizada: $function_name"
fi

# Apenas prepara a funcao. Invocacoes continuam sendo feitas explicitamente.
