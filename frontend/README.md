# Frontend

React + TypeScript application for Amnezia Control Panel. See the root README for
setup, Docker, authentication and API generation instructions.

- `src/auth.tsx`: sessions, invitation registration and nickname/password editing
- `src/api.ts`: authenticated transport, refresh coordination, error envelope
- `src/devices.tsx`: devices and configuration delivery
- `src/admin.tsx`: users, invitations, dashboard, VPN health and audit
- `src/user-picker.tsx`: paginated user dropdown for audit filtering
- `src/guide.tsx`: connection guide with supplied screenshots and official app links
- `src/theme.tsx`: persistent light/dark theme, switched in the sidebar
- `src/ui.tsx`: accessible dialogs, errors, paging and resource lifecycle
- `src/schema.d.ts`: generated API types (do not edit by hand)
- `tests/app.spec.ts`: browser acceptance tests

Design: responsive Russian interface with light/dark themes and Amnezia orange accents;
no remote fonts, analytics or external assets.

The live browser test is opt-in and uses the ignored root `.env`:
`RUN_VPS_TESTS=1 pnpm test:e2e tests/live.spec.ts` (PowerShell: set
`$env:RUN_VPS_TESTS='1'` first). It creates a uniquely named temporary user and peer,
checks real configuration delivery and revocation, and deletes its user in `finally`.
Never run it against an unapproved server. The normal browser suite does not touch VPS.
