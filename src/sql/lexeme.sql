CREATE SEQUENCE lexeme_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE lexeme (
    id integer DEFAULT nextval('lexeme_id_seq'::regclass) NOT NULL,
    created timestamp without time zone DEFAULT now(),
    language text,
    canonical text,
    structure jsonb,
    serialized text
);

CREATE INDEX ON lexeme USING gin ((((structure -> 'synsem') -> 'sem')));

-- maybe needed, maybe not.
CREATE INDEX ON lexeme USING gin (structure);
