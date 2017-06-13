@Suspendable
private fun logIssuanceAttachment(tradeTX: SignedTransaction) {
    // Find the original share issuance from the Transaction Graph
    val search = TransactionGraphSearch(
        serviceHub.storageService.validatedTransactions,
        listOf(tradeTX.tx))
    search.query = TransactionGraphSearch.Query(
        withCommandOfType = ShareContract.Commands.Issue::class.java,
        followInputsOfType = ShareContract.State::class.java)
    
    val shareIssuance = search.call().single()
    shareIssuance.attachments.first().let {
        val p = attachmentsPath.toAbsolutePath().resolve("$it.jar")
        println("Attachment for $tradeTX: $p.")
    }
}