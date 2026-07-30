# syntax=docker/dockerfile:1
# auth-service (Go + Fiber)
# 配置通过环境变量注入（见仓库根目录 .env.example）：
#   PORT, DATABASE_URL, REDIS_URL, JWT_SECRET (必填), GOOGLE/GITHUB/WECHAT_*
FROM golang:1.25-alpine AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/server ./cmd/server

FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /app
COPY --from=builder /out/server /app/server
ENV PORT=8001
EXPOSE 8001
ENTRYPOINT ["/app/server"]
