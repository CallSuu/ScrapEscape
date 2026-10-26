ALTER TABLE item_batch ADD CONSTRAINT chk_batch_quantity_max CHECK (quantity BETWEEN 1 AND 6);
