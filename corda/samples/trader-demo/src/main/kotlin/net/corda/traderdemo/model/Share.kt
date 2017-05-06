package net.corda.traderdemo.model

data class Share(val ticker: String,
                 val qty: Long,
                 val price: Double)