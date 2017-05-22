echo "Changing IP for the nodes."

cd samples/trader-demo/build/nodes

sed -i 's/localhost/146.169.47.221/g' Notary/node.conf
sed -i 's/localhost/146.169.47.221/g' BankC/node.conf
sed -i 's/localhost/146.169.47.221/g' Hargreaves/node.conf
sed -i 's/localhost/146.169.47.221/g' Exchange/node.conf