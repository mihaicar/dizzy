#!/bin/bash

clear

echo "Deploying new node... (easy hardcorded version)"
echo
#./gradlew samples:trader-demo:deployExtraNode -PnodeName=$1 -PnodeCity=$2 -Ppport=$3 -Prport=$4 -Pwport=$5
./gradlew samples:trader-demo:deployExtraNode -PnodeName=$1 -PnodeCity="London" -Ppport=10030 -Prport=10031 -Pwport=10032 &
wait $!

#osascript <<EOF
#tell app "Terminal" 
#    activate
#end tell
#tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
#    delay 0.5
#    do script "bash -c ls" in selected tab of the front window
#end tell
#EOF

echo "bash -c 'cd /Users/mikecar/MCorda/dizzy/corda/samples/trader-demo/build/nodes/$1; /usr/libexec/java_home -v 1.8 --exec java -jar corda-webserver.jar  && exit'" > start$1.sh
chmod +x start$1.sh
cd /Users/mikecar/MCorda/dizzy/corda/samples/trader-demo/build/nodes/$1; /usr/libexec/java_home -v 1.8 --exec java -jar corda.jar  && exit

