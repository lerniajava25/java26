import org.example.Animal;
import org.example.Cat;
import org.example.Dog;
import org.example.Point;

void main() {
    IO.println("Hello World!");
    Point point = new Point(1.0, 1.0);
    IO.println("Point: " + point.x() + "," + point.y());
    Point point2 = new Point(1.0, 1.0);

    if (point.equals(point2)) {
        System.out.println("Points are the same");
    }

    List<Animal> animals = new ArrayList<>();
    animals.add(new Dog());
    animals.add(new Cat());
    animals.add(new Cat());

    animals.forEach(animal -> IO.println("Animal sounds like: " + animal.sound()));


}
