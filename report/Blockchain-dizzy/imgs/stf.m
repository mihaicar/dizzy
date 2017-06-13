val vault = serviceHub.vaultService
val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
val currentOwner = serviceHub.myInfo.legalIdentity

// Stage 1.
// Retrieve needed shares and add them as input/output to the tx builder
val txb = TransactionType.General.Builder(notary.notaryIdentity)
val (tx, keys) = vault.generateShareSpend(txb, qty, ticker,
                        otherParty.owningKey, value = value)

// Stage 2. Verify and sign the share transaction
tx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
val ptx = tx.signWith(serviceHub.legalIdentityKey)
                                .toSignedTransaction(false)

// Stage 3. Collect signature from buyer side.
// This also verifies the transaction and checks the signatures.
val shareSTX = subFlow(SignTransferFlow.Initiator(ptx))

//Stage 4. Send the buyer info for the cash tx
val items = SellerTransferInfo(qty, ticker,
                               value, currentOwner.owningKey)
send(otherParty, items)

// Stage 5. Retrieve the tx for cash movement
val newCash = receive<SignedTransaction>(otherParty).unwrap { it }

// Stage 6. Notarise and record the share transaction in
// participating nodes' vaults
val newShare = subFlow(FinalityFlow(shareSTX, 
                       setOf(currentOwner, otherParty))).single()
