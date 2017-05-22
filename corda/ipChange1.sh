echo "Changing IP for the nodes."

cd samples/trader-demo/build/nodes

sed -i 's/localhost:10002/146.169.47.221:10002/g' BankA/node.conf
sed -i 's/localhost:10002/146.169.47.221:10002/g' BankB/node.conf
sed -i 's/localhost:10002/146.169.47.221:10002/g' Beaufort/node.conf
sed -i 's/localhost:10002/146.169.47.221:10002/g' BankOfCorda/node.conf

sed -i 's/localhost/146.169.47.223/g' BankA/node.conf
sed -i 's/localhost/146.169.47.223/g' BankB/node.conf
sed -i 's/localhost/146.169.47.223/g' Beaufort/node.conf
sed -i 's/localhost/146.169.47.223/g' BankOfCorda/node.conf