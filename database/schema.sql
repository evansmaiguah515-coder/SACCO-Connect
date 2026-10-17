-- SACCO Connect database schema (SQLite). Room creates these tables automatically from the
-- entity classes in app/src/main/java/com/sacco/app/data/Entities.kt. This file documents them
-- and can be used to inspect the data (e.g. with DB Browser for SQLite on a pulled sacco.db).
-- Members and administrators are rows in `users`, separated by the `role` column.
PRAGMA foreign_keys = ON;

CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  memberNumber TEXT NOT NULL, fullName TEXT NOT NULL, nationalId TEXT NOT NULL,
  phone TEXT NOT NULL, email TEXT NOT NULL, gender TEXT NOT NULL, dateOfBirth TEXT NOT NULL,
  address TEXT NOT NULL, passwordHash TEXT NOT NULL, salt TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER','ADMIN')),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','REJECTED')),
  photoPath TEXT, createdAt INTEGER NOT NULL
);
CREATE UNIQUE INDEX index_users_email ON users(email);
CREATE UNIQUE INDEX index_users_memberNumber ON users(memberNumber);

CREATE TABLE products (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL,
  interestRate REAL NOT NULL, minAmount REAL NOT NULL, maxAmount REAL NOT NULL,
  maxMonths INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE savings (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL CHECK (type IN ('DEPOSIT','WITHDRAWAL')),
  amount REAL NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL
);
CREATE INDEX index_savings_userId ON savings(userId);

CREATE TABLE loans (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, loanNumber TEXT NOT NULL,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  productId INTEGER REFERENCES products(id) ON DELETE SET NULL,
  loanType TEXT NOT NULL, principal REAL NOT NULL, interestRate REAL NOT NULL, interest REAL NOT NULL,
  totalRepayable REAL NOT NULL, installment REAL NOT NULL, installments INTEGER NOT NULL,
  frequency TEXT NOT NULL CHECK (frequency IN ('Weekly','Monthly')), durationMonths INTEGER NOT NULL,
  purpose TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACTIVE','REJECTED','FULLY_PAID')),
  amountRepaid REAL NOT NULL DEFAULT 0, adminNote TEXT NOT NULL DEFAULT '',
  appliedAt INTEGER NOT NULL, issuedAt INTEGER
);
CREATE INDEX index_loans_userId ON loans(userId);
CREATE INDEX index_loans_productId ON loans(productId);

CREATE TABLE loan_repayments (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  loanId INTEGER NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount REAL NOT NULL, reference TEXT NOT NULL, paidAt INTEGER NOT NULL
);
CREATE INDEX index_loan_repayments_loanId ON loan_repayments(loanId);
CREATE INDEX index_loan_repayments_userId ON loan_repayments(userId);

CREATE TABLE transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, reference TEXT NOT NULL,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL CHECK (type IN ('DEPOSIT','WITHDRAWAL','LOAN_DISBURSEMENT','LOAN_REPAYMENT','SHARE_CONTRIBUTION','TRANSFER')),
  amount REAL NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'SUCCESS', createdAt INTEGER NOT NULL
);
CREATE INDEX index_transactions_userId ON transactions(userId);

CREATE TABLE shares (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  shares INTEGER NOT NULL, unitValue REAL NOT NULL, amount REAL NOT NULL, createdAt INTEGER NOT NULL
);
CREATE INDEX index_shares_userId ON shares(userId);

CREATE TABLE notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL, message TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('ACCOUNT','TRANSACTION','LOAN','ANNOUNCEMENT','REMINDER')),
  isRead INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL
);
CREATE INDEX index_notifications_userId ON notifications(userId);

-- Useful report queries -----------------------------------------------------
-- Savings balance per member
-- SELECT u.fullName, SUM(CASE WHEN s.type='DEPOSIT' THEN s.amount ELSE -s.amount END) AS balance
--   FROM users u JOIN savings s ON s.userId=u.id GROUP BY u.id;
-- Outstanding loans
-- SELECT loanNumber, totalRepayable-amountRepaid AS outstanding FROM loans WHERE status='ACTIVE';
-- Demo users are inserted by the app on first launch (SaccoRepository.seedIfEmpty); passwords are stored as salted PBKDF2 hashes.
