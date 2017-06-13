fun generateIssue(issuance: PartyAndReference,
	 faceValue: Amount<Issued<Currency>>, maturityDate: Instant, 
	 notary: Party, qty: Long, ticker: String): TransactionBuilder {

    val state = TransactionState(State(issuance, issuance.party.owningKey
    				,faceValue, maturityDate, qty, ticker), notary)
    return TransactionType.General.Builder(notary = notary)
        .withItems(state, Command(Commands.Issue(),
        						  issuance.party.owningKey))
}