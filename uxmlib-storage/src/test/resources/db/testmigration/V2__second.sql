CREATE TABLE second_table (id INTEGER PRIMARY KEY, first_id INTEGER NOT NULL);
CREATE INDEX second_table_first_id ON second_table (first_id);
