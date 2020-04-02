-- auto-generated definition
create table processor
(
    sequence_number  bigserial not null,
    amount           bigint    not null,
    calculation_name varchar   not null constraint fee_pk primary key
);
