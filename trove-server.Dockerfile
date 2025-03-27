# --- Build stage ---
FROM golang:1.24.1 AS builder

RUN apt-get update && apt-get install -y unzip curl git && \
    curl -LO https://github.com/protocolbuffers/protobuf/releases/download/v29.4/protoc-29.4-linux-x86_64.zip && \
    unzip protoc-29.4-linux-x86_64.zip -d /usr/local && \
    rm protoc-29.4-linux-x86_64.zip

RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

ENV PATH="/go/bin:$PATH"

WORKDIR /app

COPY . .

WORKDIR /app
RUN cd server && chmod +x ./gen-proto.sh && ./gen-proto.sh && cd ..

WORKDIR /app/server
RUN go mod download

WORKDIR /app/server
RUN go build -o trove-server ./cmd

# --- Runtime stage ---
FROM debian:bookworm-slim

WORKDIR /opt/trove

COPY --from=builder /app/server/trove-server .

CMD ["./trove-server"]
