# FocusForge Architecture

## 1. Overview

FocusForge is an Android productivity application that connects real-world
goals with configurable rewards and screen-time management.

The application is designed using:

- Kotlin
- Jetpack Compose
- MVVM
- Navigation
- Room
- DataStore
- Android services required for usage monitoring and app restriction

The architecture is intended to keep UI code, business logic, data access, and
Android system services separated.

---

## 2. High-Level Architecture

The application follows this general flow:

UI
↓
ViewModel
↓
Domain
↓
Repository
↓
Data sources

Android-specific services are kept in the services layer and are exposed to
the rest of the application through appropriate abstractions.

---

## 3. Package Structure

The main application structure is:

```text
app/
│
├── data/
│   ├── database/
│   ├── entities/
│   ├── dao/
│   └── repository/
│
├── domain/
│   ├── managers/
│   ├── models/
│   └── usecases/
│
├── ui/
│   ├── home/
│   ├── goals/
│   ├── rewards/
│   ├── restrictions/
│   ├── stats/
│   ├── settings/
│   └── navigation/
│
└── services/
    ├── usage/
    ├── blocking/
    └── notifications/