const fs = require('fs')
const path = require('path')

const composeResourcesTestingSetupFile = path.join(__dirname, 'compose-resources-testing-setup.js')
fs.writeFileSync(composeResourcesTestingSetupFile, 'window.composeResourcesTesting = true;\n')

config.set({
    files: [composeResourcesTestingSetupFile].concat(config.files).concat([
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
