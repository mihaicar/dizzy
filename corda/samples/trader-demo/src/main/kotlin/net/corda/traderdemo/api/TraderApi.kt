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
     * Returns all parties registered with the [NetworkMapService], the names can be used to look-up identities
     * by using the [IdentityService].
     */
    @GET
    @Path("banks")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBanks() = mapOf("banks" to services.networkMapUpdates().first
            .map { it.legalIdentity.name }
            .filter { it != myLegalName && it != NOTARY_NAME && it != BANK_OF_CORDA && it != EXCHANGE})

    /**
     * Displays all purchase order states that exist in the vault.
     */
    @GET
    @Path("shares")
    @Produces(MediaType.APPLICATION_JSON)
    fun getShares() = services.getShareBalances()

    /**
     * Displays all purchase order states that exist in the vault.
     */
    @GET
    @Path("share-tickers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getShareTickers() = services.getShareBalances().keys

    @GET
    @Path("cash-balance")
    @Produces(MediaType.APPLICATION_JSON)
    //returns map currency - amount(currency)
    fun getCashBalance() = services.getCashBalances()
//    fun getCashBalance() = {
//        val cash = services.getCashBalances()
//        val cashMap = mutableMapOf<String, Double>()
//        for ((key, value) in cash) cashMap[key.symbol.toString()] = value.quantity
//    }

    @GET
    @Path("{ticker}/get-quote")
    fun getQuote(@PathParam("ticker") ticker: String) : String  = StockFetch.getPrice(ticker)

    /**
     * This should only be called from the 'buyer' node. It initiates a flow to agree a purchase order with a
     * seller. Once the flow finishes it will have written the purchase order to ledger. Both the buyer and the
     * seller will be able to see it when calling /api/example/purchase-orders on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("{party}/sell-shares")
    fun sellShares(tradeInfo: Share, @PathParam("party") partyName: String): Response {
        val otherParty = services.partyFromName(partyName) ?: return Response.status(Response.Status.BAD_REQUEST).build()
        val result = services.startFlow(::SellerTransferFlow, otherParty, tradeInfo.qty, tradeInfo.ticker, DOLLARS(tradeInfo.price))
                .returnValue
                .getOrThrow()

        when (result) {
            is FlowResult.Success ->
                return Response
                        .status(Response.Status.CREATED)
                        .entity(result.message)
                        .build()
            is FlowResult.Failure ->
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(result.message)
                        .build()
        }
    }

    @PUT
    @Path("{party}/sell-fresh-shares")
    fun sellFreshShares(tradeInfo: Share, @PathParam("party") partyName: String): Response {
        val otherParty = services.partyFromName(partyName) ?: return Response.status(Response.Status.BAD_REQUEST).build()
        val result = services.startFlow(::SellerFlow, otherParty, DOLLARS(tradeInfo.price), tradeInfo.qty, tradeInfo.ticker)
                    .returnValue
                    .getOrThrow()


        when (result) {
            is FlowResult.Success -> {
                var resp = Response
                    .status(Response.Status.CREATED)
                    .entity(result.message)
                    .build()
                return Response
                    .status(Response.Status.CREATED)
                    .entity(result.message)
                    .build()
            }
            is FlowResult.Failure ->
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(result.message)
                        .build()
        }
    }
}