private fun transferShares(buyer: String, seller: String,
                           ports: Map<String, Pair<Int, String>>,
                           amount: Amount<Currency>, ticker: String) {
    try {
        CordaRPCClient(HostAndPort.fromString("${ports[seller]?.second}:
                        ${ports[seller]?.first}")).use("demo", "demo") {
            TraderDemoClientApi(this)
                    .runSellerTransferR(amount, buyer, 1, ticker)
        }
    } catch (ex: InsufficientBalanceException) {
        println("Not enough cash to buy shares! + ${ex.message}")
        tryIssueCashTo(buyer, ports)
    } catch (ex: InsufficientSharesException) {
        println("Not enough shares to sell! + ${ex.message}")
        tryIssueSharesTo(buyer, "Exchange", ports, amount, ticker)
    }
}