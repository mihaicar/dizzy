package net.corda.testing

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import java.io.InputStream

/**
 * This interface defines output state lookup by label. It is split from the interpreter interfaces so that outputs may
 * be looked up both in ledger{..} and transaction{..} blocks.
 */
interface OutputStateLookup {
    /**
     * Retrieves an output previously defined by [TransactionDSLInterpreter._output] with a label passed in.
     * @param clazz The class object holding the type of the output state expected.
     * @param label The label of the to-be-retrieved output state.
     * @return The output [StateAndRef].
     */
    fun <S : ContractState> retrieveOutputStateAndRef(clazz: Class<S>, label: String): StateAndRef<S>
}

/**
 * This interface asserts that the DSL at hand is capable of verifying its underlying construct(ledger/transaction).
 */
interface Verifies {
    /**
     * Verifies the ledger/transaction, throws if the verification fails.
     */
    fun verifies(): EnforceVerifyOrFail

    /**
     * Asserts that verifies() throws.
     * @param expectedMessage An optional string to be searched for in the raised exception.
     */
    fun failsWith(expectedMessage: String?): EnforceVerifyOrFail {
        val exceptionThrown = try {
            verifies()
            false
        } catch (exception: Exception) {
            if (expectedMessage != null) {
                val exceptionMessage = exception.message
                if (exceptionMessage == null) {
                    throw AssertionError(
                            "Expected exception containing '$expectedMessage' but raised exception had no message",
                            exception
                    )
                } else if (!exceptionMessage.toLowerCase().contains(expectedMessage.toLowerCase())) {
                    throw AssertionError(
                            "Expected exception containing '$expectedMessage' but raised exception was '$exception'",
                            exception
                    )
                }
            }
            true
        }

        if (!exceptionThrown) {
            throw AssertionError("Expected exception but didn't get one")
        }

        return EnforceVerifyOrFail.Token
    }

    /**
     * Asserts that [verifies] throws, with no condition on the exception message.
     */
    fun fails() = failsWith(null)

    /**
     * @see failsWith
     */
    infix fun `fails with`(msg: String) = failsWith(msg)
}


/**
 * This interface defines the bare bone functionality that a Ledger DSL interpreter should implement.
 *
 * TODO (Kotlin 1.1): Use type synonyms to make the type params less unwieldy
 */
interface LedgerDSLInterpreter<out T : TransactionDSLInterpreter> : Verifies, OutputStateLookup {
    /**
     * Creates and adds a transaction to the ledger.
     * @param transactionLabel Optional label of the transaction, to be used in diagnostic messages.
     * @param transactionBuilder The base transactionBuilder that will be used to build the transaction.
     * @param dsl The dsl that should be interpreted for building the transaction.
     * @return The final [WireTransaction] of the built transaction.
     */
    fun _transaction(transactionLabel: String?, transactionBuilder: TransactionBuilder,
                     dsl: TransactionDSL<T>.() -> EnforceVerifyOrFail): WireTransaction

    /**
     * Creates and adds a transaction to the ledger that will not be verified by [verifies].
     * @param transactionLabel Optional label of the transaction, to be used in diagnostic messages.
     * @param transactionBuilder The base transactionBuilder that will be used to build the transaction.
     * @param dsl The dsl that should be interpreted for building the transaction.
     * @return The final [WireTransaction] of the built transaction.
     */
    fun _unverifiedTransaction(transactionLabel: String?, transactionBuilder: TransactionBuilder,
                               dsl: TransactionDSL<T>.() -> Unit): WireTransaction

    /**
     * Creates a local scoped copy of the ledger.
     * @param dsl The ledger DSL to be interpreted using the copy.
     */
    fun tweak(dsl: LedgerDSL<T, LedgerDSLInterpreter<T>>.() -> Unit)

    /**
     * Adds an attachment to the ledger.
     * @param attachment The [InputStream] defining the contents of the attachment.
     * @return The [SecureHash] that identifies the attachment, to be used in transactions.
     */
    fun attachment(attachment: InputStream): SecureHash

}

/**
 * This is the class that defines the syntactic sugar of the ledger Test DSL and delegates to the contained interpreter,
 * and what is actually used in `ledger { (...) }`. Add convenience functions here, or if you want to extend the DSL
 * functionality then first add your primitive to [LedgerDSLInterpreter] and then add the convenience defaults/extension
 * methods here.
 */
class LedgerDSL<out T : TransactionDSLInterpreter, out L : LedgerDSLInterpreter<T>> (val interpreter: L) :
        LedgerDSLInterpreter<TransactionDSLInterpreter> by interpreter {

    /**
     * @see LedgerDSLInterpreter._transaction
     */
    @JvmOverloads
    fun transaction(label: String? = null, transactionBuilder: TransactionBuilder = TransactionBuilder(notary = DUMMY_NOTARY),
                    dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) =
            _transaction(label, transactionBuilder, dsl)
    /**
     * @see LedgerDSLInterpreter._unverifiedTransaction
     */
    @JvmOverloads
    fun unverifiedTransaction(label: String? = null, transactionBuilder: TransactionBuilder = TransactionBuilder(notary = DUMMY_NOTARY),
                              dsl: TransactionDSL<TransactionDSLInterpreter>.() -> Unit) =
            _unverifiedTransaction(label, transactionBuilder, dsl)

    /**
     * @see OutputStateLookup.retrieveOutputStateAndRef
     */
    inline fun <reified S : ContractState> String.outputStateAndRef(): StateAndRef<S> =
            retrieveOutputStateAndRef(S::class.java, this)

    /**
     * Retrieves the output [TransactionState] based on the label.
     * @see OutputStateLookup.retrieveOutputStateAndRef
     */
    inline fun <reified S : ContractState> String.output(): S =
            outputStateAndRef<S>().state.data

    /**
     * @see OutputStateLookup.retrieveOutputStateAndRef
     */
    fun <S : ContractState> retrieveOutput(clazz: Class<S>, label: String) =
            retrieveOutputStateAndRef(clazz, label).state.data
}
