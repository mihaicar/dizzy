#!/bin/bash

clear

echo "Deploying and starting the nodes..."
echo

./gradlew samples:trader-demo:deployNodes && ./samples/trader-demo/build/nodes/runnodes


