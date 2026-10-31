-- Reduce each recipe to seven materials; keep repaired/ended/legacy game snapshots.
UPDATE recipe SET required_quantity=CASE
 WHEN part_id=1 AND material_id=1 THEN 2
 WHEN part_id=2 AND material_id=1 THEN 2
 WHEN part_id=3 AND material_id=1 THEN 1
 WHEN part_id=4 AND material_id=3 THEN 1
 WHEN part_id=5 AND material_id=4 THEN 2
 WHEN part_id=6 AND material_id=3 THEN 2
 WHEN part_id=7 AND material_id=1 THEN 2
 ELSE required_quantity END;
UPDATE game_recipe gr
JOIN game g ON g.game_id=gr.game_id
JOIN game_repair gp ON gp.game_id=gr.game_id AND gp.part_id=gr.part_id
JOIN recipe r ON r.part_id=gr.part_id AND r.material_id=gr.material_id
SET gr.required_quantity=r.required_quantity
WHERE g.rules_version=2 AND g.status='RUNNING' AND gp.repaired=FALSE;
