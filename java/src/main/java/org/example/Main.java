void main() {
    List<String> strings = List.of("Ett", "Två", "Tre", "Fyra");

    strings.forEach(s -> IO.println(s)); //Lambda implementation of Functional interface
    //Functional interface has only one abstract method
    strings.forEach(IO::println); //Method reference since println has same return and parameters
}
