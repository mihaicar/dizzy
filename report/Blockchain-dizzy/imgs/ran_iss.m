if (role == Role.RANDOM_ISSUE) {
    val vals = varDeclaration()
    @Suppress("UNCHECKED_CAST")
    val stocks = vals[1] as Map<String, Amount<Currency>>
    val rand = vals[2] as Random
    val stocksAsArray = ArrayList<String>(stocks.keys)
    val banksAsArray = ArrayList<String>(ports.keys.minus("Exchange"))

    // Issue money for Banks
    for (entity in banksAsArray) {
        tryIssueCashTo(entity, ports)
    }

    // Issue some shares for the banks
    var tries = 20
    while (tries > 0) {
        val stock = rand.nextInt(stocks.size)
        val p = rand.nextInt(ports.size - 1)
        tryIssueSharesTo(banksAsArray[p], "Exchange", ports,
                        stocks[stocksAsArray[stock]] as Amount<Currency>,
                        stocksAsArray[stock])
        tries--
    }
}