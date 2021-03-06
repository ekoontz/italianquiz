ALTER TABLE expression ADD PRIMARY KEY (id);

DROP TABLE question CASCADE;
DROP SEQUENCE question_id_seq;

CREATE SEQUENCE question_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE question (
    game integer REFERENCES game (id),
    session_id UUID, -- a email address if it exists; otherwise a cookie or something similar.
    id integer DEFAULT nextval('question_id_seq'::regclass) NOT NULL,
    issued timestamp without time zone DEFAULT now(),
    source integer REFERENCES expression,
    answer text, -- the user's answer as a string.
    time_to_correct_response integer
);

