# Portabilidade de consignado

| Pasta        | O quê                                         |
|--------------|-----------------------------------------------|
| `backend/`   | API Spring Boot ([docs/API.md](backend/docs/API.md)) |
| `frontend/`  | React + Vite                                  |
| `simulator/` | Central de Convênios simulada                 |

## Configuração (`.env`)

Cada app tem um `.env.example`; copie para `.env` e ajuste se precisar (o `.env` não é versionado):

```sh
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

| Arquivo         | Variáveis                                                                 |
|-----------------|---------------------------------------------------------------------------|
| `backend/.env`  | `SERVER_PORT`, `CENTRAL_CONVENIOS_BASE_URL`, timeouts da Central (todas opcionais) |
| `frontend/.env` | `VITE_API_URL` (obrigatória, validada em `src/config/env.ts`), `API_PROXY_TARGET` |

## Rodar com Docker

Requer apenas Docker.

```sh
docker compose up --build
```

O Compose lê os `.env` (se existirem) e sobrescreve só os endereços internos da rede Docker. Portas já ocupadas?
`BACKEND_PORT=18080 FRONTEND_PORT=15173 docker compose up --build`.

| Serviço     | URL                                     |
|-------------|-----------------------------------------|
| Frontend    | http://localhost:5173                   |
| Backend     | http://localhost:8080/api               |
| Swagger UI  | http://localhost:8080/swagger-ui.html   |

Beneficiários do simulador: `BEN001` (confirma), `BEN002` (rejeita), `BEN_ERRO` (Central indisponível), `BEN003` (possui agenda, mas a portabilidade falha com erro 500 da Central).
O banco é H2 em memória: os dados somem ao reiniciar o backend.

## Rodar sem Docker

Requer Java 21, Node 22 e Yarn 1 (versões do frontend fixadas com [Volta](https://volta.sh) no `package.json`), e o `frontend/.env` criado acima. Um terminal para cada:

```sh
node simulator/mock-central.js
cd backend && ./mvnw spring-boot:run
cd frontend && yarn install && yarn dev
```

## Testes

```sh
cd backend && ./mvnw test
cd frontend && yarn test
```
