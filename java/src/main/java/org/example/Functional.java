package org.example;

public class Functional {

    public static int counter = 0;

    //This is a pure function
    public static int add(int a, int b) {
        return a + b;
    }

    //Not pure, side effects and different return values each call
    public static int count() {
        counter++;
        return counter;
    }

    //No side effect but different return values each call.
    public static int dice(int sides) {
        return (int) (Math.random() * sides + 1);
    }


}
