# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | Yes                |
| < 1.0   | No                 |

---

## Security Architecture & Privacy by Design

Muraqib is engineered with security and user privacy as foundational principles:

- **100% Offline by Design:** The application requests zero internet permissions (`android.permission.INTERNET` is not included in the manifest). All usage tracking, network data analytics, restrictions, and evaluations are computed purely on-device.
- **PIN Cryptography:** The 4-digit master PIN and security answers are protected using cryptographic Salted SHA-256 hashing. Plaintext PINs are never stored in memory or disk.
- **Brute-Force Protection:** The security layer enforces exponential lockout periods after multiple consecutive failed PIN attempts to prevent PIN guessing attacks.
- **Isolated Per-App Bypass:** Group restriction rules evaluate individual packages strictly, preventing cross-application security leaks.

---

## Reporting a Vulnerability

If you discover a potential security vulnerability within Muraqib, please do not disclose it publicly via GitHub issues.

Instead, please report the vulnerability privately by opening a confidential security advisory on GitHub or contacting the maintainers directly through their GitHub profile:
- https://github.com/yalaahamdy

Please include:
1. Detailed description of the vulnerability.
2. Steps to reproduce or proof of concept (PoC).
3. The affected versions and devices.

We take all security reports seriously and will acknowledge receipt within 48 hours and work on a prompt remediation.
