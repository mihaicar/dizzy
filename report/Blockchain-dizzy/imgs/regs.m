val regulators = serviceHub.networkMapCache.regulatorNodes
if (regulators.isNotEmpty()) {
    regulators.forEach { send(it.serviceIdentities(ServiceType.regulator)
    							.first(), fullySigned) }
}