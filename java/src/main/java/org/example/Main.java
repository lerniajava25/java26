package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Main {

    void main() {
        List<Student> students = new ArrayList<>();

        students.add(new Student("Martin", 49, LocalDateTime.now()));
        students.add(new Student("Kalle", 19, LocalDateTime.now()));
        String name = IO.readln("Student name: ");
        String age = IO.readln("Student age: ");
        while (age != null && !tryParseInt(age)) {
            IO.println("Please enter a valid age!");
            age = IO.readln("Student age: ");
        }
        var temp = Integer.parseInt(age);
        IO.println("Student age is > 10: " + greaterThanTen(temp));
        students.add(new Student(name, Integer.parseInt(age), LocalDateTime.now()));

        printAllStudents(students);
    }

    private static void printAllStudents(List<Student> students) {
        //Print all students
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Student student : students) {
            System.out.println(student.name() + " " + student.age() + " " + student.createdAt().format(dtf));
        }
    }

    record Student(String name, int age, LocalDateTime createdAt) {
    }

    boolean tryParseInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    //Method overloading
    boolean greaterThanTen(int value) {
        return value > 10;
    }

    boolean greaterThanTen(float value) {
        return value > 10.0f;
    }
}
