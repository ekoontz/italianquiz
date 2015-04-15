-- this function does the same thing as editor.clj/(expressions-for-game).
-- TODO: editor.clj/(expressions-for-game) should just be a wrapper for
-- calling this sql function expressions_for_game().

CREATE OR REPLACE FUNCTION expressions_for_game(game_id int) 
        RETURNS TABLE (
    source text, target text) AS 
$body$
BEGIN
RETURN QUERY

    SELECT source.surface AS source,target.surface AS target
      FROM game
INNER JOIN expression AS source
        ON source.language = game.source
       AND source.structure @> ANY(game.source_lex)
       AND source.structure @> ANY(game.source_grammar)
INNER JOIN expression AS target
        ON target.language = game.target
       AND target.structure @> ANY(game.target_lex)
       AND target.structure @> ANY(game.target_grammar)
     WHERE game.id=game_id
       AND (((source.structure->'synsem'->'sem') @> (target.structure->'synsem'->'sem'))
        OR   (source.structure->'synsem'->'sem') @> (target.structure->'synsem'->'sem'));

END;

$body$ LANGUAGE plpgsql;

-- thanks to http://stackoverflow.com/questions/3994556/eliminate-duplicate-array-values-in-postgres
CREATE OR REPLACE FUNCTION uniq (ANYARRAY) RETURNS ANYARRAY
LANGUAGE SQL
AS $body$
  SELECT ARRAY(
    SELECT DISTINCT $1[s.i]
    FROM generate_series(array_lower($1,1), array_upper($1,1)) AS s(i)
    ORDER BY 1
  );
$body$;
