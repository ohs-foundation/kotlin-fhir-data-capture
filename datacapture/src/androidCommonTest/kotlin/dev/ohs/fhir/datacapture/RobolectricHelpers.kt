/*
 * Copyright 2025-2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.datacapture




actual typealias Runner = org.junit.runner.Runner
actual typealias RunWith = org.junit.runner.RunWith
actual typealias AndroidJUnit4 = androidx.test.ext.junit.runners.AndroidJUnit4

actual fun printApplicationPackageName() {
  val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  println("--- Application Context Package Name: ${context.packageName} ---")
}

actual fun setupRobolectricActivity() {
  registerComponentActivity()
}

fun registerComponentActivity() {
  val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  try {
    val shadowsClazz = Class.forName("org.robolectric.Shadows")
    val shadowOfMethod = shadowsClazz.getMethod("shadowOf", android.content.pm.PackageManager::class.java)
    val shadowPackageManager = shadowOfMethod.invoke(null, context.packageManager)

    val activityInfo = android.content.pm.ActivityInfo().apply {
      name = "dev.ohs.fhir.datacapture.TestActivity"
      packageName = context.packageName
      enabled = true
      exported = true
    }

    val addOrUpdateActivityMethod = shadowPackageManager.javaClass.getMethod("addOrUpdateActivity", android.content.pm.ActivityInfo::class.java)
    addOrUpdateActivityMethod.invoke(shadowPackageManager, activityInfo)

    val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
      addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }
    val addIntentFilterForActivityMethod = shadowPackageManager.javaClass.getMethod(
      "addIntentFilterForActivity",
      android.content.ComponentName::class.java,
      android.content.IntentFilter::class.java
    )
    addIntentFilterForActivityMethod.invoke(
      shadowPackageManager,
      android.content.ComponentName(context.packageName, "dev.ohs.fhir.datacapture.TestActivity"),
      intentFilter
    )
  } catch (e: ClassNotFoundException) {
    // We are running on a real device, Robolectric is not on the classpath. Do nothing.
  }

  // Initialize Compose Multiplatform resources context via reflection
  try {
    val clazz = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
    val companionField = clazz.getDeclaredField("Companion")
    companionField.isAccessible = true
    val companionInstance = companionField.get(null)
    val setContextMethod = companionInstance.javaClass.getMethod("setANDROID_CONTEXT", android.content.Context::class.java)
    setContextMethod.invoke(companionInstance, context)
    println("--- Programmatic AndroidContextProvider context set successfully ---")
  } catch (e: Throwable) {
    println("--- Failed to set AndroidContextProvider context: ${e.message} ---")
    e.printStackTrace()
  }
}

open class TestActivity : androidx.activity.ComponentActivity() {
  private var mockResources: android.content.res.Resources? = null

  override fun getResources(): android.content.res.Resources {
    if (mockResources == null) {
      val original = super.getResources()
      mockResources = object : android.content.res.Resources(
        original.assets,
        original.displayMetrics,
        original.configuration
      ) {
        override fun getText(id: Int): CharSequence {
          return try {
            super.getText(id)
          } catch (e: NotFoundException) {
            "Mock String"
          }
        }

        override fun getString(id: Int): String {
          return try {
            super.getString(id)
          } catch (e: NotFoundException) {
            "Mock String"
          }
        }

        override fun getString(id: Int, vararg formatArgs: Any?): String {
          return try {
            super.getString(id, *formatArgs)
          } catch (e: NotFoundException) {
            "Mock String"
          }
        }
      }
    }
    return mockResources!!
  }
}

actual fun runLooperTasks() {
  try {
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runToEndOfTasks()
  } catch (e: Throwable) {
    // Ignore
  }
}

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
actual fun runComposeUiTestWrapper(block: suspend androidx.compose.ui.test.ComposeUiTest.() -> Unit) {
  setupRobolectricActivity()
  
  try {
    androidx.compose.ui.test.runAndroidComposeUiTest<TestActivity> {
      block()
    }
  } finally {

    val shadowClassNames = listOf(
      "org.robolectric.shadows.ShadowLineBreaker",
      "org.robolectric.shadows.ShadowMeasuredText",
      "org.robolectric.shadows.ShadowPaint",
      "org.robolectric.shadows.ShadowTypeface",
      "org.robolectric.shadows.ShadowFont",
      "org.robolectric.shadows.ShadowFontFamily",
      "org.robolectric.shadows.ShadowCanvas",
      "org.robolectric.shadows.ShadowPath"
    )
    for (className in shadowClassNames) {
      try {
        val clazz = Class.forName(className)
        for (field in clazz.declaredFields) {
          if (field.type.name == "org.robolectric.res.android.NativeObjRegistry") {
            field.isAccessible = true
            val registry = field.get(null)
            if (registry != null) {
              val clearMethod = registry.javaClass.getMethod("clear")
              clearMethod.invoke(registry)
            }
          }
        }
      } catch (e: Throwable) {
        // Ignore
      }
    }
  }
}

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
actual fun androidx.compose.ui.test.ComposeUiTest.waitUntilSynchronized(
  timeoutMillis: Long,
  condition: () -> Boolean
) {
  val start = System.currentTimeMillis()
  val wasAutoAdvance = mainClock.autoAdvance
  mainClock.autoAdvance = false
  try {
    while (!condition()) {
      if (System.currentTimeMillis() - start > timeoutMillis) {
        throw androidx.compose.ui.test.ComposeTimeoutException("Condition still not satisfied after $timeoutMillis ms")
      }
      try {
        mainClock.advanceTimeBy(16)
      } catch (e: Throwable) {
        // Ignore
      }
      try {
        val shadowLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        shadowLooper.runToEndOfTasks()
      } catch (e: Throwable) {
        // Ignore
      }
      try {
        val testDispatcher = kotlinx.coroutines.Dispatchers.Main as? kotlinx.coroutines.test.TestDispatcher
        if (testDispatcher != null) {
          testDispatcher.scheduler.advanceTimeBy(16)
          testDispatcher.scheduler.runCurrent()
        }
      } catch (e: Throwable) {
        // Ignore
      }
      try {
        Thread.sleep(10)
      } catch (e: InterruptedException) {
        break
      }
    }
  } finally {
    mainClock.autoAdvance = wasAutoAdvance
  }
}

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
actual fun androidx.compose.ui.test.ComposeUiTest.advanceTimeAndIdleLooper(millis: Long) {
  mainClock.advanceTimeBy(millis)
  try {
    val shadowLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    try {
      shadowLooper.idleFor(millis, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (e: Throwable) {
      shadowLooper.runToEndOfTasks()
    }
  } catch (e: Throwable) {
    // Ignore
  }
  try {
    val testDispatcher = kotlinx.coroutines.Dispatchers.Main as? kotlinx.coroutines.test.TestDispatcher
    if (testDispatcher != null) {
      testDispatcher.scheduler.advanceTimeBy(millis)
      testDispatcher.scheduler.runCurrent()
    }
  } catch (e: Throwable) {
    // Ignore
  }
}


