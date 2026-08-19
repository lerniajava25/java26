// We want to read information about students from stdinput
// Each student should have a name and an age
// We need to store multiple students

void main() {
    Student[] students = new Student[3];

    students[0] = new Student("Martin", 49);
    students[1] = new Student("Kalle", 19);
    String name = IO.readln("Student name: ");
    String age = IO.readln("Student age: ");
    while (!tryParseInt(age)) {
        IO.println("Please enter a valid age!");
        age = IO.readln("Student age: ");
    }
    students[2] = new Student(name, Integer.parseInt(age));

    //Print all students
    for (int i = 0; i < students.length; i++) {
        System.out.println(students[i].name() + " " + students[i].age());
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
