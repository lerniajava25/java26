-- Skapa databas
DROP DATABASE IF EXISTS demo_shop;
CREATE DATABASE demo_shop;
USE demo_shop;

-- Kunder
CREATE TABLE customers
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(50)  NOT NULL,
    last_name  VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Produkter
CREATE TABLE products
(
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(100)   NOT NULL,
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT            NOT NULL
);

-- Ordrar
CREATE TABLE orders
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT    NOT NULL,
    order_date  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers (id),

    CONSTRAINT fk_order_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
);

-- Testdata kunder
INSERT INTO customers (first_name, last_name, email)
VALUES ('Anna', 'Andersson', 'anna6@example.com'),
       ('Erik', 'Eriksson', 'erik6@example.com'),
       ('Maria', 'Nilsson', 'maria6@example.com');

-- Testdata produkter
INSERT INTO products (name, price, stock_quantity)
VALUES ('Laptop', 9999.00, 10),
       ('Mus', 249.00, 50),
       ('Tangentbord', 799.00, 20),
       ('Skärm', 2999.00, 15);

-- Testdata ordrar
INSERT INTO orders (customer_id, product_id, quantity)
VALUES (1, 1, 1),
       (1, 2, 2),
       (2, 3, 1),
       (3, 4, 2);

# INSERT INTO customers(first_name, last_name, email)
# SELECT
#     CONCAT('User', n),
#     CONCAT('Lastname', n),
#     CONCAT('user', n, '@example.com')
# FROM (
#          SELECT
#              ones.i +
#              tens.i * 10 +
#              hundreds.i * 100 +
#              thousands.i * 1000 + 1 AS n
#          FROM
#              (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
#               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
#                  CROSS JOIN
#              (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
#               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
#                  CROSS JOIN
#              (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
#               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
#                  CROSS JOIN
#              (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
#               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
#      ) numbers
# WHERE n <= 10000;
