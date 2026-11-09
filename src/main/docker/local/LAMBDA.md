# Testar a Lambda no LocalStack

Execute na raiz do projeto, no PowerShell. Esta configuracao e exclusiva do emulador;
nao envie `lambda-environment.json` para a AWS real. Docker Desktop precisa estar
em modo Linux e ter acesso a internet para baixar a imagem do runtime Java 21.

## 1. Preparar o LocalStack

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --build --wait
```

O Compose habilita Lambda e logs, monta o socket Docker e conecta os containers
na rede `weekly-reports-local`. A funcao precisa dessa rede para acessar o emulador.

## 2. Empacotar

Pare a execucao de desenvolvimento e gere o ZIP com a extensao Lambda:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Nao use `-Dlocal-http`. Esse argumento exclui a extensao Lambda e nao gera o pacote
esperado. `-DskipTests` serve apenas para este empacotamento: execute os testes
separadamente com `.\mvnw.cmd test`.

O resultado esperado e `target/function.zip`. O bytecode e Java 21 conforme o POM.

## 3. Copiar o pacote e configurar a funcao

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml cp target/function.zip localstack:/tmp/function.zip
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml cp src/main/docker/local/lambda-environment.json localstack:/tmp/lambda-environment.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda create-function --function-name weekly-reports --runtime java21 --handler io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest --role arn:aws:iam::000000000000:role/weekly-reports-local --memory-size 1024 --timeout 120 --zip-file fileb:///tmp/function.zip --environment file:///tmp/lambda-environment.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda wait function-active-v2 --function-name weekly-reports
```

O ARN da role e ficticio, apenas para o teste local; nao configura IAM real.
O handler configurado na API da Lambda e o adaptador do Quarkus. Dentro da aplicacao,
`quarkus.lambda.handler=weekly-report` seleciona seu `WeeklyReportLambdaHandler`.

A funcao executa com o perfil `prod`, como na AWS, mas as variaveis de ambiente
sobrescrevem os endpoints para `http://localstack:4566`. Nao se usa `localhost`
porque a Lambda executa em outro container. A regiao e fornecida pelo runtime,
e as credenciais de execucao sao disponibilizadas pelo LocalStack.

Se a funcao ja existir, atualize em vez de repetir `create-function`:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda update-function-code --function-name weekly-reports --zip-file fileb:///tmp/function.zip
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda wait function-updated-v2 --function-name weekly-reports
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda update-function-configuration --function-name weekly-reports --environment file:///tmp/lambda-environment.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda wait function-updated-v2 --function-name weekly-reports
```

## 4. Invocar e verificar o resultado

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda invoke --function-name weekly-reports --payload '{}' /tmp/lambda-response.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack cat /tmp/lambda-response.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 ls s3://weekly-reports-local/reports/ --recursive
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal logs filter-log-events --log-group-name /aws/lambda/weekly-reports --query "events[].message" --output text
```

Confira a ausencia de `FunctionError`, os logs de inicio e conclusao com `requestId`
e o arquivo atualizado no bucket. `StatusCode: 200` sozinho nao garante sucesso:
uma falha do handler pode retornar 200 junto com `FunctionError`.
O conteudo de `/tmp/lambda-response.json` deve ser `null`, pois o handler retorna `Void`.
Baixe o XLSX pelo dashboard S3 e confira os valores. A carga tem datas fixas;
se estiver fora do periodo calculado, o relatorio pode estar vazio sem indicar falha.

Esta invocacao e manual: nao valida Scheduler, permissoes IAM reais ou retry da AWS.

Referencias: [Lambda no LocalStack](https://docs.localstack.cloud/aws/services/lambda/)
e [empacotamento Quarkus Lambda](https://quarkus.io/guides/aws-lambda/).
