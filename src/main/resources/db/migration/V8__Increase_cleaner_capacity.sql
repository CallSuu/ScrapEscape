-- Keep stored cleaner amounts and the initial one-use supply unchanged.
SET @scrap_cleaner_check = (
    SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='game'
      AND tc.CONSTRAINT_TYPE='CHECK' AND LOWER(cc.CHECK_CLAUSE) LIKE '%cleaner_amount%'
    LIMIT 1
);
SET @scrap_cleaner_drop = IF(@scrap_cleaner_check IS NULL,'SELECT 1',
    CONCAT('ALTER TABLE game DROP CHECK `',REPLACE(@scrap_cleaner_check,'`','``'),'`'));
PREPARE scrap_cleaner_statement FROM @scrap_cleaner_drop;
EXECUTE scrap_cleaner_statement;
DEALLOCATE PREPARE scrap_cleaner_statement;
ALTER TABLE game ADD CONSTRAINT chk_game_cleaner_capacity CHECK (cleaner_amount BETWEEN 0 AND 8);
