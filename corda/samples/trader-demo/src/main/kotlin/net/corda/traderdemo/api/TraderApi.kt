package net.corda.traderdemo.api

import net.corda.contracts.ShareContract
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.stockinfo.StockFetch
import net.corda.traderdemo.flow.FlowResult
import net.corda.traderdemo.flow.SellerFlow
import net.corda.traderdemo.flow.SellerTransferFlow
import net.corda.traderdemo.model.Share
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val NOTARY_NAME = "Notary"
val BANK_OF_CORDA = "BankOfCorda"
val EXCHANGE = "Exchange"

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
class TraderApi(val services: CordaRPCOps) {
    val myLegalName: String = services.nodeIdentity().legalIdentity.name

    /**
     * Returns the party name of the node providing this end-point.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService], the names can be used to look-up identities
     * by using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers() = mapOf("peers" to services.networkMapUpdates().first
            .map { it.legalIdentity.name }
            .filter { it != myLegalName && it != NOTARY_NAME && it != BANK_OF_CORDA})

    /**
     * Returns all participant banks registered with the [NetworkMapService], the names can be used to look-up identities
     * by using the [IdentityService].
     */
    @GET
    @Path("banks")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBanks() = mapOf("banks" to services.networkMapUpdates().first
            .map { it.legalIdentity.name }
            .filter { it != myLegalName && it != NOTARY_NAME && it != BANK_OF_CORDA && it != EXCHANGE})

    /**
     * Displays all share states that exist in the vault.
     */
    @GET
    @Path("shares")
    @Produces(MediaType.APPLICATION_JSON)
    fun getShares() = services.getShareBalances()

    /**
     * Displays all share tickers that exist in the vault.
     */
    @GET
    @Path("share-tickers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getShareTickers() = services.getShareBalances().keys

    /**
     * Returns the cash balances for different currencies that exist in the vault.
     */
    @GET
    @Path("cash-balance")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCashBalance() = services.getCashBalances()

    /**
     * Returns a quote for the given ticker
     */
    @GET
    @Path("{ticker}/get-quote")
    fun getQuote(@PathParam("ticker") ticker: String) : String  = StockFetch.getPrice(ticker)

    /**
     * This should only be called from the 'seller' node. It initiates a flow to agree a share transfer with a
     * buyer. Once the flow finishes it will have written the share transfer contract to ledger. Both the buyer and the
     * seller will be able to see its results after calling the shares api.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("{party}/sell-shares")
    fun sellShares(tradeInfo: Share, @PathParam("party") partyName: String): String {
        val otherParty = services.partyFromName(partyName) ?: return "bad party"
        val result = services.startFlow(::SellerTransferFlow, otherParty, tradeInfo.qty, tradeInfo.ticker, DOLLARS(tradeInfo.price))
                .returnValue
                .getOrThrow()
        return result
    }

    /**
     * This should only be called from the 'exchange' node. It initiates a flow to agree a share contract with a
     * buyer. Once the flow finishes it will have written the share transfer contract to ledger. The buyer will be able
     * to see the shares in its node after calling the shares api
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("{party}/sell-fresh-shares")
    fun sellFreshShares(tradeInfo: Share, @PathParam("party") partyName: String): String {
        val otherParty = services.partyFromName(partyName) ?: return "bad party"
        val result = services.startFlow(::SellerFlow, otherParty, DOLLARS(tradeInfo.price), tradeInfo.qty, tradeInfo.ticker)
                    .returnValue
                    .getOrThrow()
        return result
    }
}