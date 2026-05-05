-- Dev seed data for PersonService (H2). UUIDs are encoded as 32-char hex literals
-- because Hibernate maps UUID @Id to BINARY(16) on H2; the dashed UUID string
-- form is rejected by H2 for binary columns.
INSERT INTO people (id, first_name, last_name, birthdate, phone_number, email) VALUES
('11111111111111111111111111111111', 'John', 'Doe', '1990-05-15', '+1 555 123 45 67', 'john.doe@example.com'),
('22222222222222222222222222222222', 'Jane', 'Smith', '1985-08-22', '+1 555 987 65 43', 'jane.smith@example.com'),
('33333333333333333333333333333333', 'Bob', 'Johnson', '1978-12-01', '+1 555 456 78 90', 'bob.johnson@example.com');
