#!/bin/sh

mvn clean compile package -o
# Copy latest plugin in
rm ../paper-test-1.20.6/plugins/Shop-*.jar 
cp target/Shop-*.jar ../paper-test-1.20.6/plugins
rm ../paper-test-1.21/plugins/Shop-*.jar 
cp target/Shop-*.jar ../paper-test-1.21/plugins
rm ../spigot-test-1.21/plugins/Shop-*.jar 
cp target/Shop-*.jar ../spigot-test-1.21/plugins

# Startup latest minecraft server version to test
# cd ../paper-test-1.20.6/
# rm -r plugins/.paper-remapped
# java -jar paper-1.20.6-148.jar --nogui

cd ../paper-test-1.21/
rm -r plugins/.paper-remapped
java -jar paper-1.21*.jar --nogui

# cd ../spigot-test-1.21/
# java -jar spigot-1.21.jar --nogui
