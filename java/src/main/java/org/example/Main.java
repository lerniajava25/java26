void main() {
    List<Student> students = new ArrayList<>();

    students.add(new Student("Martin", 49));
    students.add(new Student("Kalle", 19));
    String name = IO.readln("Student name: ");
    String age = IO.readln("Student age: ");
    while (age != null && !tryParseInt(age)) {
        IO.println("Please enter a valid age!");
        age = IO.readln("Student age: ");
    }
    var temp = Integer.parseInt(age);
    IO.println("Student age is > 10: " + greaterThanTen(temp));
    students.add(new Student(name, Integer.parseInt(age)));

    printAllStudents(students);
}

private static void printAllStudents(List<Student> students) {
    //Print all students
    for (Student student : students) {
        System.out.println(student.name() + " " + student.age());
    }
}

record Student(String name, int age) {
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
