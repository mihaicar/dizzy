@Suspendable
@Throws(NotaryException::class)
override fun call(): List<SignedTransaction> {
    val me = serviceHub.myInfo.legalIdentity
    val notarisedTxns = notariseAndRecord(lookupParties(
                        resolveDependenciesOf(transactions)))

    // Each transaction has its own set of recipients,
    // but extra recipients get them all
    // Regulator nodes will always be added as extra recipients
    for ((stx, parties) in notarisedTxns) {
        subFlow(BroadcastTransactionFlow(stx, 
                                         parties + extraRecipients - me))
    }
    return notarisedTxns.map { it.first }
}