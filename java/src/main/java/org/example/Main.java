import org.example.Animal;
import org.example.Cat;
import org.example.Dog;
import org.example.Point;

void main() {
    IO.println("Hello World!");
    Point point = new Point(1.0, 1.0);
    IO.println("Point: " + point.x() + "," + point.y());
    Point point2 = new Point(1.0, 1.0);
    Point point3 = new Point();  //Default constructor, No-args/no params constructor
    Point point4 = new Point(point3); //Copy constructor
    Point point5 = Point.of(1.0, 1.0);  //Factory method

    if (point.equals(point2)) {
        System.out.println("Points are the same");
    }

    List<Animal> animals = new ArrayList<>();
//    animals = Collections.unmodifiableList(animals); //Breaks when using modifying methods

    animals.add(new Dog());
    animals.add(new Cat());
    animals.add(new Cat());

    animals.forEach(animal -> IO.println("Animal sounds like: " + animal.sound()));


}
