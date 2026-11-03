// ChromeHeadless hardening for jsBrowserTest on constrained/containerised hosts.
// Without --no-sandbox / --disable-dev-shm-usage Chrome exits before serving a
// test, which Karma reports as "Disconnected ... no message in 30000 ms".
// Timeouts are sized for a shared machine where the default 60s capture window
// is not enough for a ~440 test Compose UI suite.
(function (config) {
  config.customLaunchers = config.customLaunchers || {};
  config.customLaunchers.ChromeHeadlessNoSandbox = {
    base: "ChromeHeadless",
    flags: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-dev-shm-usage",
      "--disable-gpu",
      "--disable-background-timer-throttling",
      "--disable-backgrounding-occluded-windows",
      "--disable-renderer-backgrounding",
    ],
  };
  config.browsers = ["ChromeHeadlessNoSandbox"];

  config.captureTimeout = 120000;          // default 60000
  config.browserNoActivityTimeout = 300000; // default 30000
  config.browserDisconnectTimeout = 30000;  // default 2000
  config.browserDisconnectTolerance = 2;    // default 0
  config.processKillTimeout = 30000;        // default 2000

  config.retryLimit = 2;                    // retries browser *capture*, not the suite

  config.client = config.client || {};
  config.client.captureConsole = true;
  config.browserConsoleLogOptions = {
    level: "debug",
    format: "%b %T: %m",
    terminal: true,
  };
})(config);
