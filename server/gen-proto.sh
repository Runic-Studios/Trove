#!/bin/bash
set -e

# Proto roots
PROTO_API_ROOT="../api"

# 💡 Change this to the path you want generated Go files to live in
GEN_OUT="gen/$PROTO_API_ROOT"

# Create output dir if needed
mkdir -p "$GEN_OUT"

echo "🔄 Generating Protobuf and gRPC code..."

# === CONFIG ===
# Module path must match your go.mod
GO_MODULE="github.com/Runic-Studios/Trove/server"

# === TROVE SERVICE ===
protoc \
  --proto_path="$PROTO_API_ROOT" \
  --go_out="$GEN_OUT" \
  --go_opt=paths=source_relative \
  --go-grpc_out="$GEN_OUT" \
  --go-grpc_opt=paths=source_relative \
  "$PROTO_API_ROOT/trove/service.proto"

# === PLAYER SCHEMAS ===
for version in v1 v2; do
  PROTO_PATH="$PROTO_API_ROOT/db_schema/players/$version/player.proto"
  echo "📦 Generating players schema: $version"
  protoc \
    --proto_path="$PROTO_API_ROOT" \
    --go_out="$GEN_OUT" \
    --go_opt=paths=source_relative \
    "$PROTO_PATH"
done

echo "✅ Protobuf generation complete!"
