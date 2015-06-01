DROP TABLE question CASCADE;
DROP SEQUENCE question_id_seq;

CREATE SEQUENCE question_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE question (
    id integer DEFAULT nextval('question_id_seq'::regclass) NOT NULL,
    issued timestamp without time zone DEFAULT now(),
    game integer REFERENCES game (id),
    structure jsonb,
    time_to_correct_response integer
);

CREATE INDEX ON question USING gin ((((structure -> 'synsem') -> 'sem')));
