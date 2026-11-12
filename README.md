# weekly-reports

## Relatorio XLSX

O caso de uso gera a planilha com `XlsxReportGenerator` e publica no S3.
O nome segue `descricao - yyyy-MM-dd-yyyy-MM-dd.xlsx`, com datas no fuso de Sao Paulo.
O modelo fica em `src/main/resources/templates/weekly-report.xlsx`, copiado da referencia
fornecida: resumo formatado, agrupamento por dia e agrupamento por urgencia.
Os graficos no resumo usam os dados atuais, com rotulos Baixa, Media e Alta.
A media e numerica com duas casas decimais de exibicao, sem alterar o valor calculado.
Os detalhes individuais das avaliacoes continuam pendentes; esta versao usa os indicadores
existentes na `WeeklyReport`.

Teste o gerador com `.\mvnw.cmd "-Dtest=XlsxReportGeneratorTest" test`.

## Publicacao no S3 local

Execute o roteiro de teste da Lambda no LocalStack abaixo para gerar e publicar o XLSX. Depois, abra S3 no dashboard, selecione a regiao us-east-1, o bucket weekly-reports-local e o prefixo reports/. Baixe a planilha para conferir as abas, os totais e os graficos.

Para listar pelo terminal:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 ls s3://weekly-reports-local/reports/ --recursive
```

`ReportPublisher.publish(bytes, fileName)` define o contrato. `S3ReportPublisher`
recebe `S3Client` via CDI, le `reports.bucket`, monta o `PutObjectRequest` com a chave
`reports/<fileName>` e o Content-Type XLSX, e envia os bytes com `RequestBody.fromBytes`.
Referencia: [upload com o SDK Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices-s3-uploads.html).
O bucket precisa existir. Falhas do SDK propagam para o chamador, sem retornar sucesso.
Nenhuma ACL publica e enviada. Em AWS, a role precisa de `s3:PutObject` no prefixo autorizado.

Repetir o mesmo nome sobrescreve o objeto (ou cria uma nova versao se o bucket tiver
versionamento). Como o nome usa apenas datas, execucoes no mesmo dia atualizam o mesmo
arquivo, mesmo que o instante final avance. Isso nao implementa um snapshot imutavel.
Os testes `S3ReportPublisherTest` usam um duble do SDK; nao comprovam conectividade
HTTP nem permissoes reais. Valide o fluxo completo pelos passos acima.

## Teste da Lambda no LocalStack

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

## Configuracao dos ambientes

O projeto executa o caso de uso pelo handler Lambda. Para testar localmente, use o pacote function.zip no LocalStack conforme o roteiro acima.

- LocalStack: iniciar o Compose com `--env-file .env-local` e executar a aplicacao
  com `-Dquarkus.profile=dev`. `.env-local` e usado pelo Compose; ele nao e
  carregado automaticamente pela aplicacao Java.
- AWS: `.env.example` documenta `AWS_REGION`, `EVALUATIONS_TABLE` e `REPORTS_BUCKET`.
  Os nomes reais da tabela e do bucket ainda precisam ser confirmados com a equipe.
  Na Lambda, configure tabela e bucket nas variaveis de ambiente da funcao;
  `AWS_REGION` e fornecida pelo runtime. Use o perfil `prod` (padrao do aplicativo
  empacotado). As credenciais devem vir da IAM Role da Lambda, sem chaves no arquivo.
- Para executar fora da Lambda, copie `.env.example` para `.env` (ignorado pelo Git),
  preencha os valores e disponibilize credenciais pelo perfil AWS local ou pela cadeia
  padrao do SDK. O Quarkus le `.env` no diretorio de trabalho.

Somente `dev` possui endpoints LocalStack e credenciais ficticias. O perfil
`prod` usa os endpoints AWS e a cadeia padrao de credenciais.
Os adaptadores usam `reports.evaluations-table` e `reports.bucket`.
Nenhum recurso AWS e criado ou deploy executado por esta configuracao.

Referencia: [configuracao dos clientes AWS](https://quarkus.io/guides/amazon-dynamodb/).

## Referencias

- [Quarkus Lambda](https://quarkus.io/guides/aws-lambda/)
- [LocalStack Lambda](https://docs.localstack.cloud/aws/services/lambda/)
