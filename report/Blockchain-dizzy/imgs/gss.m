@Suspendable
override fun generateShareSpend(tx: TransactionBuilder, qty: Long,
                                ticker: String, to: CompositeKey,
                                onlyFromParties: Set<AbstractParty>?, 
                                value : Amount<Currency>)
    : Pair<TransactionBuilder, List<CompositeKey>> 
{
    // get all the unconsumed states for share spending:
    val lock: UUID = UUID(1234, 1234)
    val price = value `issued by` DUMMY_CASH_ISSUER
    val acceptableShares 
       = unconsumedStatesForShareSpending<ShareContract.State>(qty = qty,
                     lockId = lock, ticker = ticker, notary = tx.notary);
    
    // notary may be associated with locked state only?
    tx.notary = acceptableShares.firstOrNull()?.state?.notary
    // Adapted algorithm for shares
    // get change from number of shares
    val (gathered, gatheredAmount) = gatherShares(acceptableShares, qty)
    val takeChangeFrom = gathered.firstOrNull()
    val change = if (takeChangeFrom != null && gatheredAmount > qty) {
        gatheredAmount - qty
    } else {
        null
    }

    // derive shares depending on new owner
    var keysUsed = gathered.map { it.state.data.owner }
    val states = gathered.groupBy { it.state.data.ticker }.map {
        val sh = it.value
        val totalAmount = sh.map { it.state.data.qty }.sum()
        deriveShareState(sh.first().state, totalAmount, to, price)
    }.sortedBy { it.data.qty}

    // now, if we have some change left, split the state
    // between the original owner and the buyer
    val outputs = if (change != null ) {
        val existingOwner = gathered.first().state.data.owner
        states.subList(0, states.lastIndex) +
                states.last().let {
                    val spent = it.data.qty - change
                    deriveShareState(it, spent, it.data.owner, price)
                } +
                states.last().let{
                    deriveShareState(it, change, existingOwner, price)
                }
    } else states

    // add the states as inputs and outputs to the tx builder
    for (state in gathered) {
        tx.addInputState(state)
        keysUsed += state.state.data.participants
    }
    for (state in outputs) {
        tx.addOutputState(state)
    }

    // add the transfer command and return the tx builder
    tx.addCommand(ShareContract.Commands.Transfer(), keysUsed)
    return Pair(tx, keysUsed)

}