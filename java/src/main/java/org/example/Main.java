static final String JAVA = "Java";
static final int WORKING_DAYS = 5;

/**
 * This is the main entry point for our application
 *
 * @param args
 */
void main(String args[]) {
    String text = "Hello World!";
    String multiLineText = """
            <html>
            <head>
            
            </head>
            <body>
            </body>
            </html>
            """;
    int value = 10;
    var formatted = String.format("Hello \"%s\" %d times.", "Martin", value);
    IO.println(formatted);
    if (value == 10) {
        IO.println("" + 2 + 3 + text);
    }

//    StringBuilder stringBuilder = new StringBuilder();
//    stringBuilder.append(text);
//    stringBuilder.append(".....");
//    IO.println(stringBuilder.toString());
}
