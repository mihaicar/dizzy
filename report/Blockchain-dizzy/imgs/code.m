private fun verify(data: UntrustworthyData<SellerTransferInfo>): 
                                            SellerTransferInfo {
    return data.unwrap{
        // is price accurately describing the asset?
        val sharePrice = StockFetch.getPrice(it.ticker)
        if (sharePrice.toDouble() * 100.0 > it.price.quantity) {
            throw UnacceptablePriceException(it.price, 
                                        DOLLARS(sharePrice.toDouble()))
        }
        // do we have acceptable prices and quantities (>0)
        if (it.price.quantity <= 0 || it.qty <= 0) {
            throw UnacceptableNegativeException(it.price.quantity, 
                                                it.qty)
        }
        // check whether the seller is indeed the other party
        if (it.sellerOwnerKey != otherParty.owningKey) {
            throw UnacceptableSellerException(it.sellerOwnerKey, 
                                              otherParty.owningKey)
        }
        it
    }
}