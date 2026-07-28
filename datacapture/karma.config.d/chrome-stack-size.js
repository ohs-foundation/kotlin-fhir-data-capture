;(function (config) {
    // kotlin-fhirpath's generated sealed-interface dispatch (see MoreSealedInterfaces.kt) compiles
    // to deeply nested JS that can exceed V8's default call-stack limit at runtime, surfacing as
    // "RangeError: Maximum call stack size exceeded" during FHIRPath evaluation. That's swallowed by
    // FhirPathService.evaluate()'s runCatching, so it looks like wrong/empty results downstream
    // rather than a crash. Chrome's V8 stack size varies by build; raise it explicitly so evaluation
    // isn't at the mercy of whatever headless Chrome happens to be installed.
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
