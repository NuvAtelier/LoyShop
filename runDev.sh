#!/bin/sh

export MAVEN_OPTS="-Xms8g -Xmx16g"
mvn clean compile package -T 8C -o

# Copy latest plugin in
# rm ../spigot-test-1.21/plugins/Shop-*.jar 
# cp target/Shop-*.jar ../spigot-test-1.21/plugins

# rm ../paper-test-1.20.6/plugins/Shop-*.jar 
# cp target/Shop-*.jar ../paper-test-1.20.6/plugins

# rm ../paper-test-1.21/plugins/Shop-*.jar 
# cp target/Shop-*.jar ../paper-test-1.21/plugins

# rm ../paper-test-1.21.3/plugins/Shop-*.jar 
# cp target/Shop-*.jar ../paper-test-1.21.3/plugins

rm ../paper-test-1.21.4/plugins/Shop-*.jar 
cp target/Shop-*.jar ../paper-test-1.21.4/plugins

# rm ~/Downloads/Shop-*.jar # Remove any pre-existing jars
# # rm -r ~/Downloads/Shop-* # Remove unzipped files
# cp target/Shop-*.jar ~/Downloads

# Startup latest minecraft server version to test
# cd ../paper-test-1.20.6/
# rm -r plugins/.paper-remapped
# java -jar paper-1.20.6-148.jar --nogui

cd ../paper-test-1.21.4/
# rm -r plugins/.paper-remapped
java -Xms4G -Xmx8G -jar paper-1.21.4*.jar --nogui

# cd ../spigot-test-1.21/
# java -jar spigot-1.21.jar --nogui
