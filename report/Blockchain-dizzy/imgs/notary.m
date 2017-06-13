@Suspendable
override fun receiveAndVerifyTx(): TransactionParts {
    val stx = receive<SignedTransaction>(otherSide).unwrap { it }
    checkSignatures(stx)
    val wtx = stx.tx
    validateTransaction(wtx)
    return TransactionParts(wtx.id, wtx.inputs, wtx.timestamp)
}