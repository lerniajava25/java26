void main() {
    List<String> strings = List.of("Ett", "Två", "Tre", "Fyra");

    strings.forEach(s -> IO.println(s)); //Lambda implementation of Functional interface
    //Functional interface has only one abstract method
    strings.forEach(IO::println); //Method reference since println has same return and parameters

    strings.forEach(printItems().andThen(s -> IO.println("Second consumer says hi!")));
}

public static Consumer<String> printItems() {
    String preText = "Item: ";
    return s -> IO.println(preText + s);
}

//Avoid returning references to modifiable collections
//Make a copy or make immutable
private List<Integer> numbers = new ArrayList<>();

public List<Integer> getNumbers() {
    //return new ArrayList<>(numbers);
    return Collections.unmodifiableList(numbers);
}
