package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.*
import net.corda.core.crypto.SecureHash

/**
 * Specifies the contract between two parties that are agreeing to a portfolio of trades and valuating that portfolio.
 * Implements an agree clause to agree to the portfolio and an update clause to change either the portfolio or valuation
 * of the portfolio arbitrarily.
 */
data class PortfolioSwap(override val legalContractReference: SecureHash = SecureHash.sha256("swordfish")) : Contract {
    override fun verify(tx: TransactionForContract) = verifyClause(tx, AllOf(Clauses.Timestamped(), Clauses.Group()), tx.commands.select<Commands>())

    interface Commands : CommandData {
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to portfolio
        class Update : TypeOnlyCommandData(), Commands // Both sides re-agree to portfolio
    }

    interface Clauses {
        class Timestamped : Clause<ContractState, Commands, Unit>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(tx.timestamp?.midpoint != null) { "must be timestamped" }
                // We return an empty set because we don't process any commands
                return emptySet()
            }
        }

        class Group : GroupClauseVerifier<PortfolioState, Commands, UniqueIdentifier>(FirstOf(Agree(), Update())) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<PortfolioState, UniqueIdentifier>>
                    // Group by Trade ID for in / out states
                    = tx.groupStates() { state -> state.linearId }
        }

        class Update : Clause<PortfolioState, Commands, UniqueIdentifier>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Update::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<PortfolioState>,
                                outputs: List<PortfolioState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Update>()

                requireThat {
                    "there is only one input" by (inputs.size == 1)
                    "there is only one output" by (outputs.size == 1)
                    "the valuer hasn't changed" by (inputs[0].valuer == outputs[0].valuer)
                    "the linear id hasn't changed" by (inputs[0].linearId == outputs[0].linearId)
                }

                return setOf(command.value)
            }
        }

        class Agree : Clause<PortfolioState, Commands, UniqueIdentifier>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Agree::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<PortfolioState>,
                                outputs: List<PortfolioState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Agree>()

                requireThat {
                    "there are no inputs" by (inputs.size == 0)
                    "there is one output" by (outputs.size == 1)
                    "valuer must be a party" by (outputs[0].parties.contains(outputs[0].valuer))
                    "all participants must be parties" by (outputs[0].parties.map { it.owningKey }.containsAll(outputs[0].participants))
                }

                return setOf(command.value)
            }
        }
    }
}
