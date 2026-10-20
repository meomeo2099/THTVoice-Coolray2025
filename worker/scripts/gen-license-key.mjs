// Creates the license signing key pair and the VIN key.
//   node worker/scripts/gen-license-key.mjs [key-id]
// Put the PRIVATE values in Cloudflare secrets; put the PUBLIC line in config/license-server.properties.
// Never commit the private key or VIN key.
const kid = process.argv[2] || 'k1';
if (!/^[a-z0-9]{1,8}$/.test(kid)) throw new Error('key id: 1-8 lowercase letters/digits');
const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
const b64 = (buf) => Buffer.from(buf).toString('base64');
const priv = b64(await crypto.subtle.exportKey('pkcs8', pair.privateKey));
const pub = b64(await crypto.subtle.exportKey('spki', pair.publicKey));
const vin = b64(crypto.getRandomValues(new Uint8Array(32)));
console.log('# 1) Cloudflare secrets (keep private, do not commit):');
console.log(`LICENSE_SIGNING_KEY=${priv}`);
console.log(`VIN_KEY=${vin}`);
console.log(`LICENSE_KEY_ID=${kid}   (wrangler.toml [vars])`);
console.log('');
console.log('# 2) Public key for the app (config/license-server.properties, safe to commit):');
console.log(`publicKeys=${kid}:${pub}`);
