#!/bin/sh

mvn clean compile package
# Copy latest plugin in
cp target/Shop-1.8.5.0.jar ../paper-test-1.21/plugins
# cp target/Shop-1.8.4.7.jar ../paper-test-1.20.4/plugins

# Startup latest minecraft server version to test
cd ../paper-test-1.21/
rm -r plugins/.paper-remapped
java -jar paper-1.21-44.jar --nogui