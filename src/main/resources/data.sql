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
