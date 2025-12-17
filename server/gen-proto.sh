#!/bin/bash
set -e

# Proto roots
PROTO_API_ROOT="../api"

# 💡 Change this to the path you want generated Go files to live in
GEN_OUT="gen/api/$PROTO_API_ROOT"

# Create output dir if needed
mkdir -p "$GEN_OUT"

echo "🔄 Generating Protobuf and gRPC code..."

# === CONFIG ===
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
schema_root="$PROTO_API_ROOT/schema"
proto_files=$(find "$schema_root" -name "*.proto")

if [[ -n "$proto_files" ]]; then
  echo "📦 Generating schema files from $schema_root"
  protoc \
    --proto_path="$PROTO_API_ROOT" \
    --go_out="$GEN_OUT" \
    --go_opt=paths=source_relative \
    --go-grpc_out="$GEN_OUT" \
    --go-grpc_opt=paths=source_relative \
    $proto_files
else
  echo "⚠️  No .proto files found under $schema_root"
fi

echo "✅ Protobuf generation complete!"
