DROP TABLE expression_import;
CREATE TABLE expression_import (
    language text,
    model text,
    surface text,
    structure jsonb,
    serialized text
);

