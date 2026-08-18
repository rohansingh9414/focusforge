# FocusForge Economy

## 1. Overview

FocusForge uses a reward economy to connect real-world goals with digital and
real-world rewards.

The economy currently contains three primary balances:

- Credits
- Rupee balance
- Screen-time minutes

Credits are earned by completing goals and spent when redeeming rewards.

The rupee balance represents discretionary money available inside the app's
reward system.

Screen-time minutes represent earned entertainment time that can later be used
by the app restriction system.

---

## 2. Wallet

The Wallet is the central source of balance information.

### Wallet fields

| Field | Description |
|---|---|
| `creditBalance` | Current number of reward credits |
| `rupeeBalance` | Current discretionary rupee balance |
| `screenTimeMinutes` | Available screen-time minutes |
| `lastDailyGrantDate` | Date on which the daily grant was last applied |

---

## 3. Credits

Credits are the primary internal reward currency.

Goals can award credits when completed.

Rewards consume credits when redeemed.

The exact credit amount awarded by a goal is configurable through that goal's
`creditRate`.

---

## 4. Goal Rewards

A goal may define:

- `creditRate`
- `dailyCap`
- `recurring`

When a goal is completed, the system calculates the credits earned from the
completion.

The resulting credit amount is added to `Wallet.creditBalance`.

A goal's `dailyCap` must be respected so that the same goal cannot generate
unlimited credits within a single day.

---

## 5. Rewards

Rewards are configurable and may represent different types of benefits.

A reward may use:

- Manual credit pricing
- Automatic credit pricing

Reward configuration may include:

- Reward name
- Reward unit
- Pricing mode
- Rupee cost
- Credit rate

When automatic pricing is enabled, the system derives the credit price from
the reward's rupee cost and the current exchange configuration.

---

## 6. Rupee-to-Credit Exchange

FocusForge supports exchanging rupee balance for credits.

The exchange is controlled by:

- `creditsPerRupee`
- `exchangeFeePercent`

The exchange process must:

1. Calculate the number of credits obtained.
2. Apply the configured exchange fee.
3. Deduct the required rupee amount.
4. Add the resulting credits.
5. Record the transaction.

The exact exchange formula will be implemented according to the configured
exchange settings.

---

## 7. Daily Grant

FocusForge provides a daily grant consisting of:

- ₹50 rupee balance
- 60 minutes of screen time

The daily grant must be idempotent.

This means that triggering the daily-grant process multiple times on the same
day must not grant the daily reward more than once.

The last successful grant date is tracked using:

`lastDailyGrantDate`

---

## 8. Screen-Time Balance

`screenTimeMinutes` represents the amount of earned screen time available to
the user.

The app restriction system will eventually use this balance to determine
whether a restricted app can be used.

When a screen-time reward is redeemed, the corresponding number of minutes is
added to the screen-time balance.

When restricted-app usage consumes earned time, the balance decreases.

---

## 9. Insufficient Balance

A reward redemption must fail when the user's available credit balance is
less than the required credit cost.

The system must not allow the balance to become negative because of a reward
redemption.

Similarly, an exchange must not deduct more rupee balance than the user has
available.

---

## 10. Accounting Rules

Economy-related operations should be recorded so that balance changes can be
traced.

Important operations include:

- Goal completion
- Reward redemption
- Rupee-to-credit exchange
- Daily grants
- Screen-time consumption

Balance-changing operations should be performed through the appropriate
domain/repository logic rather than directly from the UI.

---

## 11. Source of Truth

`ECONOMY.md` defines the business rules for the reward economy.

Application code must implement these rules rather than independently
inventing alternative economy behavior.

When an economy rule changes, this document should be updated together with
the corresponding implementation.