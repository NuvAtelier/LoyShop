#!/bin/sh
export MAVEN_OPTS="-Xms2g -Xmx4g"
mvn clean compile package -T 2C
