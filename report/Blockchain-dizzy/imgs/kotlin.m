// each element in the list has a ticker and a qty attribute
val list = getList()

// we want to sum up the qty for every ticker that's AAPL
var sum = 0L
list
        .filter { it.ticker == "AAPL" }
        .forEach { sum += it.qty }

println("List has a total of $sum in $ticker.")