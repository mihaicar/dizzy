class Issue : AbstractIssue<State, Commands, Terms>(
        { map { Amount(it.faceValue.quantity, it.token) }.sumOrThrow() },
        { token -> map { Amount(it.faceValue.quantity, it.token) }
                                            .sumOrZero(token) }) {
    override val requiredCommands: Set<Class<out CommandData>> 
            = setOf(Commands.Issue::class.java)

    override fun verify(tx: TransactionForContract,
                        inputs: List<State>,
                        outputs: List<State>,
                        commands: List<AuthenticatedObject<Commands>>,
                        groupingKey: Issued<Terms>?): Set<Commands> {
        val consumedCommands = super.verify(tx, inputs, outputs,
                                            commands, groupingKey)

        commands.requireSingleCommand<Commands.Issue>()
        val timestamp = tx.timestamp
        val time = timestamp?.before ?: 
            throw IllegalArgumentException("No timestamp on issuance")
        // Requirements created during this project
        // Verifies the maturity date has passed 
        // (forbid malformed transactions happening on wrong dates)
        require(outputs.all { time < it.maturityDate }) 
        { "maturity date is not in the past" }
        
        // Checks whether the offered price corresponds to the exchange
        // price (we do not care about the cash issuer so we use a dummy)
        require(outputs.all 
            { it.faceValue > 
            (Amount.parseCurrency("$" + StockFetch.getPrice(it.ticker))
                                  `issued by` DUMMY_CASH_ISSUER)}) 
        {"not enough cash for this pricey share"}
        
        return consumedCommands
    }
}