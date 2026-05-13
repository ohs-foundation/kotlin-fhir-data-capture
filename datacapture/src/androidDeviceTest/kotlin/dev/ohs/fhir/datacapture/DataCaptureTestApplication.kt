package dev.ohs.fhir.datacapture

import android.app.Application

/** Application class when you want to test the DataCaptureConfig.Provider */
internal class DataCaptureTestApplication : Application(), DataCaptureConfig.Provider {

    override fun onCreate() {
        super.onCreate()
        DataCapture.initialize(this)
    }

    override fun getDataCaptureConfig(): DataCaptureConfig {
        return DataCaptureConfig()
    }
}