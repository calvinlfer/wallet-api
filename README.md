## Wallet API

An implementation of a Wallet API making using of event sourcing and distributed by Akka Cluster Sharding.
Deposits and Withdrawals follow a fee structure (the more money in your account, the cheaper it becomes to transact)

### Running the application
1. Make sure to spin up Postgres via `docker-compose up -d`
2. Run the application using IntelliJ, Visual Studio Code + Metals or just `sbt run`

### Operations

#### Create 
`POST /wallets`
```json
{
  "id": "abc"
}
```

#### Deposit
`POST /wallets/<id>/deposit`
```json
{
  "amount": 2000
}
```

#### Withdraw
`POST /wallets/<id>/withdraw`
```json
{
  "amount": 2000
}
```

#### Query Deposit Fee
`POST /wallets/<id>/depositFee`
```json
{
  "amount": 2000
}
```

#### Query Withdraw Fee
`POST /wallets/<id>/withdrawFee`
```json
{
  "amount": 2000
}
```

#### Immediate history
`GET /wallets/abc/history/immediate`

#### All history
`GET /wallets/abc/history/all`