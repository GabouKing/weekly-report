# AWS local

Para executar o pacote como Lambda no emulador, siga [LAMBDA.md](LAMBDA.md).

Preencha `LOCALSTACK_AUTH_TOKEN` no arquivo `.env-local` da raiz com um token associado a uma licenca valida. Esse arquivo esta no `.gitignore`. Nao grave o token em arquivos versionados.

Como o nome nao e `.env`, carregue-o explicitamente com `--env-file .env-local`
nos comandos Docker Compose abaixo. Isso nao exporta variaveis para a aplicacao Java.

Execute os comandos abaixo na raiz do repositorio, com Docker Desktop em modo Linux.
Toda a infraestrutura local fica em `src/main/docker/local`.

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --build --wait
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml ps
```

O hook de inicializacao cria os recursos automaticamente e pode ser reexecutado sem
apagar dados existentes. A imagem LocalStack esta fixada em 2026.3.0 para reproducibilidade.
O Dockerfile normaliza quebras de linha e permissoes do script para funcionar no Windows.

| Recurso | Valor local |
| --- | --- |
| Endpoint | http://localhost:4566 |
| Regiao | us-east-1 |
| Access key / secret key ficticias | test / test |
| Tabela DynamoDB | evaluations-local |
| Chave primaria | id (String) |
| Bucket S3 | weekly-reports-local |

Os nomes e a chave sao exclusivos deste ambiente de desenvolvimento. Nao definem o
contrato de infraestrutura de producao. Nao ha GSI: a futura leitura por periodo
precisara considerar o esquema definitivo; um Scan local precisa ser paginado.
O bucket nao recebe ACL nem politica publica e tem bloqueio de acesso publico configurado.

## Aplicacao

Se a porta 4566 estiver ocupada, escolha outra porta no PowerShell antes de subir
o Compose e iniciar a aplicacao na mesma sessao:

```powershell
$env:LOCALSTACK_PORT = "4567"
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml up -d --build --wait
```

O Compose e o perfil Quarkus `dev` usam essa variavel; o endpoint no host passa
a ser `http://localhost:4567`. Em outro terminal, defina a mesma variavel antes
de iniciar a aplicacao. A porta interna do container continua sendo 4566.

```powershell
.\mvnw.cmd quarkus:dev "-Dquarkus.profile=dev"
```

O perfil `dev` configura os SDKs para o emulador. O caso de uso le as avaliacoes,
gera o XLSX e publica em `s3://weekly-reports-local/reports/<nome>.xlsx`.
Os adaptadores leem tabela e bucket por configuracao. O perfil pressupoe a aplicacao no host;
em outro container o endpoint deve ser `http://localstack:4566` na mesma rede Compose.

## Verificar e inserir uma avaliacao

A inicializacao executa um unico `batch-write-item` usando `evaluations.json`.
O arquivo contem 21 avaliacoes com datas fixas de 01/11/2026 a 07/11/2026,
no horario de Sao Paulo (armazenadas em UTC): 3 por dia, notas 4, 6 e 9.
Ajuste as datas no JSON quando precisar testar outro periodo.
Reiniciar regrava os mesmos IDs de exemplo, sem duplicar; itens com outros IDs sao preservados.
O JSON usa a tabela `evaluations-local`; ajuste-o se renomear a tabela.
A regra do relatorio continua de segunda a segunda.
Recrie a imagem com `up -d --build --wait` para aplicar alteracoes nos scripts.

Use `awslocal` dentro do container, sem instalar AWS CLI no host:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal dynamodb list-tables
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 ls
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal dynamodb put-item --table-name evaluations-local --item '{"id":{"S":"8c7619b1-f338-443b-98c6-26c78618cc40"},"descricao":{"S":"Feedback local"},"nota":{"N":"8"},"data":{"S":"2026-11-03T12:00:00.00000Z"}}'
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal dynamodb scan --table-name evaluations-local
```

A data e apenas um exemplo; ajuste-a para o periodo do relatorio que estiver testando.

## Enviar e recuperar um relatorio manualmente

Depois de gerar um arquivo `report.csv` na raiz do projeto:

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml cp ./report.csv localstack:/tmp/report.csv
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 cp /tmp/report.csv s3://weekly-reports-local/reports/2026-11-02/report.csv
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 ls s3://weekly-reports-local/ --recursive
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml exec localstack awslocal s3 cp s3://weekly-reports-local/reports/2026-11-02/report.csv /tmp/downloaded-report.csv
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml cp localstack:/tmp/downloaded-report.csv ./downloaded-report.csv
```

A chave por periodo e ilustrativa. Repetir o envio na mesma chave substitui o objeto.

## Logs e encerramento

```powershell
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml logs localstack
docker compose --env-file .env-local -f src/main/docker/local/compose.yaml down
```

Os dados ficam no volume nomeado. Para apagar todos os dados locais e recomecar,
use `docker compose --env-file .env-local -f src/main/docker/local/compose.yaml down -v`.

Referencias: [hooks LocalStack](https://blog.localstack.cloud/how-to-automatically-load-state-in-localstack/)
e [configuracao S3 Quarkiverse](https://docs.quarkiverse.io/quarkus-amazon-services/dev/amazon-s3.html).
