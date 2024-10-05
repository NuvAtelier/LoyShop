![520ef725efdc8caad836d0370a17d58ce8ee99b2](https://github.com/user-attachments/assets/075aaff3-2328-4672-89af-32bc86ec3fcd)

## Description
Allows players to quickly create shops to buy, sell, barter, or gamble items seamlessly!

By focusing on ease of use, players of any skill level can create in-game shops in a way that feels like a native feature.

## Build a Release
* Update version in `/pom.xml`
* `./compile.sh`
* Plugin is built to `/target/shop-{version}.jar`

## Versioning
`breaking.mcupdate.feature.bugfix`
- `breaking`: Changes that are not backwards compatible, or complete overhaul of plugin
- `mcupdate`: When a new major version of Minecraft comes out including new features that impact Shop
- `feature`: Addition of new/reworked features, or significantly refactored code
- `bugfix`: 

Examples:
- `v1.x.x.x` -> `v2.x.x.x`: Backwards incompatible changes
- `v1.8.x.x` -> `v1.9.x.x`: Minecraft 1.21 released
- `v1.9.1.x` -> `v1.9.2.x`: New feature added to Shop
- `v1.9.1.0` -> `v1.9.1.1`: Bug was fixed

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

