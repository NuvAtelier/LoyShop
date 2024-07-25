# Shop Plugin by Snowgears

## Description
This repository contains the source code for the "Shop" Spigot Plugin. This plugin enhances the Minecraft server experience by providing a customizable shop feature. Follow the instructions below to compile and build the plugin.

## Compiling Prerequisites
Before you begin, ensure you have met the following requirements:

- Docker: Install Docker from the official website.
- JDK 21: Ensure you have JDK 21 installed. You can download it from the Oracle website.
- Maven: Verify Maven is installed. You can download it from the Maven website.

### Building the Local Maven Repository
Before compiling the plugin, you need to set up the local Maven repository with the supported versions of Spigot.

- Open a terminal.
- Navigate to the root directory of the repository.
- Run the script to build the local Maven repository:
```shell
# Pull the latest image from Docker Hub and extract the Maven repo from it
./buildMavenRepo.sh
# Alternatively, you can build the image locally on your machine, it just might take an hour or two
# useful if you add a new version to the Dockerfile
./buildMavenRepo.sh local
```

This script will compile all supported versions of Spigot and copy the Maven repository into the Shop folder for later use.

## Compiling the Plugin
After setting up the local Maven repository, you can compile the Shop plugin.

- Open a terminal.
- Navigate to the root directory of the repository.
- Run the script to compile the plugin:
```shell
./compile.sh
```

The compiled plugin will be stored in the target directory with the filename Shop-version.jar.

## Usage
After compiling, you can find the plugin jar file in the target directory. Copy this jar file into the plugins folder of your Spigot server to use the Shop plugin.