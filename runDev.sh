#!/bin/sh
# Get the short commit hash, take full hash then get just the first 7 characters
COMMIT_HASH=$(git rev-parse --short HEAD)
# Read the current version from pom.xml
CURRENT_VERSION=$(grep -m 1 "<revision>" pom.xml | sed 's/.*<revision>\(.*\)<\/revision>.*/\1/')
# Create new version with commit hash and human readable timestamp
TIMESTAMP=$(date +"%b-%d-%Y_%H-%M")
NEW_VERSION="${CURRENT_VERSION}-${COMMIT_HASH}-${TIMESTAMP}-dev"
# Move this into a function
function updateVersion() {
    # Update the version in pom.xml
    sed -i '' "s|<revision>$CURRENT_VERSION</revision>|<revision>$NEW_VERSION</revision>|" pom.xml
    echo "Updated version from $CURRENT_VERSION to $NEW_VERSION"
}
function resetVersion() {
    # Reset the version in pom.xml
    # Is this global? do we need to pass in the version?
    sed -i '' "s|<revision>$NEW_VERSION</revision>|<revision>$CURRENT_VERSION</revision>|" pom.xml
    echo "Reset version from $NEW_VERSION to $CURRENT_VERSION"
}


# Temporarily update the version to the commit hash while we build the dev version
updateVersion
# Ensure Maven toolchain for JDK 21 is present so tests run with MockBukkit
mkdir -p ~/.m2
cat > ~/.m2/toolchains.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>21</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>${JAVA_HOME}</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
EOF

# Build the plugin
export MAVEN_OPTS="-Xms8g -Xmx16g"
mvn clean compile package -T 8C #-o
# Reset the version in pom.xml
resetVersion

# Copy latest plugin in
rm ../paper-test-1.21.8/plugins/Shop-*.jar 
cp target/Shop-*.jar ../paper-test-1.21.8/plugins

cd ../paper-test-1.21.8/
java -Xms4G -Xmx8G -jar paper*.jar --nogui
