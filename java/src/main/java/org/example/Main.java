import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

void main() throws IOException {
    //Create a list of Shapes
    //Add some Shape objects
    //Define a 2D image
    BufferedImage image = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);

    //For each pixel in 2D image
    //Generate ray from camera
    //For each Shape in list
    //call hit(ray)
    //save color in 2D image for that pixel

    //Save 2D image to file as png or show on screen
    ImageIO.write(image, "PNG", new File("image.png"));

}
