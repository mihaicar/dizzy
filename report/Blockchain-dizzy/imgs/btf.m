val vault = serviceHub.vaultService
val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
val currentOwner = serviceHub.myInfo.legalIdentity

// Stage 1. Receive the tx from the seller.
val untrustedItems = receive<SellerTransferInfo>(otherParty)

// Stage 2. verify the amount is correctly determined
val items = verify(untrustedItems)

// Stage 3. Generate the cash spend
val tx = TransactionType.General.Builder(notary.notaryIdentity)
val cash = serviceHub.vaultService
                     .cashBalances[Currency.getInstance("USD")]
                     ?: throw InsufficientBalanceException(items.price)
if (cash.quantity < items.price.quantity * items.qty) {
    throw InsufficientBalanceException(items.price)
}
val (ctx, sgn) = vault.generateSpend(tx, items.price * items.qty,
                                     items.sellerOwnerKey)

// Stage 4. Verify and sign transaction
ctx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
val ptx = ctx.signWith(serviceHub.legalIdentityKey)
             .toSignedTransaction(false)

// Stage 5. Collect signature from seller.
// This also verifies the transaction and checks the signatures
val stx = subFlow(SignTransferFlow.Initiator(ptx))

// Stage 6. Notarise and record the transaction in participants' vaults.
val newCash = subFlow(FinalityFlow(stx, 
                            setOf(currentOwner, otherParty))).single()

// Stage 7. Send the signed transaction over to the seller for 
// finalisation
send(otherParty, newCash)