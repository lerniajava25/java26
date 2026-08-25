package org.example;

public class HotOrNot {
    static void main() {
        Temperature temperature = new Fahrenheit();
        displayTemperature(temperature);
    }

    private static void displayTemperature(Temperature temperature) {
        switch (temperature) {
            case Celsius c -> IO.println(c.getTemperature() + " ℃");
            case Fahrenheit f -> IO.println(f.getTemperature() + " ℉");
        }
    }
}

sealed interface Temperature permits Celsius, Fahrenheit {
    int getTemperature();
}

final class Celsius implements Temperature {
    @Override
    public int getTemperature() {
        return 0;
    }
}

final class Fahrenheit implements Temperature {
    @Override
    public int getTemperature() {
        return 32;
    }
}
