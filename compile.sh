#!/bin/sh

mvn clean compile package -o
# Copy latest plugin in
cp target/Shop-1.8.5.0.jar ../paper-test-1.20.6/plugins
cp target/Shop-1.8.5.0.jar ../paper-test-1.21/plugins
cp target/Shop-1.8.5.0.jar ../spigot-test-1.21/plugins
# cp target/Shop-1.8.4.7.jar ../paper-test-1.20.4/plugins

# Startup latest minecraft server version to test
# cd ../paper-test-1.20.6/
# rm -r plugins/.paper-remapped
# java -jar paper-1.20.6-148.jar --nogui

cd ../paper-test-1.21/
rm -r plugins/.paper-remapped
# java -jar paper-1.21-44.jar --nogui
java -Dpaper.log-level=FINE -jar paper-1.21-99.jar --nogui

# cd ../spigot-test-1.21/
# java -jar spigot-1.21.jar --nogui
