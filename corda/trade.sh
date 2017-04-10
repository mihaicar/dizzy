#!/bin/bash

clear
#echo "Commencing trade for "$2" shares in "$3" priced at a total of "$1
echo "Easy demo version! Revert back to arg version!"
echo

#./gradlew samples:trader-demo:runSeller -Pamt=$1 -Pqty=$2 -Ptck=$3
./gradlew samples:trader-demo:runSeller -Pamt="\$300" -Pqty=2 -Ptck=AAPL
#./gradlew samples:trader-demo:runSeller -Pamt="\$10000" -Pqty=2 -Ptck=AAPL



