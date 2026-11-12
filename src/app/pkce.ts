/**
 * PKCE (Proof Key for Code Exchange), RFC 7636.
 *
 * Antes do login, o app sorteia um segredo (code_verifier) e manda ao auth-server só o hash dele
 * (code_challenge). Na troca do código por tokens, apresenta o segredo. Assim, mesmo que alguém
 * intercepte o código na volta do login, não consegue trocá-lo: não tem o code_verifier.
 * É o que substitui o client_secret num cliente público, que não tem onde guardar segredo.
 */

/** Texto aleatório em Base64URL (sem "=" no fim), como pede o PKCE. */
export function randomString(byteLength = 32): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

/** code_challenge = BASE64URL(SHA-256(code_verifier)), o método "S256". */
export async function codeChallengeFor(codeVerifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier));
  return base64Url(new Uint8Array(digest));
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => (binary += String.fromCharCode(byte)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Lê o conteúdo (payload) de um JWT. Só leitura: quem valida a assinatura é o backend. */
export function decodeJwtPayload<T>(jwt: string): T {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const json = decodeURIComponent(
    atob(base64)
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join(''),
  );
  return JSON.parse(json) as T;
}
