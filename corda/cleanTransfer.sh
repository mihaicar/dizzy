#!/bin/bash

clear

echo "Deploying and starting the nodes..."
echo

./gradlew samples:trader-demo:deployNodes && ./samples/trader-demo/build/nodes/runnodes

echo "Issuing money for the buyer"
./gradlew samples:trader-demo:runBuyer

echo "Issuing 6 shares of AAPL, in 2 batches"
./gradlew samples:trader-demo:runSeller -Pamt="\$150" -Pqty=2 -Ptck=AAPL
&
./gradlew samples:trader-demo:runSeller -Pamt="\$150" -Pqty=4 -Ptck=AAPL

echo "Trading with the other party"
./gradlew samples:trader-demo:runSellerTransfer -Pamt="\$150" -Pqty=5 -Ptck=AAPL
