task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['build']) {
    ext.rpcUsers = [['user': "demo", 'password': "demo", 'permissions': [
            'StartFlow.net.corda.flows.IssuerFlow$IssuanceRequester',
            "StartFlow.net.corda.traderdemo.flow.SellerFlow",
            "StartFlow.net.corda.traderdemo.flow.SellerTransferFlow"
    ]]]

    directory "./build/nodes"
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
    node {
        name "Bank A"
        nearestCity "London"
        advertisedServices = []
        p2pPort 10005
        rpcPort 10006
        webPort 10007
        cordapps = []
        rpcUsers = ext.rpcUsers
    }
/*  This has to be added to the build script if we want to add a node  
    node {
        name "Bank B"
        nearestCity "New York"
        advertisedServices = []
        p2pPort 10008
        rpcPort 10009
        webPort 10010
        cordapps = []
        rpcUsers = ext.rpcUsers
    }
*/
}