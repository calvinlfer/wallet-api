## Wallet API

An implementation of a Wallet API making using of event sourcing and distributed by Akka Cluster Sharding.
Deposits and Withdrawals follow a fee structure (the more money in your account, the cheaper it becomes to transact)

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