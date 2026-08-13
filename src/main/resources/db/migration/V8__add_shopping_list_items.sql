CREATE TABLE shopping_list_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC NOT NULL CHECK (quantity > 0 AND quantity = TRUNC(quantity)),
    purchased BOOLEAN NOT NULL DEFAULT FALSE,
    purchased_at TIMESTAMPTZ,
    CONSTRAINT shopping_purchase_state_check CHECK (
        (purchased = FALSE AND purchased_at IS NULL) OR
        (purchased = TRUE AND purchased_at IS NOT NULL)
    )
);

CREATE INDEX shopping_list_items_user_idx ON shopping_list_items(user_id);
CREATE INDEX shopping_list_items_user_purchased_idx ON shopping_list_items(user_id, purchased);
CREATE UNIQUE INDEX shopping_list_items_active_product_unique
    ON shopping_list_items(user_id, product_id) WHERE purchased = FALSE;
