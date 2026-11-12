# Teste da Lambda no LocalStack

Execute na raiz do projeto, no PowerShell, com Docker Desktop em modo Linux e
LOCALSTACK_AUTH_TOKEN preenchido em `.env-local`.

### Primeira execucao ou alteracao no codigo Java

```powershell
.\mvnw.cmd clean package -DskipTests
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --build --wait --wait-timeout 600
```

Confirme `BUILD SUCCESS` e a existencia de
`target/function.zip`. Execute os testes separadamente com `.\mvnw.cmd test`.

O diretorio `target` fica montado em modo somente leitura no LocalStack. O hook
inicializa tabela, bucket e dados de exemplo; em seguida, `deploy-lambda.sh` cria
`weekly-reports` ou atualiza seu codigo e configuracao caso ja exista.
O estado `healthy` so e atingido depois dessa preparacao. A primeira execucao pode
demorar enquanto o runtime Java 21 e baixado.

### Proximas vezes, sem alteracao de codigo

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --wait --wait-timeout 600
```

Ao iniciar um container parado, o hook roda novamente. Se o container ja estiver
rodando, `up` nao executa o hook outra vez. Para publicar um ZIP recompilado sem
reiniciar o container:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack bash /opt/weekly-reports/deploy-lambda.sh
```

Esse comando dispensa `docker cp`, `create-function` e as atualizacoes manuais.
Se alterar o Dockerfile, JSON de ambiente ou scripts de inicializacao, execute
novamente `up -d --build --wait --wait-timeout 600` para incorporar essas mudancas.
Nao use `down -v`, a menos que queira apagar os dados locais.

### Invocar e validar

A preparacao nao invoca a Lambda automaticamente. Dispare quando desejar gerar o XLSX:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda invoke --function-name weekly-reports --payload '{}' /tmp/lambda-response.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack cat /tmp/lambda-response.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal logs filter-log-events --log-group-name /aws/lambda/weekly-reports --query "events[].message" --output text
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 ls s3://weekly-reports-local/reports/ --recursive
```

Espere resposta `null`, ausencia de `FunctionError`, logs de inicio e conclusao com
mesmo requestId e o XLSX atualizado no S3. `StatusCode: 200` sozinho nao comprova
sucesso. Baixe a planilha pelo dashboard: regiao `us-east-1`, bucket
`weekly-reports-local`, prefixo `reports/`. Use `filter-log-events`: o `awslocal`
desse container nao suporta `logs tail`.

### Configuracao e diagnostico

- Pacote: `target/function.zip`, incluindo codigo, dependencias e template XLSX.
- Runtime: Java 21; memoria 1024 MB e timeout 120 s para o teste local.
- Handler AWS: `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.
- Handler da aplicacao: `quarkus.lambda.handler=weekly-report`.
- `lambda-environment.json` usa perfil prod com endpoints `http://localstack:4566`.
  Isso e exclusivo do emulador; nao envie esse arquivo para a AWS real.
- Socket Docker e rede `weekly-reports-local` permitem executar os containers Lambda.
- A role de teste e ficticia; nao cria IAM real.
- Se o pacote estiver ausente, a inicializacao falha e registra uma mensagem clara.
- Para investigar inicializacao, use:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml logs localstack
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda get-function-configuration --function-name weekly-reports --query "{Nome:FunctionName,Estado:State,Atualizacao:LastUpdateStatus,Erro:StateReason}"
```

O periodo vai da meia-noite de sete dias atras ate o instante da execucao, no fuso
America/Sao_Paulo (inicio inclusivo e fim exclusivo). A carga tem datas fixas de
01/11 a 07/11/2026; ajuste `evaluations.json` para testar outros periodos.
Relatorio vazio pode ser esperado quando a carga estiver fora do intervalo.
Reiniciar preserva outros registros, mas reaplica os mesmos IDs do JSON de exemplo.

A execucao manual anterior foi validada pelo usuario: a Lambda retornou null e os
logs confirmaram a publicacao no S3 e a conclusao com o mesmo requestId.
A nova automatizacao de cadastro precisa ser validada na proxima inicializacao.
O teste local nao comprova Scheduler, IAM real, retry AWS nem dimensionamento.
O aviso de provider Log4j foi observado sem impedir a publicacao; sua correcao segue pendente.

Referencias: [LocalStack Lambda](https://docs.localstack.cloud/aws/services/lambda/)
e [Quarkus Lambda](https://quarkus.io/guides/aws-lambda/).
