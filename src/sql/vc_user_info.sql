ALTER TABLE vc_user DROP COLUMN fullname;
ALTER TABLE vc_user DROP COLUMN username;
ALTER TABLE vc_user ADD COLUMN family_name TEXT;
ALTER TABLE vc_user ADD COLUMN given_name TEXT;
ALTER TABLE vc_user ADD COLUMN picture TEXT;

