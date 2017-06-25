if (role == Role.RANDOM_TRANSACT) {
    val vals = varDeclaration()
    @Suppress("UNCHECKED_CAST")
    val stocks = vals[1] as Map<String, Amount<Currency>>
    val rand = vals[2] as Random
    val stocksAsArray = ArrayList<String>(stocks.keys)
    val banksAsArray = ArrayList<String>(ports.keys.minus("Exchange"))
    var tries = 0

    while (true) {
        val stock = rand.nextInt(2)
        val buyer = banksAsArray[rand.nextInt(ports.size - 1)]
        val seller = banksAsArray[rand.nextInt(ports.size - 1)]
        transferShares(buyer, seller, ports, stocks[stocksAsArray[stock]]
                       as Amount<Currency>, stocksAsArray[stock])
        tries++

        // Display balances every 10 transactions
        if (tries % 10 == 0) {
            for (entity in banksAsArray) {
                displayBalances(entity, ports)
            }
        }
    }
}