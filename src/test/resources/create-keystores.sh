#!/bin/bash
set -e 
set -x
echo =============================
echo When prompted for a password just enter 'password'
echo =============================
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 13650 -passout pass:password -subj '/C=AU/ST=SomeState/L=Atlantis/CN=testing.com'
openssl pkcs12 -export -out keystore.p12 -inkey key.pem -in cert.pem -passin pass:password -passout pass:password
rm -f trustStore.jks
keytool -import -file cert.pem -alias tls-testing -keystore trustStore.jks -storepass password -noprompt
rm -f keyStore.jks
#keytool -list -keystore keystore.p12 -storetype pkcs12 -storepass password
keytool -importkeystore -srckeystore keystore.p12 -srcstoretype pkcs12 -srcstorepass password -srcalias 1 -destkeystore keyStore.jks -deststoretype jks -deststorepass password -destalias testing 
rm key.pem cert.pem keystore.p12
