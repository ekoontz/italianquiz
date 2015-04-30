CREATE SEQUENCE expression_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE expression (
    id integer DEFAULT nextval('expression_id_seq'::regclass) NOT NULL,
    created timestamp without time zone DEFAULT now(),
    language text,
    model text,
    surface text,
    structure jsonb,
    serialized text
);

CREATE INDEX ON expression USING gin ((((structure -> 'synsem') -> 'sem')));

-- maybe needed, maybe not.
CREATE INDEX ON expression USING gin (structure);
-- CREATE INDEX ON expression language;

-- maybe needed, maybe not.
-- CREATE INDEX ON expression USING gin ((((structure -> 'head'::text) -> 'espanol'::text -> 'espanol'::text)));


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
