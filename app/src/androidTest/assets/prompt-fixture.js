export async function signIn() {
  host.storage.set('existing-session', 'keep');
  while (true) {
    host.signInPrompt({text:'Cancel fixture login'});
    await new Promise(resolve=>setTimeout(resolve, 50));
  }
}
export function saved() { return host.storage.get('existing-session'); }
