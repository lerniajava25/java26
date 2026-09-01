package org.example;

import java.util.ArrayList;
import java.util.List;

public class StreamDemo {
    static void main() {
        List<String> strings = List.of("Ett", "Två", "Tre", "Fyra");

        //Imperativ style, keep all strings starting with T
        List<String> result = new ArrayList<>();
        for (int i = 0; i < strings.size(); i++) {
            if (strings.get(i).startsWith("T"))
                result.add(strings.get(i));
        }
        //result.forEach(IO::println);

        //Deklarativ style, using java streams
        var resultFromStream = strings.stream()
                .filter(s -> s.startsWith("T"))
                .toList();

        resultFromStream.forEach(IO::println);

        //Rule1. Filter as early as possible
        //Rule2. Divide complicated filters into multiple steps or call method
        resultFromStream = strings.stream()
                .filter(s -> s.length() <= 3)
                .filter(StreamDemo::notStartingWithT)
                .map(s -> s.substring(0, 1))
                .map(String::toLowerCase)
                .toList();

        resultFromStream.forEach(IO::println);

        //Goal, find out how many strings that starts with T in the list
        var count = strings.stream()
                .filter(s -> s.startsWith("T"))
                .count();
        IO.println(count);

        //Goal, find number of products in total
        List<Product> products = List.of(
                new Product("Apple", 2),
                new Product("Grape", 2),
                new Product("Banana", 10),
                new Product("Bappelsin", 3));

        //When dealing with primitive datatypes use mapToInt, mapToLong, mapToDouble
        var sum = products.stream()
                .mapToInt(p -> p.count())
                .sum();

        IO.println("Total number of products: " + sum);
    }

    record Product(String name, int count) {
    }


    private static boolean notStartingWithT(String s) {
        return !s.startsWith("T");
    }
}
