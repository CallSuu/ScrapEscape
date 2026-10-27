-- Preserve existing stamina; only new games start with the larger reserve.
SET @scrap_stamina_check = (
    SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='game'
      AND tc.CONSTRAINT_TYPE='CHECK' AND LOWER(cc.CHECK_CLAUSE) LIKE '%stamina%'
    LIMIT 1
);
SET @scrap_stamina_drop = IF(@scrap_stamina_check IS NULL,'SELECT 1',
    CONCAT('ALTER TABLE game DROP CHECK `',REPLACE(@scrap_stamina_check,'`','``'),'`'));
PREPARE scrap_stamina_statement FROM @scrap_stamina_drop;
EXECUTE scrap_stamina_statement;
DEALLOCATE PREPARE scrap_stamina_statement;
ALTER TABLE game MODIFY stamina INT NOT NULL DEFAULT 15;
ALTER TABLE game ADD CONSTRAINT chk_game_stamina_capacity CHECK (stamina BETWEEN 0 AND 15);
