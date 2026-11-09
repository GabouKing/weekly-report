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

1. Inicie o LocalStack com
   `docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --build --wait`.
2. Execute `.\mvnw.cmd quarkus:dev -Dlocal-http "-Dquarkus.http.port=8082"`.
3. Em outro PowerShell, chame
   `Invoke-RestMethod -Method Post http://localhost:8082/dev/weekly-reports`.
4. No dashboard, abra S3, regiao `us-east-1`, bucket `weekly-reports-local`, prefixo `reports/`.
   Baixe o XLSX e confira as abas, os totais e os graficos.

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

Este roteiro executa o pacote real da Lambda dentro do LocalStack, sem o controller
HTTP temporario. Todos os comandos abaixo devem ser executados no PowerShell, na
raiz do projeto. Use Docker Desktop com containers Linux e preencha seu token em
`.env-local` (arquivo ignorado pelo Git). O emulador precisa de acesso a internet
para baixar a imagem do runtime Java 21.

### 1. Iniciar os recursos locais

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --build --wait
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml ps
```

O servico deve aparecer como `healthy`. A inicializacao cria a tabela
`evaluations-local`, o bucket `weekly-reports-local` e carrega as avaliacoes de
exemplo. A funcao Lambda sera criada no passo 3; subir o Compose nao a cadastra.
O socket Docker e a rede `weekly-reports-local` permitem executar o runtime em
outro container e acessar o emulador.

### 2. Executar os testes e gerar o pacote

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
Test-Path .\target\function.zip
```

Continue apenas se o build terminar com `BUILD SUCCESS` e `Test-Path` retornar
`True`. O ZIP contem o codigo compilado, dependencias e o template XLSX.
Nao use `-Dlocal-http`: essa opcao retira a extensao Lambda do empacotamento.
`-DskipTests` evita repetir os testes ja executados; nao substitui sua validacao.

### 3. Copiar os arquivos e criar a funcao (primeira vez)

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml cp target/function.zip localstack:/tmp/function.zip
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml cp src/main/docker/local/lambda-environment.json localstack:/tmp/lambda-environment.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda create-function --function-name weekly-reports --runtime java21 --handler io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest --role arn:aws:iam::000000000000:role/weekly-reports-local --memory-size 1024 --timeout 120 --zip-file fileb:///tmp/function.zip --environment file:///tmp/lambda-environment.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda wait function-active-v2 --function-name weekly-reports
```

`Pending` imediatamente apos a criacao e esperado. O comando `wait` aguarda a
funcao ficar ativa. A role informada e ficticia, exclusiva do LocalStack.
A funcao usa o perfil `prod`, mas `lambda-environment.json` sobrescreve os endpoints
para `http://localstack:4566`, acessivel dentro da rede Docker. Esse JSON nao deve
ser usado na AWS real. O handler AWS e o adaptador do Quarkus; a propriedade
`quarkus.lambda.handler=weekly-report` seleciona nosso handler dentro da aplicacao.

Para conferir o cadastro:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda list-functions
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda get-function-configuration --function-name weekly-reports --query "{Nome:FunctionName,Estado:State,Atualizacao:LastUpdateStatus,Erro:StateReason}"
```

O esperado e `State: Active` e `LastUpdateStatus: Successful`.

### 4. Invocar a Lambda

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda invoke --function-name weekly-reports --payload '{}' /tmp/lambda-response.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack cat /tmp/lambda-response.json
```

A invocacao deve retornar sem `FunctionError`. O arquivo de resposta deve conter
`null`, pois o handler retorna `Void`. `StatusCode: 200` sozinho nao comprova
sucesso: verifique tambem a ausencia de `FunctionError`, os logs e o objeto no S3.

### 5. Conferir logs e relatorio

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal logs filter-log-events --log-group-name /aws/lambda/weekly-reports --query "events[].message" --output text
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 ls s3://weekly-reports-local/reports/ --recursive
```

Use `filter-log-events`: a versao do `awslocal` neste container nao oferece `logs tail`.
Procure inicio e conclusao com o mesmo `requestId`, alem da mensagem de publicacao:

```text
Iniciando geracao do relatorio semanal. requestId=...
Relatorio publicado em s3://weekly-reports-local/reports/<nome>.xlsx
Relatorio semanal gerado e publicado. requestId=...
```

No dashboard LocalStack, selecione `us-east-1`, abra S3, bucket
`weekly-reports-local`, prefixo `reports/`, e baixe o XLSX. Confira datas, total,
media com duas casas, agrupamentos e graficos com Baixa, Media e Alta.
O horario de atualizacao deve corresponder a invocacao mais recente.

O periodo atual vai da meia-noite de sete dias atras ate o instante da execucao,
no fuso `America/Sao_Paulo` (inicio inclusivo e fim exclusivo). A carga de exemplo
possui datas fixas de 01/11/2026 a 07/11/2026: ajuste `evaluations.json` para testar
outros periodos. Uma consulta sem dados gera um relatorio vazio valido.

### 6. Atualizar apos alterar o codigo

Gere novamente o ZIP e copie os dois arquivos como nos passos 2 e 3. Nao repita
`create-function` se a funcao ja existir; use:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda update-function-code --function-name weekly-reports --zip-file fileb:///tmp/function.zip
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda wait function-updated-v2 --function-name weekly-reports
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda update-function-configuration --function-name weekly-reports --environment file:///tmp/lambda-environment.json
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal lambda wait function-updated-v2 --function-name weekly-reports
```

Depois, repita a invocacao e a verificacao dos logs e do S3.

### Problemas comuns e alcance da validacao

- `Functions: []`: confirme a regiao `us-east-1` e execute `create-function`.
- `target/function.zip` ausente: confira o build e remova `-Dlocal-http` do comando.
- Arquivo `/tmp/lambda-environment.json` ou `/tmp/function.zip` ausente: copie-o
  novamente. Recriar o container remove os arquivos temporarios.
- Porta 4566 ocupada: pare a outra instancia ou ajuste `LOCALSTACK_PORT` em
  `.env-local`. Entre containers, a porta interna continua sendo 4566.
- `Log4j API could not find a logging provider`: foi observado no teste local,
  sem impedir a publicacao. A integracao do logging da biblioteca ainda e pendente.

Na execucao manual compartilhada nesta sessao, a Lambda iniciou com perfil `prod`,
registrou inicio e conclusao com o mesmo requestId e publicou o XLSX no S3 local.
Isso valida o fluxo no emulador, sem comprovar agendamento, permissoes IAM reais,
retry da AWS ou dimensionamento de memoria. Nao use o consumo reportado pelo
LocalStack para dimensionar a Lambda real.

Guia complementar: [Lambda local](src/main/docker/local/LAMBDA.md).

## Configuracao dos ambientes

Para testar pelo controller temporario, execute `./mvnw quarkus:dev -Dlocal-http`
(Windows: `.\mvnw.cmd quarkus:dev -Dlocal-http`). Esse modo habilita o REST e
exclui a extensao Lambda para evitar conflito entre os servidores locais.
Chame `POST http://localhost:8082/dev/weekly-reports`; a localizacao do arquivo aparece no console.
Sem `-Dlocal-http`, o projeto usa a extensao Lambda normalmente.

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
