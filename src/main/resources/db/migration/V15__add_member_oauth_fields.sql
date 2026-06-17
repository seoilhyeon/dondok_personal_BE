ALTER TABLE member
  ADD COLUMN oauth_provider VARCHAR(20) NULL,
  ADD COLUMN oauth_provider_id VARCHAR(100) NULL,
  ADD CONSTRAINT uk_member_oauth_provider_id UNIQUE (oauth_provider, oauth_provider_id),
  ADD CONSTRAINT chk_member_oauth_fields_pair
    CHECK (
      (oauth_provider IS NULL AND oauth_provider_id IS NULL)
      OR (oauth_provider IS NOT NULL AND oauth_provider_id IS NOT NULL)
    );
