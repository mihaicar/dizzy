task deployExtraNode(type: net.corda.plugins.Cordform, dependsOn: ['build']) {
    ext.rpcUsers = [['user': "demo", 'password': "demo", 'permissions': [
            'StartFlow.net.corda.flows.IssuerFlow$IssuanceRequester',
            "StartFlow.net.corda.traderdemo.flow.SellerFlow",
            "StartFlow.net.corda.traderdemo.flow.SellerTransferFlow"
    ]]]
    directory "./build/nodes"
    // Rebuild the notary & BoC (on-the-fly) to add a new entry to the network map
    networkMap "Notary"
    node {
        name "Notary"
        nearestCity "London"
        advertisedServices = ["corda.notary.validating"]
        p2pPort 10002
        webPort 10004
        cordapps = []
    }
    node {
        name "BankOfCorda"
        nearestCity "London"
        advertisedServices = []
        p2pPort 10011
        webPort 10013
        cordapps = []
    }
    // The new node is registered to the same network map as all the others
    // We pass parameters for name, city and ports
    if (project.hasProperty("nodeName")) {
        node {
            name nodeName
            nearestCity nodeCity
            advertisedServices = []
            p2pPort Integer.parseInt(pport)
            rpcPort Integer.parseInt(rport)
            webPort Integer.parseInt(wport)
            cordapps = []
            rpcUsers = ext.rpcUsers
        }
    }

}