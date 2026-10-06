CREATE TABLE IF NOT EXISTS game (
    game_id CHAR(36) PRIMARY KEY,
    elapsed_hours INT NOT NULL DEFAULT 0 CHECK (elapsed_hours >= 0),
    stamina INT NOT NULL DEFAULT 10 CHECK (stamina BETWEEN 0 AND 10),
    cleaner_amount INT NOT NULL DEFAULT 1 CHECK (cleaner_amount BETWEEN 0 AND 5),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    explorations INT NOT NULL DEFAULT 0,
    day_explorations INT NOT NULL DEFAULT 0,
    night_explorations INT NOT NULL DEFAULT 0,
    empty_explorations INT NOT NULL DEFAULT 0,
    collected INT NOT NULL DEFAULT 0,
    cleaned INT NOT NULL DEFAULT 0,
    discarded INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS location (
    location_id BIGINT PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS material (
    material_id BIGINT PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    location_id BIGINT NOT NULL,
    FOREIGN KEY (location_id) REFERENCES location(location_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS item_batch (
    batch_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id CHAR(36) NOT NULL,
    material_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    storage_type VARCHAR(16) NOT NULL CHECK (storage_type IN ('INVENTORY','CHEST')),
    acquired_hour INT NOT NULL CHECK (acquired_hour >= 0),
    item_condition VARCHAR(16) NOT NULL CHECK (item_condition IN ('DIRTY','CLEAN','UNUSABLE')),
    FOREIGN KEY (game_id) REFERENCES game(game_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    INDEX idx_batch_game_storage (game_id, storage_type)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS part (
    part_id BIGINT PRIMARY KEY,
    name VARCHAR(40) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS recipe (
    part_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    required_quantity INT NOT NULL CHECK (required_quantity > 0),
    PRIMARY KEY (part_id,material_id),
    FOREIGN KEY (part_id) REFERENCES part(part_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS game_repair (
    game_id CHAR(36) NOT NULL,
    part_id BIGINT NOT NULL,
    repaired BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (game_id,part_id),
    FOREIGN KEY (game_id) REFERENCES game(game_id) ON DELETE CASCADE,
    FOREIGN KEY (part_id) REFERENCES part(part_id)
) ENGINE=InnoDB;
