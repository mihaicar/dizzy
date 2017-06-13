val regulators = serviceHub.networkMapCache.regulatorNodes
if (regulators.isNotEmpty()) {
    // Copy the transaction to every regulator in the network.
    regulators.forEach { 
    	send(it.serviceIdentities(ServiceType.regulator).first(), 
    		 fullySignedTransaction) 
    }
}