#!/bin/bash
JAVA_DOWNLOAD_URL="https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2+9/OpenJDK22U-jdk_x64_windows_hotspot_22.0.2_9.zip"
wget "$JAVA_DOWNLOAD_URL" -O jdk.zip
unzip jdk.zip
rm jdk.zip
mv jdk-22.0.2+9 jdk
