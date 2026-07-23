;(function (config, __dirname) {
    var fs = require('fs')
    var path = require('path')

    // The Kotlin/JS `js` target's Skiko bridge (js-reexport-symbols.mjs) loads skiko.wasm and
    // assigns its exports onto `window` as bare globals. Nothing require()s this .mjs file, and
    // it's absent from the Karma "files" list by default, so Compose UI tests fail with
    // "ReferenceError: org_jetbrains_skia_... is not defined" the moment they touch the canvas.
    var kotlinDir = path.join(__dirname, 'kotlin')
    var skikoReexportPath = path.join(kotlinDir, 'js-reexport-symbols.mjs')

    if (fs.existsSync(skikoReexportPath)) {
        // skiko.wasm instantiation kicked off by js-reexport-symbols.mjs is asynchronous.
        // Without gating Mocha's run on it, tests race the WASM load and fail with
        // "TypeError: org_jetbrains_skia_... is not a function". A global `before()` hook
        // (registered during Mocha's collection phase, before any suite runs) closes that race.
        var skikoAwaitSetupPath = path.join(kotlinDir, 'skiko-await-setup.mjs')
        fs.writeFileSync(
            skikoAwaitSetupPath,
            'import { api } from "./js-reexport-symbols.mjs"\n' +
                'before(function () {\n' +
                '  return api.awaitSkiko\n' +
                '})\n',
        )

        config.set({
            files: [skikoReexportPath, skikoAwaitSetupPath].concat(config.files),
            preprocessors: Object.assign({}, config.preprocessors, {
                [skikoReexportPath]: ['webpack', 'sourcemap'],
                [skikoAwaitSetupPath]: ['webpack', 'sourcemap'],
            }),
        })
    }
})(config, __dirname)
