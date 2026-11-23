# Connecting Silo as a Home Server

Silo is supported through its Jellyfin-compatible API, using the existing
Jellyfin connection format. The connection and its libraries sync between
Android/TV and the webapp without a separate provider or administrator token.

1. Ask the owner for the **Jellyfin-compatible address**. Silo's main website
   and compatibility listener can use different ports or public URLs. A working
   browser login at the main website does not confirm the compatible address.
   ARVIO does not guess other ports or send credentials to discovered hosts.
2. In Android/TV, open Settings → Home Server. In the webapp, choose
   **Jellyfin / Silo**. Use the supplied address, preserving any base path.
3. Enter your ordinary account login and password. If Silo asks for a profile,
   use `username#ProfileName` (or `email#ProfileName` if your server accepts email
   login). Use your actual Silo profile name, not an ARVIO profile name.
4. For a PIN-protected Silo profile, use `password#PIN` as the password. ARVIO
   passes this unchanged to Silo; it never selects a profile or bypasses a PIN.
5. Connect and enable the libraries shared with that account.

A single eligible unprotected profile can be selected automatically by Silo;
the suffix is not required for every account. The compatibility service must
be enabled and exposed by the server owner. No administrator access is needed
by the ARVIO user.

The login handling distinguishes profile selection, profile PIN and incorrect
API address errors from invalid account credentials. Server error text is
mapped to fixed messages rather than displaying arbitrary response bodies.
Passwords and profile PINs are exchanged only during login and are not saved
in successful synced connections.

## Evidence and verification

Behavior checked against Silo's public implementation:
- [Login and profile/PIN selection](https://github.com/Silo-Server/silo-server/blob/main/internal/jellycompat/login.go)
- [Compatible authentication request and response](https://github.com/Silo-Server/silo-server/blob/main/internal/jellycompat/handlers_auth.go)
- [Separate compatibility listener](https://github.com/Silo-Server/silo-server/blob/main/internal/jellycompat/server.go)

Regression fixtures cover normal-user login, base paths, unchanged profile/PIN
credentials, library discovery, actionable failures and fresh web connection
tests. They do not establish that a particular private Silo deployment works;
its exposed API address, version and account configuration still need a live check.
