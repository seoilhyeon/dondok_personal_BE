-- Keep this email in sync with SystemMemberProvider.SYSTEM_MEMBER_EMAIL.
INSERT INTO member (uuid, email, password_hash, nickname, status)
VALUES (
    UNHEX(REPLACE('00000000-0000-0000-0000-000000000001', '-', '')),
    'system@dondok.internal',
    NULL,
    'SYSTEM',
    'ACTIVE'
);
