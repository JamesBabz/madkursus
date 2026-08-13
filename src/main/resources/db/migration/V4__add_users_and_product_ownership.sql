CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT users_username_unique UNIQUE (username)
);

-- Existing development products are disposable and cannot be assigned safely
-- without inventing a user. Inventory rows must be removed first due to its FK.
DELETE FROM inventory_items;
DELETE FROM products;

ALTER TABLE products
    ADD COLUMN user_id UUID NOT NULL,
    ADD CONSTRAINT products_user_fk FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX products_user_id_idx ON products (user_id);
