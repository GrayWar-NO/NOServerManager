openssl genrsa -out central.key 2048
openssl req -new -key central.key -out central.csr -config central.cnf

openssl x509 -req \
  -in central.csr \
  -CA ca.crt \
  -CAkey ca.key \
  -CAcreateserial \
  -out central.crt \
  -days 365 \
  -sha256 \
  -extensions v3_req \
  -extfile central.cnf
