#!/usr/bin/env bash
# Hinterlegt den Upload-Signierschlüssel einmalig als GitHub-Secrets, damit der Release-Workflow
# signierte APKs bauen kann. Quelle ist derselbe Proton-Pass-Eintrag wie bei build-release.sh.
# Die Werte gehen per stdin an gh – sie tauchen weder in der Prozessliste noch in der Shell-History auf.
#
#   pass-cli login            # falls nötig
#   tools/release/set-github-secrets.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
PC="${PASS_CLI:-$HOME/.local/bin/pass-cli}"
ITEM=(--vault-name "App-Signierung" --item-title "FreeIPTVPlayer Signierung")

"$PC" vault list >/dev/null 2>&1 || { echo "Proton Pass nicht angemeldet: erst 'pass-cli login'." >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "GitHub CLI nicht angemeldet: erst 'gh auth login'." >&2; exit 1; }

set_secret() { # $1 = Secret-Name, $2 = Feld im Proton-Pass-Eintrag
  "$PC" item view "${ITEM[@]}" --field "$2" | tr -d '\n' | gh secret set "$1"
}
set_secret IPTV_KEYSTORE_BASE64 KEYSTORE_BASE64
set_secret IPTV_STORE_PASSWORD STORE_PASSWORD
set_secret IPTV_KEY_ALIAS KEY_ALIAS
set_secret IPTV_KEY_PASSWORD KEY_PASSWORD
echo "Fertig:"; gh secret list
