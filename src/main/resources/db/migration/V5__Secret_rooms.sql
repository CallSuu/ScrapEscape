ALTER TABLE game
    ADD secret_key_amount INT NOT NULL DEFAULT 0 CHECK (secret_key_amount BETWEEN 0 AND 2),
    ADD secret_keys_acquired INT NOT NULL DEFAULT 0 CHECK (secret_keys_acquired>=0),
    ADD secret_keys_used INT NOT NULL DEFAULT 0 CHECK (secret_keys_used>=0);
CREATE TABLE game_secret_room (
    game_id CHAR(36) NOT NULL,
    location_id BIGINT NOT NULL,
    discovered_hour INT NOT NULL CHECK (discovered_hour>=0),
    immediate_available BOOLEAN NOT NULL DEFAULT TRUE,
    opened_hour INT NULL,
    visit_mode VARCHAR(16) NULL CHECK (visit_mode IN ('IMMEDIATE','REVISIT')),
    outcome VARCHAR(24) NULL CHECK (outcome IN ('MATERIALS','CLEANER','MEDICAL_KIT','MATERIAL_LOSS','CONTAMINATION')),
    duration_hours INT NULL,
    stamina_cost INT NULL,
    contamination_increase INT NOT NULL DEFAULT 0,
    storm_exposure_hours INT NOT NULL DEFAULT 0,
    material_id BIGINT NULL,
    material_quantity INT NOT NULL DEFAULT 0,
    materials_collected INT NOT NULL DEFAULT 0,
    materials_lost INT NOT NULL DEFAULT 0,
    cleaner_added INT NOT NULL DEFAULT 0,
    medical_kits_added INT NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id,location_id),
    FOREIGN KEY (game_id) REFERENCES game(game_id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES location(location_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id)
) ENGINE=InnoDB;
