fun runBuyer(amount: Amount<Currency> = 30000.DOLLARS) {
    val bankOfCordaParty = rpc.partyFromName(BOC.name)
            ?: throw Exception("Unable to locate ${BOC.name}
                               in Network Map Service")
    val me = rpc.nodeIdentity()
    val amounts = calculateRandomlySizedAmounts(amount, 3, 10, Random())
    // issuer random amounts of currency totaling $30000 in parallel
    val resultFutures = amounts.map { pennies ->
        rpc.startFlow(::IssuanceRequester, Amount(pennies, amount.token),
        me.legalIdentity, OpaqueBytes.of(1), bankOfCordaParty).returnValue
    }
    Futures.allAsList(resultFutures).getOrThrow()
    println("Requested and received in ${me.legalIdentity.name}")
}