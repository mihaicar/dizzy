package net.corda.stockinfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Pattern;

public class StockFetch {

    public static String getPrice(String symbol) {
        String price = "0";
        symbol.toUpperCase();
        try {
            // Retrieve CSV File
            URL yahoo = new URL("http://finance.yahoo.com/d/quotes.csv?s=" + symbol + "&f=l1");
            URLConnection connection = yahoo.openConnection();
            InputStreamReader is = new InputStreamReader(connection.getInputStream());
            BufferedReader br = new BufferedReader(is);
            // Parse CSV Into Array
            String line = br.readLine();
            //Only split on commas that aren't in quotes
            String[] stockinfo = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

            if (!Pattern.matches("N/A", stockinfo[0])) {
                price = stockinfo[0];
            }

        } catch (IOException | NullPointerException e) {
            System.out.println("error retrieving price " + e.getMessage());
        }
        System.out.println("Price retrieved from Yahoo: " + price);
        return price;
    }
}
