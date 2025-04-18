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
for dir in "$PROTO_API_ROOT/db_schema/players/"*/; do
    version=$(basename "$dir")
    PROTO_PATH="$dir/player.proto"
    if [[ -f "$PROTO_PATH" ]]; then
      echo "📦 Generating players schema: $version"
      protoc \
        --proto_path="$PROTO_API_ROOT" \
        --go_out="$GEN_OUT" \
        --go_opt=paths=source_relative \
        "$PROTO_PATH"
    else
      echo "⚠️  Skipping $version (no player.proto found)"
    fi
done

echo "✅ Protobuf generation complete!"
