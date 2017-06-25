// each element in the list has a ticker and a qty attribute
List<T> list = getList();

// we want to sum up the qty for every ticker that's AAPL
long sum = 0;
for (int i = 0; i < list.size(); i++) {
    if (list[i].ticker == "AAPL") {
        sum += list[i].qty;
    }
}
System.out.println("List has a total of " + sum + " in " + ticker + ".");