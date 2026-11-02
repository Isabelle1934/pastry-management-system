# Frontend compilado servido pelo Caddy, que também faz HTTPS automático e repassa /api para o backend.
# Build a partir da raiz do projeto: docker build -f infra/caddy/Dockerfile .
FROM node:24-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM caddy:2.10-alpine
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv
