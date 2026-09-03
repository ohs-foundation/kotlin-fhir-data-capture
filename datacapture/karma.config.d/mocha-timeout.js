// Mocha's default 2000ms per-test timeout is too tight for a Compose/Skia render under load
// (many tests sharing one browser tab/GC). Confirmed flaky on both wasmJsBrowserTest and
// jsBrowserTest: passes reliably alone, intermittently exceeds 2000ms when run with the full
// suite. Raise the timeout instead of just tolerating occasional CI flakes.
config.set({
    client: Object.assign({}, config.client, {
        mocha: Object.assign({}, (config.client && config.client.mocha) || {}, {
            timeout: 10000,
        }),
    }),
})
