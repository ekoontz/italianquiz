INSERT INTO expression (language,model,surface,structure,serialized)
     SELECT language,model,surface,structure,serialized
       FROM expression_import;
