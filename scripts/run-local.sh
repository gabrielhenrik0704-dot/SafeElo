#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
umask 077
mkdir -p .local
if [ ! -f .local/server-key ]; then
  if [ -f .local/db/safeelo.mv.db ]; then echo "Banco existente sem chave local. Restaure .local/server-key do backup antes de iniciar." >&2; exit 1; fi
  if command -v openssl >/dev/null 2>&1; then openssl rand -base64 32 > .local/server-key
  else python3 -c 'import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())' > .local/server-key; fi
fi
export SAFEELO_ENCRYPTION_KEY="$(cat .local/server-key)"
if [ ! -f target/safeelo-1.0.0.jar ]; then
  "${MAVEN_BIN:-./mvnw}" -B package
fi
exec java -jar target/safeelo-1.0.0.jar
