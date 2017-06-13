fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>,
				 newOwner: CompositeKey) {
    tx.addInputState(paper)
    tx.addOutputState(TransactionState
    		(paper.state.data.copy(owner = newOwner), paper.state.notary))
    tx.addCommand(Commands.Move(), paper.state.data.owner)
}