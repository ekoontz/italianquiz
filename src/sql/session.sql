ALTER TABLE question DROP COLUMN user_id;

CREATE SEQUENCE session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

DROP TABLE session;

CREATE TABLE session (
  id uuid,
  access_token text,
  user_id integer REFERENCES vc_user,
  created timestamp without time zone DEFAULT now()
);

ALTER TABLE vc_user DROP COLUMN session;
ALTER TABLE vc_user DROP COLUMN access_token;

