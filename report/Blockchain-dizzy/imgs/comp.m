companion object {
    val logger: Logger = loggerFor<TraderDemo>()
    val ipM1 = "146.169.47.223"
    val ipM2 = "146.169.47.221"
    val ports = mapOf(
            "Bank A" to Pair(10006, ipM1),
            "Bank B" to Pair (10009, ipM1),
            "Bank C" to Pair (10015, ipM2),
            "Beaufort" to Pair(10021, ipM1),
            "Hargreaves" to Pair(10048, ipM2),
            "Exchange" to Pair(10041, ipM2))
}