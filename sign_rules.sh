#!/bin/bash
set -euo pipefail

# === CONFIG ===
FOLDER="/home/nigga_nasty/Desktop/VerumOmnisV1"
KEYSTORE="$FOLDER/verum-release-key.jks"
ALIAS="verum"
PASS="lPwsmJjGt8PhfovbR6W12AHy2LLUISgEn9"
RULES_JSON="$FOLDER/app/src/main/assets/rules.json"   # unsigned rules data
OUT_VOP="$FOLDER/app/src/main/assets/rules.vop"       # signed rules pack

# === STEP 1: Export private key from JKS ===
echo "=== Exporting private key from JKS to PEM ==="
# Convert JKS → PKCS12 (temporary)
keytool -importkeystore \
  -srckeystore "$KEYSTORE" -srcstorepass "$PASS" \
  -srcalias "$ALIAS" -destalias "$ALIAS" \
  -destkeystore "$FOLDER/tmp-keystore.p12" -deststoretype PKCS12 \
  -deststorepass "$PASS"

# Extract private key PEM
openssl pkcs12 -in "$FOLDER/tmp-keystore.p12" \
  -nocerts -nodes -password pass:$PASS \
  -out "$FOLDER/private_key.pem"

# === STEP 2: Sign rules.json ===
echo "=== Signing rules.json ==="
openssl dgst -sha256 -sign "$FOLDER/private_key.pem" "$RULES_JSON" > "$FOLDER/sig.bin"

# === STEP 3: Build rules.vop ===
echo "=== Building rules.vop ==="
cat "$RULES_JSON" "$FOLDER/sig.bin" > "$OUT_VOP"

# === STEP 4: Verify ===
echo "=== Verifying signature ==="
openssl x509 -in "$FOLDER/public_cert.pem" -pubkey -noout > "$FOLDER/pub.pem"
openssl dgst -sha256 -verify "$FOLDER/pub.pem" -signature "$FOLDER/sig.bin" "$RULES_JSON" \
  && echo "✔ Signature valid" || echo "❌ Signature check failed"

# === STEP 5: Cleanup ===
rm -f "$FOLDER/tmp-keystore.p12" "$FOLDER/private_key.pem" "$FOLDER/sig.bin" "$FOLDER/pub.pem"

echo "=== DONE: Created $OUT_VOP ==="
ls -lh "$OUT_VOP"
