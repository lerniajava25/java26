package org.example;

import java.sql.*;

public class App {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/demo_shop?user=root&password=root";

        String name = "Anna' OR '1'='1";
        //String name = "Anna";

        try (Connection conn = DriverManager.getConnection(url)) {

            PreparedStatement stmt = conn.prepareStatement("select * from customers where first_name = ?");
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    row.append(meta.getColumnLabel(i))
                            .append(": ")
                            .append(rs.getString(i))
                            .append(i < columnCount ? " | " : "");
                }
                System.out.println(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
