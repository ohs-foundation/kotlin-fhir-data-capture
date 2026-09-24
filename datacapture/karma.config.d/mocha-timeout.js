// Mocha's default 2000ms per-test timeout is too tight for a Compose/Skia render when the whole
// suite shares one browser tab: tests pass in isolation but intermittently exceed it under load.
config.set({
    client: Object.assign({}, config.client, {
        mocha: Object.assign({}, (config.client && config.client.mocha) || {}, {
            timeout: 10000,
        }),
    }),
})
