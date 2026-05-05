-- Dev seed data for AccountService (H2). UUIDs are encoded as 32-char hex literals
-- because Hibernate maps UUID @Id/@JoinColumn to BINARY(16) on H2; the dashed UUID
-- string form is rejected by H2 for binary columns.
INSERT INTO accounts (id, name, creation_date, person_id) VALUES
('aaaa1111aaaaaaaaaaaaaaaaaaaaaaaa', 'John Checking', '2024-01-15', '11111111111111111111111111111111'),
('aaaa2222aaaaaaaaaaaaaaaaaaaaaaaa', 'John Savings', '2024-02-01', '11111111111111111111111111111111'),
('bbbb1111bbbbbbbbbbbbbbbbbbbbbbbb', 'Jane Checking', '2024-01-20', '22222222222222222222222222222222'),
('cccc1111cccccccccccccccccccccccc', 'Bob Checking', '2024-03-10', '33333333333333333333333333333333');

INSERT INTO bills (id, currency, amount, is_overdraft, account_id) VALUES
('dddd1111dddddddddddddddddddddddd', 'USD', 1500.00, true, 'aaaa1111aaaaaaaaaaaaaaaaaaaaaaaa'),
('dddd2222dddddddddddddddddddddddd', 'USD', 5000.00, false, 'aaaa2222aaaaaaaaaaaaaaaaaaaaaaaa'),
('eeee1111eeeeeeeeeeeeeeeeeeeeeeee', 'EUR', 2500.00, true, 'bbbb1111bbbbbbbbbbbbbbbbbbbbbbbb'),
('ffff1111ffffffffffffffffffffffff', 'USD', 800.00, false, 'cccc1111cccccccccccccccccccccccc');
