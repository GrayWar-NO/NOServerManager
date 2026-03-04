openssl genrsa -out "$(1).key" 2048

openssl req -new \
  -key "$(1).key" \
  -out "$(1).csr" \
  -subj "/CN=$(1)"

openssl x509 -req \
  -in "$(1).csr" \
  -CA ca.crt \
  -CAkey ca.key \
  -CAcreateserial \
  -out "$(1).crt" \
  -days 365 \
  -sha256

