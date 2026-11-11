-- P1 return correctness: optimistic locking and an honest partial-refund state.

ALTER TABLE return_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE orders DROP CONSTRAINT orders_payment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
    CHECK (payment_status IN ('UNPAID', 'INITIATED', 'PAID', 'FAILED', 'EXPIRED', 'REFUNDED', 'PARTIALLY_REFUNDED'));

ALTER TABLE return_requests ADD COLUMN resolution_note TEXT;
