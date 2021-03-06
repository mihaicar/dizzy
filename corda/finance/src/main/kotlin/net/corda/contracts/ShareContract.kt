package net.corda.contracts

import net.corda.contracts.IShareState
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.stockinfo.StockFetch
import net.corda.contracts.asset.sumCashBy
import net.corda.contracts.clause.AbstractIssue
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.by
import net.corda.core.contracts.clauses.AnyOf
import net.corda.core.contracts.clauses.Clause
import net.corda.core.contracts.clauses.GroupClauseVerifier
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.VaultService
import net.corda.core.random63BitValue
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.Emoji
import net.corda.schemas.ShareSchemaV1
import java.time.Instant
import java.util.*


val SP_PROGRAM_ID = ShareContract()

class ShareContract : Contract {
    // TODO: should reference the content of the legal agreement, not its URI
    override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Share_(finance)")

    data class Terms(
            // MC: modified terms used for the share contract
            val asset: Issued<Currency>,
            val maturityDate: Instant
    )

    override fun verify(tx: TransactionForContract) = verifyClause(tx, Clauses.Group(), tx.commands.select<Commands>())

    data class State(
            val issuance: PartyAndReference,
            override val owner: CompositeKey,
            val faceValue: Amount<Issued<Currency>>,
            val maturityDate: Instant,
            val qty: Long,
            val ticker: String
    ) : OwnableState, QueryableState, IShareState {
        override val contract = SP_PROGRAM_ID
        override val participants: List<CompositeKey>
            get() = listOf(owner)

        val token: Issued<Terms>
            get() = Issued(issuance, Terms(faceValue.token, maturityDate))

        override fun withNewOwner(newOwner: CompositeKey) = Pair(Commands.Move(), copy(owner = newOwner))
        override fun toString() = "${Emoji.newspaper}ShareContract(of $faceValue for $qty of $ticker Transferable on $maturityDate by '$issuance', owned by $owner)"

        // Although kotlin is smart enough not to need these, as we are using the IShareState, we need to declare them explicitly for use later,
        override fun withOwner(newOwner: CompositeKey): IShareState = copy(owner = newOwner)

        override fun withIssuance(newIssuance: PartyAndReference): IShareState = copy(issuance = newIssuance)
        override fun withFaceValue(newFaceValue: Amount<Issued<Currency>>): IShareState = copy(faceValue = newFaceValue)
        override fun withMaturityDate(newMaturityDate: Instant): IShareState = copy(maturityDate = newMaturityDate)
        override fun withQty(newQty: Long): IShareState = copy(qty = newQty)
        override fun withTicker(newTicker: String): IShareState = copy(ticker = newTicker)


        /** Object Relational Mapping support. */
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ShareSchemaV1)

        /** Object Relational Mapping support. */
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is ShareSchemaV1 -> ShareSchemaV1.PersistentShareState(
                        issuerParty = this.issuance.party.owningKey.toBase58String(),
                        issuerRef = this.issuance.reference.bytes,
                        owner = this.owner.toBase58String(),
                        maturity = this.maturityDate,
                        faceValue = this.faceValue.quantity,
                        //MCHANGE: added qty, ticker
                        qty = this.qty,
                        ticker = this.ticker,
                        currency = this.faceValue.token.product.currencyCode,
                        faceValueIssuerParty = this.faceValue.token.issuer.party.owningKey.toBase58String(),
                        faceValueIssuerRef = this.faceValue.token.issuer.reference.bytes
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
    }

    interface Clauses {
        class Group : GroupClauseVerifier<State, Commands, Issued<Terms>>(
                AnyOf(
                        Transfer(),
                        Move(),
                        Issue(),
                        Redeem())) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<State, Issued<Terms>>>
                    = tx.groupStates<State, Issued<Terms>> { it.token }
        }

        class Issue : AbstractIssue<State, Commands, Terms>(
                { map { Amount(it.faceValue.quantity, it.token) }.sumOrThrow() },
                { token -> map { Amount(it.faceValue.quantity, it.token) }.sumOrZero(token) }) {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Issue::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Issued<Terms>?): Set<Commands> {
                val consumedCommands = super.verify(tx, inputs, outputs, commands, groupingKey)
                commands.requireSingleCommand<Commands.Issue>()
                val timestamp = tx.timestamp
                val time = timestamp?.before ?: throw IllegalArgumentException("Issuances must be timestamped")
                // MC: this might not be the case for shares - right?
                require(outputs.all { time < it.maturityDate }) { "maturity date is not in the past" }
                // MC: Checks whether the offered price corresponds to the exchange price.
                require(outputs.all { it.faceValue > (Amount.parseCurrency("$" + StockFetch.getPrice(it.ticker))
                        `issued by` DUMMY_CASH_ISSUER)}) {"not enough cash for this pricey share"}
                return consumedCommands
            }
        }

        class Move : Clause<State, Commands, Issued<Terms>>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Move::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Issued<Terms>?): Set<Commands> {
                val command = commands.requireSingleCommand<Commands.Move>()
                val input = inputs.single()
                requireThat {
                    "the transaction is signed by the owner of the SC" by (input.owner in command.signers)
                    "the state is propagated" by (outputs.size == 1)
                    // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                    // the input ignoring the owner field due to the grouping.
                }
                return setOf(command.value)
            }
        }

        class Transfer() : Clause<State, Commands, Issued<Terms>>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Transfer::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Issued<Terms>?): Set<Commands> {
                // TODO: This should filter commands down to those with compatible subjects (underlying product and maturity date)
                // before requiring a single command
                val command = commands.requireSingleCommand<Commands.Transfer>()
                val timestamp = tx.timestamp

                val input = inputs.single()
                val received = tx.outputs.sumCashBy(input.owner)
                val time = timestamp?.after ?: throw IllegalArgumentException("Transfers must be timestamped")
                requireThat {
                    "the paper must have matured" by (time >= input.maturityDate)
                    //"the transaction is signed by the owner of the SC" by (input.owner in command.signers)
                }

                return setOf(command.value)
            }

        }


        class Redeem() : Clause<State, Commands, Issued<Terms>>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Redeem::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Issued<Terms>?): Set<Commands> {
                // TODO: This should filter commands down to those with compatible subjects (underlying product and maturity date)
                // before requiring a single command
                val command = commands.requireSingleCommand<Commands.Redeem>()
                val timestamp = tx.timestamp

                val input = inputs.single()
                val received = tx.outputs.sumCashBy(input.owner)
                val time = timestamp?.after ?: throw IllegalArgumentException("Redemptions must be timestamped")
                requireThat {
                    //"the paper must have matured" by (time >= input.maturityDate)
                    //"the received amount equals the face value" by (received == input.faceValue)
                    "the paper must be destroyed" by outputs.isEmpty()
                    //"the transaction is signed by the owner of the CP" by (input.owner in command.signers)
                }

                return setOf(command.value)
            }

        }
    }

    interface Commands : CommandData {
        data class Move(override val contractHash: SecureHash? = null) : FungibleAsset.Commands.Move, Commands
        class Transfer : TypeOnlyCommandData(), Commands
        class Redeem : TypeOnlyCommandData(), Commands
        data class Issue(override val nonce: Long = random63BitValue()) : IssueCommand, Commands
    }

    /**
     * Returns a transaction that issues share contract paper, owned by the issuing parties key. Does not update
     * an existing transaction because you aren't able to issue multiple pieces of CP in a single transaction
     * at the moment: this restriction is not fundamental and may be lifted later.
     */
    fun generateIssue(issuance: PartyAndReference, faceValue: Amount<Issued<Currency>>, maturityDate: Instant, notary: Party, qty: Long, ticker: String): TransactionBuilder {
        val state = TransactionState(State(issuance, issuance.party.owningKey, faceValue, maturityDate, qty, ticker), notary)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Issue(), issuance.party.owningKey))
    }

    /**
     * Updates the given partial transaction with an input/output/command to reassign ownership of the paper.
     */
    fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>, newOwner: CompositeKey) {
        tx.addInputState(paper)
        tx.addOutputState(TransactionState(paper.state.data.copy(owner = newOwner), paper.state.notary))
        tx.addCommand(Commands.Move(), paper.state.data.owner)
    }

    fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>, newFaceValue: Amount<Issued<Currency>>, newOwner: CompositeKey) {
        tx.addInputState(paper)
        // MC: Change in price as well as owner
        tx.addOutputState(TransactionState(paper.state.data.copy(faceValue = newFaceValue, owner = newOwner), paper.state.notary))
        tx.addCommand(Commands.Move(), paper.state.data.owner)
    }

    /**
     * Intended to be called by the issuer of some share contract paper, when an owner has notified us that they wish
     * to Transfer the paper. We must therefore send enough money to the key that owns the paper to satisfy the face
     * value, and then ensure the paper is removed from the ledger.
     *
     * @throws InsufficientBalanceException if the vault doesn't contain enough money to pay the Transferer.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateTransfer(vault: VaultService, issuance: PartyAndReference, value: Amount<Issued<Currency>>, maturityDate: Instant, notary: Party, qty: Long, ticker: String): TransactionBuilder {
        // generateIssue for new paper that will be transferred
        println("Transfer is generating new paper...")
        val tx = generateIssue(issuance, value, maturityDate, notary, qty, ticker) //paper with 5 shares

        // vault.generateShareSpend for that resulting transaction
        println("Transfer is generating the spend with ${issuance.party.owningKey}")
        vault.generateShareSpend(tx, qty, ticker, issuance.party.owningKey, value = value.withoutIssuer())
        //tx.addInputState(paper) - isnt this done in the generateSpend?
        // input as states all papers used

        // add command the transfer - already done, that's fine.

        // paper.owner, in our case is... the person who HAS the CP in their vault (so those who own the share, seller)
        // therefore, WE ARE THE OWNER: issuance.party.owningKey = paper...owner

        tx.addCommand(Commands.Transfer(), issuance.party.owningKey)
        return tx
    }

    /**
     * Intended to be called by the issuer of some commercial paper, when an owner has notified us that they wish
     * to redeem the paper. We must therefore send enough money to the key that owns the paper to satisfy the face
     * value, and then ensure the paper is removed from the ledger.
     *
     * @throws InsufficientBalanceException if the vault doesn't contain enough money to pay the redeemer.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateRedeem(tx: TransactionBuilder, paper: StateAndRef<State>, vault: VaultService) {
        // Add the cash movement using the states in our vault.
        val amount = paper.state.data.faceValue.let { amount -> Amount(amount.quantity, amount.token.product) }
        vault.generateSpend(tx, amount, paper.state.data.owner)
        tx.addInputState(paper)
        tx.addCommand(Commands.Redeem(), paper.state.data.owner)
    }
}

infix fun ShareContract.State.`owned by`(owner: CompositeKey) = copy(owner = owner)
infix fun ShareContract.State.`with notary`(notary: Party) = TransactionState(this, notary)
infix fun IShareState.`owned by`(newOwner: CompositeKey) = withOwner(newOwner)

