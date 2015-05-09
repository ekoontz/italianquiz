
-- TODO: add all of the following inside a dedup() postgresql function.
DROP TABLE expression_distinct;
CREATE TABLE expression_distinct (
    language text,
    model text,
    surface text,
    structure jsonb,
    serialized text
);

INSERT INTO expression_distinct (language,model,surface,structure,serialized) 
 SELECT DISTINCT language,model,surface,structure,serialized 
            FROM expression;

TRUNCATE expression;

INSERT INTO expression (language,model,surface,structure,serialized) 
     SELECT language,model,surface,structure,serialized 
       FROM expression_distinct;
