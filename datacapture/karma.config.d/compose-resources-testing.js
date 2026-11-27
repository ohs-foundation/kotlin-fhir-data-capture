const path = require('path')

// Serves Compose resources to the test page at the path the resources runtime fetches from.
config.set({
    files: config.files.concat([
        {
            pattern: path.join(__dirname, 'kotlin', 'composeResources', '**', '*'),
            included: false,
            served: true,
            watched: false,
        },
    ]),
    proxies: Object.assign({}, config.proxies, {
        '/composeResources/': '/base/kotlin/composeResources/',
    }),
})
