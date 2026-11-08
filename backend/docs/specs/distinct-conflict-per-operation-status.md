# Erro de conflito distinto para operação PENDING e CONFIRMED

Labels: `ready-for-agent`

## Problem Statement

Quando o cliente tenta criar uma operação de portabilidade com parcelas que já pertencem a outra operação, a API
sempre responde o mesmo `409 INSTALLMENT_IN_OPERATION` com a mensagem genérica "Installments are already in a pending
or confirmed portability operation.". O cliente não consegue saber, pela resposta, se a operação existente ainda está
`PENDING` (aguardando a Central — vale acompanhá-la) ou já está `CONFIRMED` (a portabilidade está concluída — não há
nada a refazer). Uma nova tentativa sobre parcelas de uma operação confirmada deveria dizer explicitamente que ela já
foi confirmada.

## Solution

`POST /api/portability-operations` passa a distinguir os dois cenários com dois `code` e duas mensagens:

- `409 INSTALLMENT_IN_PENDING_OPERATION` — "Installments are already in a pending portability operation."
- `409 INSTALLMENT_IN_CONFIRMED_OPERATION` — "Installments are already in a confirmed portability operation."

Ambos mantêm o formato atual (`ProblemDetail` com `installmentIds` e `operationIds`). O código
`INSTALLMENT_IN_OPERATION` deixa de existir.

## User Stories

1. Como cliente da API, quero receber `INSTALLMENT_IN_CONFIRMED_OPERATION` ao repetir um pedido cuja operação já foi
   confirmada, para mostrar ao usuário que a portabilidade já está concluída.
2. Como cliente da API, quero receber `INSTALLMENT_IN_PENDING_OPERATION` ao repetir um pedido cuja operação ainda está
   pendente, para orientar o usuário a aguardar e acompanhar a operação existente.
3. Como cliente da API, quero que a mensagem (`detail`) de cada erro diga o status da operação existente, para exibir
   um texto correto sem mapear códigos.
4. Como cliente da API, quero continuar recebendo `installmentIds` e `operationIds` nos dois erros, para navegar até a
   operação que já detém as parcelas.
5. Como cliente da API, quero que um clique duplo / retry após resposta perdida (pedido idêntico) receba o erro do
   status atual da operação original, para tratar o retry corretamente.
6. Como cliente da API, quero que o mesmo pedido com outra conta de destino receba o erro do status da operação
   existente, para saber que a troca de conta não é possível.
7. Como cliente da API, quero que uma sobreposição parcial (só algumas parcelas já detidas) receba o erro do status da
   operação que detém essas parcelas, listando só as parcelas detidas.
8. Como cliente da API, quando as parcelas pedidas estão em operações com status diferentes (uma `PENDING`, outra
   `CONFIRMED`), quero receber `INSTALLMENT_IN_CONFIRMED_OPERATION`, porque parte do pedido já é definitivamente
   impossível e isso é o que importa ao usuário.
9. Como cliente da API, no caso misto, quero que `installmentIds` e `operationIds` listem apenas as parcelas e
   operações confirmadas, para que a resposta seja coerente com o `code`.
10. Como cliente da API, quero que duas requisições concorrentes idênticas resultem em um `202` e um
    `INSTALLMENT_IN_PENDING_OPERATION`, para que a corrida tenha o mesmo contrato do caso sequencial.
11. Como cliente da API, quero que parcelas de operações `REJECTED` ou `FAILED` continuem livres para uma nova
    operação, sem nenhum dos dois erros.
12. Como cliente da API, quero que parcelas fora da agenda ou não `OPEN` continuem recebendo `422
    INSTALLMENT_NOT_ELIGIBLE` antes de qualquer `409`, para que a precedência atual não mude.
13. Como integrador, quero que a documentação da API (`API.md`, OpenAPI, guia de testes) descreva os dois novos códigos,
    para implementar o tratamento sem ler o código.

## Implementation Decisions

- **Contrato**: os dois novos `code` substituem `INSTALLMENT_IN_OPERATION` (quebra de contrato aceita; nenhum cliente no
  repositório depende do código antigo). Status HTTP continua `409`. Propriedades extras inalteradas:
  `installmentIds` (na ordem do pedido) e `operationIds` (distintos, na ordem em que aparecem).
- **Status considerado**: o status persistido localmente da operação que detém a parcela. Uma operação confirmada na
  Central mas ainda não atualizada localmente (o status é atualizado pelo job de polling `PortabilityStatusService`) é reportada como
  `PENDING`. Isso é aceitável: o cliente segue a operação pelo `operationIds` e, na próxima consulta, ela vira
  `CONFIRMED`.
- **Precedência no caso misto**: `CONFIRMED` vence `PENDING`; a resposta lista só o subconjunto confirmado.
- **Exceção**: o pacote `exception` não pode acessar nenhuma outra camada (regra do `ArchitectureTest`), então a
  exceção não carrega o enum de status do domínio. A exceção de conflito atual vira base comum (listas
  `installmentIds`/`operationIds`) com duas subclasses, uma para pendente e uma para confirmada, cada uma com sua
  mensagem. O serviço escolhe qual lançar; o handler global mapeia cada uma para seu `code`.
- **Montagem do conflito no serviço**: o ponto único que hoje monta o conflito (usado tanto na checagem prévia contra
  a agenda quanto na violação de unique constraint da corrida) passa a buscar também as operações detentoras pelo
  `PortabilityOperationRepository` (busca por ids) para ler seus status. Por ser ponto único, os dois caminhos ganham o
  comportamento novo sem mudança nos chamadores.
- **Sem mudança de schema**: `InstallmentLock` continua guardando só `installmentId` e `operationId`; o status vem da
  operação. Locks só existem para operações `PENDING`/`CONFIRMED`, então não há outro status possível aqui.
- **Documentação**: atualizar a tabela de erros e a seção "Duplicates" de `API.md`, a anotação `@ApiResponse 409` do
  controller, a descrição de `409` no `OpenApiConfig` e os cenários de `TESTING.md`.

## Testing Decisions

- Um bom teste aqui exercita só o comportamento externo: faz `POST` pela API e verifica status HTTP, `code`,
  `detail`, `installmentIds` e `operationIds`. Nada de verificar chamadas a repositório ou a forma da exceção.
- **Seam único**: o teste de fluxo do controller (MockMvc + stub da Central), já existente. Não é preciso seam novo.
- Prior art: os testes de conflito atuais desse teste de controller (pedido repetido, outra conta de destino, várias
  operações detentoras, operação confirmada, concorrência). Eles passam a esperar o código novo correspondente; para
  levar uma operação a `CONFIRMED` use o mesmo padrão já usado lá (stub da Central respondendo `CONFIRMADA` + rodar o job de status).
- Casos novos: caso misto (`P001` em operação confirmada, `P002` em pendente, pedido com ambas →
  `INSTALLMENT_IN_CONFIRMED_OPERATION` listando só `P001` e a operação confirmada); verificação do `detail` de cada um
  dos dois erros.
- Teste de unidade do handler não é necessário; o teste de fluxo cobre o mapeamento.

## Out of Scope

- Atualizar o status da operação detentora consultando a Central no momento do conflito.
- Retornar o status por operação (ex.: um mapa `operationId → status`) na resposta.
- Mudanças no frontend/prototype ou no simulador.
- Qualquer mudança nos erros `422`, `502`, `503`.

## Further Notes

- Como o status vem do banco local, o cenário "confirmada" só aparece depois que o job de polling observou a confirmação. Se isso se mostrar confuso para usuários, a evolução natural é sincronizar com a Central no
  conflito — fora do escopo desta spec.
