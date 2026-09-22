;(function (config) {
    // kotlin-fhirpath's generated sealed-interface dispatch (see MoreSealedInterfaces.kt) compiles
    // to deeply nested JS that can exceed V8's default call-stack limit during FHIRPath evaluation.
    // The resulting "RangeError: Maximum call stack size exceeded" is swallowed by
    // FhirPathService.evaluate()'s runCatching and surfaces as empty results rather than a crash.
    // The limit is set explicitly because it varies between Chrome builds.
    config.set({
        customLaunchers: Object.assign({}, config.customLaunchers, {
            ChromeHeadlessBigStack: {
                base: 'ChromeHeadless',
                flags: ['--js-flags=--stack-size=8192'],
            },
        }),
        browsers: ['ChromeHeadlessBigStack'],
    })
})(config)
