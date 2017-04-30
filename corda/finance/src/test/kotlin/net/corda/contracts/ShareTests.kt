package net.corda.contracts

import net.corda.contracts.asset.*
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.composite
import net.corda.core.days
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.seconds
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Created by mikecar on 26/04/2017.
 */

interface IShareContractTestTemplate {
    fun getPaper(): IShareState
    fun getIssueCommand(notary: Party): CommandData
    fun getRedeemCommand(notary: Party): CommandData
    fun getMoveCommand(): CommandData
}

class KotlinShareTest() : IShareContractTestTemplate {
    override fun getPaper(): IShareState = ShareContract.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP_PUBKEY,
            faceValue = 150.DOLLARS `issued by` DUMMY_CASH_ISSUER,
            maturityDate = TEST_TX_TIME + 30.seconds,
            qty = 2,
            ticker = "AAPL"
    )

    override fun getIssueCommand(notary: Party): CommandData = ShareContract.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = ShareContract.Commands.Redeem()
    override fun getMoveCommand(): CommandData = ShareContract.Commands.Move()
}
@RunWith(Parameterized::class)
class ShareTestsGeneric {
    companion object {
        @Parameterized.Parameters @JvmStatic
        fun data() = listOf(KotlinShareTest())
    }

    @Parameterized.Parameter
    lateinit var thisTest: IShareContractTestTemplate

    //val issuer = MEGA_CORP.ref(123)
    val issuer = DUMMY_CASH_ISSUER
/*    @Test
    fun `trade lifecycle test`() {
        val someProfits = 1200.DOLLARS `issued by` issuer
        ledger {
            unverifiedTransaction {
                output("alice's $1000", 1000.DOLLARS.CASH `issued by` issuer `owned by` ALICE_PUBKEY)
                output("some profits", someProfits.STATE `owned by` MEGA_CORP_PUBKEY)
            }

            // Some shares are issued onto the ledger by MegaCorp (2 shares in AAPL, at $150 each)
            transaction("Issuance") {
                output("paper") { thisTest.getPaper() }
                command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
                timestamp(TEST_TX_TIME)
                this.verifies()
            }
            println("Issuance done")
            // The CP is sold to alice for $150 for each share.
            transaction("Trade") {
                input("paper")
                input("alice's $300")
                output("borrowed $300") { 300.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP_PUBKEY }
                output("alice's paper") { "paper".output<IShareState>() `owned by` ALICE_PUBKEY }
                command(ALICE_PUBKEY) { Cash.Commands.Move() }
                command(MEGA_CORP_PUBKEY) { thisTest.getMoveCommand() }
                this.verifies()
            }
        }
    }

    @Test
    fun `key mismatch at issue`() {
        transaction {
            output { thisTest.getPaper() }
            command(DUMMY_PUBKEY_1) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "output states are issued by a command signer"
        }
    }

    @Test
    fun `face value is not zero`() {
        transaction {
            output { thisTest.getPaper().withFaceValue(0.DOLLARS `issued by` issuer) }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transaction {
            output { thisTest.getPaper().withMaturityDate(TEST_TX_TIME - 10.days) }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "maturity date is not in the past"
        }
    }

    @Test
    fun `issue cannot replace an existing state`() {
        transaction {
            input(thisTest.getPaper())
            output { thisTest.getPaper() }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    fun cashOutputsToVault(vararg outputs: TransactionState<Cash.State>): Pair<LedgerTransaction, List<StateAndRef<Cash.State>>> {
        val ltx = LedgerTransaction(emptyList(), listOf(*outputs), emptyList(), emptyList(), SecureHash.randomSHA256(), null, emptyList(), null, TransactionType.General())
        return Pair(ltx, outputs.mapIndexed { index, state -> StateAndRef(state, StateRef(ltx.id, index)) })
    }
*/
    /**
     *  Unit test requires two separate Database instances to represent each of the two
     *  transaction participants (enforces uniqueness of vault content in lieu of partipant identity)
     */

    private lateinit var bigCorpServices: MockServices
    private lateinit var bigCorpVault: Vault<ContractState>
    private lateinit var bigCorpVaultService: VaultService

    private lateinit var aliceServices: MockServices
    private lateinit var aliceVaultService: VaultService
    private lateinit var alicesVault: Vault<ContractState>

    private lateinit var moveTX: SignedTransaction
/*
    @Test
    fun `issue move and then redeem`() {

        val dataSourcePropsAlice = makeTestDataSourceProperties()
        val dataSourceAndDatabaseAlice = configureDatabase(dataSourcePropsAlice)
        val databaseAlice = dataSourceAndDatabaseAlice.second
        databaseTransaction(databaseAlice) {

            aliceServices = object : MockServices() {
                override val vaultService: VaultService = makeVaultService(dataSourcePropsAlice)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            alicesVault = aliceServices.fillWithSomeTestCash(9000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            aliceVaultService = aliceServices.vaultService
        }

        val dataSourcePropsBigCorp = makeTestDataSourceProperties()
        val dataSourceAndDatabaseBigCorp = configureDatabase(dataSourcePropsBigCorp)
        val databaseBigCorp = dataSourceAndDatabaseBigCorp.second
        databaseTransaction(databaseBigCorp) {

            bigCorpServices = object : MockServices() {
                override val vaultService: VaultService = makeVaultService(dataSourcePropsBigCorp)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            bigCorpVault = bigCorpServices.fillWithSomeTestCash(13000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            bigCorpVaultService = bigCorpServices.vaultService
        }

        // Propagate the cash transactions to each side.
        aliceServices.recordTransactions(bigCorpVault.states.map { bigCorpServices.storageService.validatedTransactions.getTransaction(it.ref.txhash)!! })
        bigCorpServices.recordTransactions(alicesVault.states.map { aliceServices.storageService.validatedTransactions.getTransaction(it.ref.txhash)!! })

        // BigCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned initially by itself.
        val faceValue = 10000.DOLLARS `issued by` DUMMY_CASH_ISSUER
        val issuance = bigCorpServices.myInfo.legalIdentity.ref(1)
        val issueTX: SignedTransaction =
                ShareContract().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY, 2, "AAPL").apply {
                    setTime(TEST_TX_TIME, 30.seconds)
                    signWith(bigCorpServices.key)
                    signWith(DUMMY_NOTARY_KEY)
                }.toSignedTransaction()

        databaseTransaction(databaseAlice) {
            // Alice pays $9000 to BigCorp to own some of their debt.
            moveTX = run {
                val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
                aliceVaultService.generateSpend(ptx, 9000.DOLLARS, bigCorpServices.key.public.composite)
                ShareContract().generateMove(ptx, issueTX.tx.outRef(0), aliceServices.key.public.composite)
                ptx.signWith(bigCorpServices.key)
                ptx.signWith(aliceServices.key)
                ptx.signWith(DUMMY_NOTARY_KEY)
                ptx.toSignedTransaction()
            }
        }

        databaseTransaction(databaseBigCorp) {
            // Verify the txns are valid and insert into both sides.
            listOf(issueTX, moveTX).forEach {
                it.toLedgerTransaction(aliceServices).verify()
                aliceServices.recordTransactions(it)
                bigCorpServices.recordTransactions(it)
            }
        }

        databaseTransaction(databaseBigCorp) {
            fun makeRedeemTX(time: Instant): Pair<SignedTransaction, UUID> {
                val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
                ptx.setTime(time, 30.seconds)
                ShareContract().generateRedeem(ptx, moveTX.tx.outRef(1), bigCorpVaultService)
                ptx.signWith(aliceServices.key)
                ptx.signWith(bigCorpServices.key)
                ptx.signWith(DUMMY_NOTARY_KEY)
                return Pair(ptx.toSignedTransaction(), ptx.lockId)
            }
            val validRedemption = makeRedeemTX(TEST_TX_TIME + 31.days).first
            validRedemption.toLedgerTransaction(aliceServices).verify()
            // soft lock not released after success either!!! (as transaction not recorded)
        }
    }
*/
    @Test
    fun `transfer`() {
        val dataSourcePropsAlice = makeTestDataSourceProperties()
        val dataSourceAndDatabaseAlice = configureDatabase(dataSourcePropsAlice)
        val databaseAlice = dataSourceAndDatabaseAlice.second

        // Fills Alice's vault with test cash (buyer)
        databaseTransaction(databaseAlice) {

            aliceServices = object : MockServices() {
                override val vaultService: VaultService = makeVaultService(dataSourcePropsAlice)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            alicesVault = aliceServices.fillWithSomeTestCash(1000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            aliceVaultService = aliceServices.vaultService
        }

        val dataSourcePropsBigCorp = makeTestDataSourceProperties()
        val dataSourceAndDatabaseBigCorp = configureDatabase(dataSourcePropsBigCorp)
        val databaseBigCorp = dataSourceAndDatabaseBigCorp.second

        // We also fill the Big Corp's vault with cash (seller)
        databaseTransaction(databaseBigCorp) {

            bigCorpServices = object : MockServices() {
                override val vaultService: VaultService = makeVaultService(dataSourcePropsBigCorp)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            bigCorpVault = bigCorpServices.fillWithSomeTestCash(2000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            bigCorpVaultService = bigCorpServices.vaultService
        }
        // Propagate the cash transactions to each side.
        aliceServices.recordTransactions(bigCorpVault.states.map { bigCorpServices.storageService.validatedTransactions.getTransaction(it.ref.txhash)!! })
        bigCorpServices.recordTransactions(alicesVault.states.map { aliceServices.storageService.validatedTransactions.getTransaction(it.ref.txhash)!! })

        // BigCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned initially by itself.
        val faceValue = 150.DOLLARS `issued by` DUMMY_CASH_ISSUER
        val issuance = bigCorpServices.myInfo.legalIdentity.ref(1)
        val issueTX: SignedTransaction =
                ShareContract().generateIssue(issuance, faceValue, TEST_TX_TIME + 45.seconds, DUMMY_NOTARY, 2, "AAPL").apply {
                    setTime(TEST_TX_TIME, 30.seconds)
                    signWith(bigCorpServices.key)
                    signWith(DUMMY_NOTARY_KEY)
                }.toSignedTransaction()

        databaseTransaction(databaseAlice) {
            // Alice pays $300 to BigCorp to own 2 shares.
            moveTX = run {
                val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
                // Move $300 to the given pubkey
                aliceVaultService.generateSpend(ptx, 300.DOLLARS, bigCorpServices.key.public.composite)
                ShareContract().generateMove(ptx, issueTX.tx.outRef(0), aliceServices.key.public.composite)
                ptx.signWith(bigCorpServices.key)
                ptx.signWith(aliceServices.key)
                ptx.signWith(DUMMY_NOTARY_KEY)
                ptx.toSignedTransaction()
            }
        }

        databaseTransaction(databaseBigCorp) {
            // Verify the txns are valid and insert into both sides.
            listOf(issueTX, moveTX).forEach {
                it.toLedgerTransaction(aliceServices).verify()
                aliceServices.recordTransactions(it)
                bigCorpServices.recordTransactions(it)
            }
        }

        databaseTransaction(databaseBigCorp) {
            //val accShares = aliceVaultService.unconsumedStatesForShareSpending<ShareContract.State>(qty = 2, ticker = "AAPL", lockId = lockID, notary = DUMMY_NOTARY)
            //println("Alice's shares are: $accShares")
            //println("Alice: ${aliceVaultService.cashBalances}")

        }
        /*
        databaseTransaction(databaseBigCorp) {
            val accShares = bigCorpVaultService.unconsumedStatesForShareSpending<ShareContract.State>(qty = 2, ticker = "AAPL", lockId = lockID, notary = DUMMY_NOTARY)
            println("BigCorp's shares are: $accShares")
            val balances = bigCorpVaultService.cashBalances.entries.map { "${it.key.currencyCode} ${it.value}" }
            println("Balance of BigCorp is: ${balances.joinToString()}")
        }*/
    }
}