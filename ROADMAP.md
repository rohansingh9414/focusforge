# Build Roadmap

Ground rule: **one phase = one working, committed state.** Never let Antigravity touch
phase N+1 before phase N builds, runs, and is pushed. If it can't build, it doesn't get
a commit — no "WIP fix later" garbage in main.

Model key: 🔵 Sonnet 4.6 Thinking (default) · 🟢 Gemini 3.6 Flash (fast/repetitive) · 🟣 Opus 4.6 Thinking (hard bugs only)

---

## Phase 0 — Repo & Skeleton (Day 1, before any real code)
- [ ] Create GitHub repo, `main` branch only for now (add `develop`/feature branches once solo-dev pace picks up)
- [ ] `.gitignore` (Android Studio template), README stub, LICENSE
- [ ] Commit `ECONOMY.md` and `ARCHITECTURE.md` (design docs) — **first two commits, before Kotlin exists**
- [ ] Connect repo inside Antigravity IDE
- **Commit:** `chore: init repo + design docs`

## Phase 1 — Project Foundation 🔵
- [ ] New Android project, Kotlin + Jetpack Compose, MVVM
- [ ] Package structure: `data/`, `domain/`, `ui/`, `services/` (as in ARCHITECTURE.md)
- [ ] Navigation graph with placeholder screens: Home, Goals, Rewards, Restrictions, Stats, Settings
- [ ] Theme (colors/typography — don't waste time here, just get it compiling)
- [ ] Room DB scaffold with empty `AppDatabase` (no entities yet)
- **Commit:** `feat: project scaffold, navigation, empty database`
- ✅ Checkpoint: app builds and runs on emulator, navigates between blank screens

## Phase 2 — Economy Core 🔵
- [ ] `Wallet` entity + DAO (creditBalance, rupeeBalance, screenTimeMinutes, lastDailyGrantDate)
- [ ] `ExchangeConfig` entity + DataStore-backed settings (creditsPerRupee, exchangeFeePercent)
- [ ] `WalletRepository` with basic get/update
- [ ] Home screen: show live Wallet balances (just wiring, no logic yet)
- **Commit:** `feat: wallet + exchange config schema`
- ✅ Checkpoint: Home screen shows real (currently zero) balances from Room

## Phase 3 — Goals Module 🔵 (Gemini 🟢 for the CRUD screens once schema is settled)
- [ ] `GoalTemplate` + `GoalLog` entities, DAOs
- [ ] `GoalRepository`, `GoalManager` (domain layer: `completeGoal(goal, amount) → creditsEarned`)
- [ ] Goals screen: create/edit/delete goal templates (title, unit, creditRate, dailyCap, recurring)
- [ ] "Complete Goal" flow → writes GoalLog, updates Wallet.creditBalance, respects dailyCap
- **Commit:** `feat: goal templates + completion → credit earning`
- ✅ Checkpoint: can create a custom goal, complete it, watch credit balance go up correctly

## Phase 4 — Rewards Module 🔵 (Gemini 🟢 for CRUD screens)
- [ ] `RewardTemplate` + `RedemptionLog` entities, DAOs
- [ ] `RewardRepository`, `RewardManager` (`redeem(reward, units) → creditsSpent`, blocks if insufficient balance)
- [ ] Rewards screen: create/edit rewards (name, unit, AUTO/MANUAL pricing, rupeeCost or creditRate)
- [ ] Auto-pricing calculator: rupeeCost × creditsPerRupee → creditRate, live preview while editing
- [ ] Redeem flow → deducts credits, logs redemption, increments screenTimeMinutes if reward is screen-time type
- **Commit:** `feat: reward templates + redemption → credit spending`
- ✅ Checkpoint: seed the default rewards table from ECONOMY.md §7, redeem ice cream, credits drop correctly

## Phase 5 — Daily Automation 🔵
- [ ] WorkManager periodic job (midnight trigger, idempotent via `lastDailyGrantDate`)
- [ ] Grants: +₹50 rupee, +60 min screen time (respect rollover cap if you enabled it)
- [ ] Reset dailyCap counters on goals
- **Commit:** `feat: daily grant automation`
- ✅ Checkpoint: force-run the worker, confirm balances update once and only once per day

## Phase 6 — Barter Screen 🔵
- [ ] Exchange UI: input rupees → preview credits gained (using exchangeFeePercent formula)
- [ ] Confirm → deduct rupeeBalance, add creditBalance, log transaction
- [ ] Settings toggle for exchangeFeePercent (0–100%)
- **Commit:** `feat: rupee-to-credit barter exchange`
- ✅ Checkpoint: the "slept all day" scenario actually works end to end

## Phase 7 — App Restriction / Blocking 🟣 (this is the hard one, start with Opus if Sonnet stalls)
- [ ] App selection screen (list installed apps via `PackageManager`, choose which to restrict)
- [ ] `UsageStatsManager` or `AccessibilityService` foreground-app detection — **decide which approach first, don't let the agent pick blindly, this affects Play Store policy later**
- [ ] `AppBlockingService`: on restricted app foreground, check `screenTimeMinutes > 0`; if 0, show blocking overlay/redirect; if >0, allow + decrement per minute
- [ ] Schedule support (only restrict during certain hours) — optional for V1, can stub
- **Commit:** `feat: app blocking service wired to screen time balance`
- ✅ Checkpoint: block Instagram, confirm it opens fine with screen-time balance and gets blocked at 0

## Phase 8 — Gamification (optional, but you liked it) 🟢
- [ ] XP field on Wallet or separate `XpLog`, `creditsEarned × xpMultiplier` per goal
- [ ] Level thresholds (100/250/500/1000...) — config-driven, not hardcoded
- [ ] Streaks: consecutive days completing a given goal, small creditRate multiplier bonus
- **Commit:** `feat: XP, levels, streaks`

## Phase 9 — Statistics Dashboard 🟢
- [ ] Charts: credits earned/spent over time, goal completion history, screen time usage
- [ ] Simple bar/line charts (e.g. `Vico` or `MPAndroidChart`)
- **Commit:** `feat: statistics dashboard`

## Phase 10 — Polish 🟢
- [ ] Notifications (goal reminders, daily grant confirmation, low screen-time warning)
- [ ] Dark mode
- [ ] Backup/export (JSON export of Room DB — cheap insurance against Android eating your data)
- [ ] Widget (optional): balance + today's goals on home screen
- **Commit(s):** one per feature, don't batch these

## Phase 11 — Release 🔵
- [ ] README with screenshots, architecture diagram, setup instructions
- [ ] Versioning (`v1.0.0` tag), signed release APK
- [ ] GitHub Releases page with changelog
- **Commit:** `chore: v1.0.0 release`

---

## Dependency Chain (don't skip ahead)

```
Phase 0 (repo)
   ↓
Phase 1 (skeleton)
   ↓
Phase 2 (wallet) ──────────┐
   ↓                       │
Phase 3 (goals) ──┐        │
   ↓               ├──> Phase 6 (barter needs wallet + exchange)
Phase 4 (rewards)─┘        │
   ↓                       │
Phase 5 (daily automation)─┘
   ↓
Phase 7 (blocking) — needs screenTimeMinutes from Phase 4/5 to be real
   ↓
Phase 8, 9, 10 — any order, all optional polish
   ↓
Phase 11 (release)
```

## Rules for every phase
1. New feature branch per phase (`feat/goals-module`, etc.) if you want PR history — or commit straight to `main` if you're not precious about it, your call.
2. Agent must run/build before you accept the commit. No "trust me it compiles."
3. Update `ECONOMY.md`/`ARCHITECTURE.md` if a phase changes the schema — keep docs and code in sync, don't let them drift.
