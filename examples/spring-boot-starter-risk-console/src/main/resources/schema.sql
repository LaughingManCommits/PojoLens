create table if not exists merchants (
    id varchar(32) primary key,
    name varchar(128) not null,
    segment varchar(32) not null,
    region varchar(16) not null,
    risk_tier varchar(16) not null,
    owner_team varchar(64) not null,
    onboarded_at timestamp not null,
    active boolean not null,
    index idx_merchants_region (region)
);

create table if not exists customer_accounts (
    id varchar(32) primary key,
    email_hash varchar(96) not null,
    country varchar(16) not null,
    signup_at timestamp not null,
    kyc_status varchar(32) not null,
    lifetime_value double not null,
    chargeback_count int not null
);

create table if not exists payment_transactions (
    id varchar(40) primary key,
    merchant_id varchar(32) not null,
    customer_id varchar(32) not null,
    created_at timestamp not null,
    amount double not null,
    currency varchar(8) not null,
    payment_method varchar(32) not null,
    card_brand varchar(32) not null,
    status varchar(24) not null,
    risk_score int not null,
    risk_band varchar(24) not null,
    failure_code varchar(48),
    foreign key (merchant_id) references merchants(id),
    foreign key (customer_id) references customer_accounts(id),
    index idx_transactions_created_at (created_at),
    index idx_transactions_status (status),
    index idx_transactions_risk_band (risk_band)
);

create table if not exists risk_reviews (
    id varchar(40) primary key,
    transaction_id varchar(40) not null,
    queue_name varchar(32) not null,
    priority varchar(24) not null,
    reason_code varchar(48) not null,
    analyst varchar(64) not null,
    opened_at timestamp not null,
    resolved_at timestamp null,
    review_status varchar(24) not null,
    resolution varchar(48),
    foreign key (transaction_id) references payment_transactions(id),
    index idx_reviews_transaction (transaction_id)
);

create table if not exists chargeback_cases (
    id varchar(40) primary key,
    transaction_id varchar(40) not null,
    merchant_id varchar(32) not null,
    reason_code varchar(48) not null,
    stage varchar(24) not null,
    amount double not null,
    opened_at timestamp not null,
    due_at timestamp not null,
    outcome varchar(24),
    foreign key (transaction_id) references payment_transactions(id),
    foreign key (merchant_id) references merchants(id),
    index idx_chargebacks_transaction (transaction_id)
);

create table if not exists transaction_events (
    id varchar(40) primary key,
    transaction_id varchar(40) not null,
    event_type varchar(48) not null,
    created_at timestamp not null,
    actor varchar(64) not null,
    detail_text varchar(255) not null,
    foreign key (transaction_id) references payment_transactions(id),
    index idx_events_transaction (transaction_id)
);
