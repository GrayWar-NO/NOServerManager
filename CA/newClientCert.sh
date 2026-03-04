#!/bin/bash
# generate-client-cert.sh
# Usage: ./generate-client-cert.sh agent-01

# Exit on error
set -e

# Check argument
if [ -z "$1" ]; then
    echo "Usage: $0 <client-name>"
    exit 1
fi

CLIENT_NAME="$1"

# Generate private key
openssl genrsa -out "${CLIENT_NAME}.key" 2048

# Generate CSR with CN
openssl req -new \
  -key "${CLIENT_NAME}.key" \
  -out "${CLIENT_NAME}.csr" \
  -subj "/CN=${CLIENT_NAME}"

# Sign CSR with CA
openssl x509 -req \
  -in "${CLIENT_NAME}.csr" \
  -CA ca.crt \
  -CAkey ca.key \
  -CAcreateserial \
  -out "${CLIENT_NAME}.crt" \
  -days 365 \
  -sha256

echo "Client certificate created: ${CLIENT_NAME}.crt / ${CLIENT_NAME}.key"

