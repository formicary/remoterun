#!/bin/bash -e

RSA_BITS=3072
PFX_PASS=123456

cd `dirname $0`
rm -rf ca-store *.pfx *.pem *.cnf *.jks
mkdir -p ca-store/newcerts
touch ca-store/index.txt
echo 01 > ca-store/serial

# Generate CA certificate
cat > ca-config.cnf << EOF
[ req ]
default_bits   = ${RSA_BITS}
distinguished_name  = req_distinguished_name
prompt = no
x509_extensions = x509_extensions
[ x509_extensions ]
basicConstraints=critical,CA:TRUE,pathlen:0
[ req_distinguished_name ]
C      = GB
ST     = Hertfordshire
L      = Potters Bar
O      = Twock
CN     = Twock CA
[ ca ]
default_ca = ca_default
[ ca_default ]
dir = ./ca-store
database = \$dir/index.txt
new_certs_dir = \$dir/newcerts
serial = \$dir/serial
certificate = ca-cert.pem
private_key = ca-key.pem
default_md = sha1
policy = ca_policy
email_in_dn = no
copy_extensions = copy
x509_extensions = ca_extensions
[ ca_policy ]
C = supplied
ST = supplied
L = supplied
O = supplied
CN = supplied
[ ca_extensions ]
EOF
chmod 600 ca-config.cnf
openssl req -newkey rsa:${RSA_BITS} -keyout ca-key.pem -x509 -out ca-cert.pem -days 3650 -config ca-config.cnf -nodes
keytool -import -file ca-cert.pem -keystore ca-truststore.jks -storepass 123456 -noprompt

# Generate server certificate
cat > server-config.cnf << EOF
[ req ]
default_bits   = ${RSA_BITS}
prompt = no
req_extensions = extensions
distinguished_name = req_distinguished_name
[ extensions ]
keyUsage=digitalSignature, keyEncipherment, keyAgreement
extendedKeyUsage=serverAuth
[ req_distinguished_name ]
C      = GB
ST     = Hertfordshire
L      = Potters Bar
O      = Twock
CN     = server
EOF
chmod 600 server-config.cnf
openssl req -newkey rsa:${RSA_BITS} -keyout server-key.pem -out server-certrequest.pem -days 3650 -config server-config.cnf -nodes
openssl ca -key "${CA_PASS}" -days 365 -in server-certrequest.pem -out server-cert.pem -config ca-config.cnf -batch
openssl pkcs12 -export -out server-pkcs12.pfx -in server-cert.pem -inkey server-key.pem -name server -CAfile ca-cert.pem -chain -passout "pass:${PFX_PASS}"
keytool -importkeystore -srckeystore server-pkcs12.pfx -srcstoretype pkcs12 -srcstorepass 123456 -destkeystore server-keystore.jks -deststoretype jks -deststorepass 123456

# Generate client certificates
NAME=client1
cat > ${NAME}-config.cnf << EOF
[ req ]
default_bits   = ${RSA_BITS}
prompt = no
req_extensions = extensions
distinguished_name = req_distinguished_name
[ extensions ]
keyUsage=digitalSignature, keyAgreement
extendedKeyUsage=clientAuth
[ req_distinguished_name ]
C      = GB
ST     = Hertfordshire
L      = Potters Bar
O      = Twock
CN     = ${NAME}
EOF
chmod 600 ${NAME}-config.cnf
openssl req -newkey rsa:${RSA_BITS} -keyout ${NAME}-key.pem -out ${NAME}-certrequest.pem -days 3650 -config ${NAME}-config.cnf -nodes
openssl ca -key "${CA_PASS}" -days 365 -in ${NAME}-certrequest.pem -out ${NAME}-cert.pem -config ca-config.cnf -batch
openssl pkcs12 -export -out ${NAME}-pkcs12.pfx -in ${NAME}-cert.pem -inkey ${NAME}-key.pem -name ${NAME} -CAfile ca-cert.pem -chain -passout "pass:${PFX_PASS}"
keytool -importkeystore -srckeystore ${NAME}-pkcs12.pfx -srcstoretype pkcs12 -srcstorepass 123456 -destkeystore ${NAME}-keystore.jks -deststoretype jks -deststorepass 123456

# generate self signed client2 cert
#openssl req -x509 -newkey rsa:${RSA_BITS} -keyout client2-key.pem -out client2-cert.pem -days 3650 -config client1-config.cnf -nodes
#openssl pkcs12 -export -out client2-pkcs12.pfx -in client2-cert.pem -inkey client2-key.pem -name client2 -passout "pass:${PFX_PASS}"
#keytool -importkeystore -srckeystore client2-pkcs12.pfx -srcstoretype pkcs12 -srcstorepass 123456 -destkeystore client2-keystore.jks -deststoretype jks -deststorepass 123456
