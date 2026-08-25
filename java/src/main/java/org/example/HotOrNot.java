package org.example;

import static org.example.TemperatureService.displayTemperature;

public class HotOrNot {
    static void main() {
        Temperature temperature = new Temperature.Celcius(29);
        displayTemperature(temperature);
    }
}

sealed interface Temperature {
    record Celcius(int val) implements Temperature {
    }

    record Fahrenheit(int val) implements Temperature {
    }
}

class TemperatureService {
    private TemperatureService() {
    }

    public static void displayTemperature(Temperature temperature) {
        switch (temperature) {
            case Temperature.Celcius(var t) when t > 30 -> IO.println(t + " ℃ 🔥");
            case Temperature.Celcius(var t) -> IO.println(t + " ℃");
            case Temperature.Fahrenheit(var t) -> IO.println(t + " ℉");
        }
    }
}
