ALTER TABLE game
    ADD rules_version INT NOT NULL DEFAULT 1,
    ADD medical_kit_amount INT NOT NULL DEFAULT 0 CHECK (medical_kit_amount BETWEEN 0 AND 2),
    ADD contamination INT NOT NULL DEFAULT 0 CHECK (contamination BETWEEN 0 AND 100),
    ADD max_contamination INT NOT NULL DEFAULT 0,
    ADD diseased BOOLEAN NOT NULL DEFAULT FALSE,
    ADD disease_started_hour INT NULL,
    ADD consecutive_explorations INT NOT NULL DEFAULT 0,
    ADD disease_count INT NOT NULL DEFAULT 0,
    ADD treatment_count INT NOT NULL DEFAULT 0,
    ADD medical_kits_used INT NOT NULL DEFAULT 0,
    ADD storm_exposure_hours INT NOT NULL DEFAULT 0,
    ADD failure_reason VARCHAR(32) NULL;
ALTER TABLE game_repair ADD part_name VARCHAR(40) NULL;
UPDATE game_repair gr JOIN part p ON p.part_id=gr.part_id SET gr.part_name=p.name;
CREATE TABLE game_recipe (
    game_id CHAR(36) NOT NULL,
    part_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    required_quantity INT NOT NULL,
    PRIMARY KEY (game_id,part_id,material_id),
    FOREIGN KEY (game_id,part_id) REFERENCES game_repair(game_id,part_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id)
) ENGINE=InnoDB;
INSERT INTO game_recipe SELECT gr.game_id,r.part_id,r.material_id,r.required_quantity
FROM game_repair gr JOIN recipe r ON r.part_id=gr.part_id;
CREATE TABLE location_material (
    location_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    PRIMARY KEY (location_id,material_id),
    FOREIGN KEY (location_id) REFERENCES location(location_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id)
) ENGINE=InnoDB;
CREATE TABLE radiation_storm (
    storm_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id CHAR(36) NOT NULL,
    start_hour INT NOT NULL,
    end_hour INT NOT NULL,
    CHECK (start_hour>=0 AND end_hour>start_hour),
    FOREIGN KEY (game_id) REFERENCES game(game_id) ON DELETE CASCADE,
    INDEX idx_storm_game (game_id,start_hour)
) ENGINE=InnoDB;
INSERT INTO location VALUES
(4,'정비소','엔진과 기체를 수리할 내열 합금과 기계 재료'),
(5,'통신 시설','신호 모듈과 전자 부품이 남아 있는 시설'),
(6,'병원','의료 키트 1개 확정 및 필터와 플라스틱 부품');
UPDATE location SET description='철 조각, 기어, 고무 부품을 찾는 집하장' WHERE location_id=1;
UPDATE location SET description='구리 전선, 회로 기판, 배터리 셀을 찾는 매장' WHERE location_id=2;
UPDATE location SET description='플라스틱, 고무, 필터가 남아 있는 마트' WHERE location_id=3;
-- 기존 location_id는 초기 버전의 재료 정보 보존용이다. 새 탐색은 연결 테이블을 사용한다.
INSERT INTO material VALUES
(7,'배터리 셀',2),(8,'필터',3),(9,'내열 합금',4),(10,'신호 모듈',5);
INSERT INTO location_material VALUES
(1,1),(1,2),(1,6),(2,3),(2,4),(2,7),(3,5),(3,6),(3,8),
(4,1),(4,2),(4,9),(5,3),(5,4),(5,10),(6,5),(6,8);
UPDATE part SET name='동력 전달 장치' WHERE part_id=2;
UPDATE part SET name='냉각 장치' WHERE part_id=4;
UPDATE part SET name='비행 제어 장치' WHERE part_id=5;
INSERT INTO part VALUES (6,'통신·항법 장치'),(7,'기체 보강 장치');
DELETE FROM recipe;
INSERT INTO recipe VALUES
(1,1,3),(1,2,2),(1,3,2),(1,7,1),
(2,1,3),(2,2,3),(2,6,2),
(3,1,2),(3,6,3),(3,5,2),(3,8,1),
(4,3,2),(4,6,2),(4,5,2),(4,9,2),
(5,4,3),(5,3,2),(5,5,2),(5,10,1),
(6,4,2),(6,3,3),(6,7,2),(6,10,1),
(7,1,4),(7,9,3),(7,6,2);
