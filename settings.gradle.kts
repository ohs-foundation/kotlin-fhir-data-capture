import androidx.build.gradle.gcpbuildcache.GcpBuildCache
import androidx.build.gradle.gcpbuildcache.GcpBuildCacheServiceFactory

plugins {
  id("com.gradle.enterprise") version ("3.10")
  id("androidx.build.gradle.gcpbuildcache") version "1.0.0-beta07"
  id("org.gradle.toolchains.foojay-resolver-convention") version ("0.5.0")
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
    capture { isTaskInputFiles = true }
  }
}

val kokoroRun = providers.environmentVariable("KOKORO_BUILD_ID").isPresent

if (kokoroRun == true) {
  buildCache {
    registerBuildCacheService(GcpBuildCache::class, GcpBuildCacheServiceFactory::class)
    remote(GcpBuildCache::class) {
      projectId = "android-fhir-build"
      bucketName = "android-fhir-build-cache"
      isPush = true
    }
  }
}

include(":datacapture")

include(":sdc-kmp-demo")
