@PUT
@Path("{party}/sell-shares")
fun sellShares(tradeInfo: Share,
			   @PathParam("party") partyName: String): String
{
    val otherParty = services.partyFromName(partyName) 
    			   ?: return "bad party"
    val result = services.startFlow(::SellerTransferFlow, otherParty,
    		     					  tradeInfo.qty, tradeInfo.ticker,
    		     					  DOLLARS(tradeInfo.price))
						 .returnValue
						 .getOrThrow()
    return result
}