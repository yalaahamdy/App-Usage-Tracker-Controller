# Contributing to Muraqib

Thank you for your interest in contributing to **Muraqib (App Usage Tracker & Controller)**. This document provides guidelines and instructions for contributing to the repository.

---

## Code of Conduct
We are committed to providing a friendly, safe, and welcoming environment for everyone, regardless of background or level of experience. Please treat all contributors and users with respect and professionalism.

---

## Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (Eclipse Temurin recommended)
- Android SDK 34+
- Git

### Getting Started
1. Fork the repository on GitHub.
2. Clone your fork locally:
   ```bash
   git clone https://github.com/yalaahamdy/App-Usage-Tracker-Controller.git
   cd App-Usage-Tracker-Controller
   ```
3. Open the project in Android Studio.
4. Allow Gradle to synchronize dependencies.
5. Run the unit tests to verify your setup:
   ```bash
   ./gradlew test
   ```

---

## Coding Guidelines

- **Architecture:** Follow Clean Architecture and MVVM patterns. UI must remain decoupled from data sources via Repositories and ViewModels.
- **UI Framework:** Use Jetpack Compose and Material 3 design tokens.
- **Localization & Directionality:** Support RTL (Right-to-Left) layouts natively. Arabic strings reside in `res/values/strings.xml`.
- **UI Design Standard:** Keep interfaces clean, typography-focused, and professional. **Do not use emojis** in UI components or system strings.
- **Code Style:** Adhere to standard official Kotlin coding conventions (Kotlin Style Guide).
- **Concurrency:** Use Kotlin Coroutines and StateFlow/SharedFlow.

---

## Commit Message Convention

We follow conventional commits format:
- `feat:` A new feature
- `fix:` A bug fix
- `docs:` Documentation changes only
- `refactor:` Code changes that neither fix a bug nor add a feature
- `test:` Adding missing tests or correcting existing tests
- `chore:` Maintenance tasks, dependency updates, build configurations

Example:
```text
feat(restrictions): add isolated per-app bypass mechanism
```

---

## Submitting Pull Requests

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Commit your changes with clear, descriptive commit messages.
3. Ensure all tests pass:
   ```bash
   ./gradlew test
   ```
4. Push your branch to your fork:
   ```bash
   git push origin feat/your-feature-name
   ```
5. Open a Pull Request against the `main` branch with the provided PR template filled out completely.
