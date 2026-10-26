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

INSERT IGNORE INTO location (location_id,name,description) VALUES
(1,'고물상','철 조각과 기어를 찾을 수 있는 폐고철 집하장'),
(2,'전자제품 매장','구리 전선과 회로 기판이 남아 있는 매장'),
(3,'마트','플라스틱과 고무 부품을 찾을 수 있는 버려진 마트');
INSERT IGNORE INTO material (material_id,name,location_id) VALUES
(1,'철 조각',1),(2,'기어',1),(3,'구리 전선',2),(4,'회로 기판',2),(5,'플라스틱 부품',3),(6,'고무 부품',3);
INSERT IGNORE INTO part (part_id,name) VALUES
(1,'엔진 시동 장치'),(2,'통신 장치'),(3,'연료 공급 장치'),(4,'날개 고정 장치'),(5,'항법 센서');
INSERT IGNORE INTO recipe (part_id,material_id,required_quantity) VALUES
(1,1,3),(1,3,2),(1,2,2),
(2,4,2),(2,3,2),(2,5,3),
(3,1,2),(3,2,3),(3,6,2),
(4,1,4),(4,5,2),(4,2,2),
(5,4,3),(5,3,2),(5,6,2);
