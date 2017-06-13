@CordaSerializable
data class SellerTransferInfo(
        val qty: Long,
        val ticker: String,
        val price: Amount<Currency>,
        val sellerOwnerKey: CompositeKey
)