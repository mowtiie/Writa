package com.mowtiie.writa.feature.crash

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.time.Clock
import kotlin.system.exitProcess

/** Collects the app and device facts for a report. A seam so the handler is testable without Android. */
fun interface CrashInfoProvider {
    fun infoFor(thread: Thread): CrashInfo
}

class AndroidCrashInfoProvider(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : CrashInfoProvider {
    override fun infoFor(thread: Thread): CrashInfo {
        val packageInfo =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        return CrashInfo(
            time = clock.instant().toString(),
            threadName = thread.name,
            packageName = context.packageName,
            versionName = packageInfo?.versionName,
            versionCode = packageInfo?.longVersionCode,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
}

/**
 * Writes the crash report, then hands the crash on to the handler that was installed before, so the system still
 * shows its crash behaviour. Writing never fails the crash: whatever goes wrong here is ignored.
 */
class CrashHandler(
    private val store: CrashReportStore,
    private val infoProvider: CrashInfoProvider,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val terminate: () -> Unit = ::killProcess,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        error: Throwable,
    ) {
        try {
            store.write(CrashReportFormatter.format(infoProvider.infoFor(thread), error))
        } catch (_: Throwable) {
            // The app is already crashing; there is nowhere left to report a failure to.
        }
        if (previous != null) previous.uncaughtException(thread, error) else terminate()
    }

    companion object {
        /** Call first thing in `attachBaseContext`, so crashes while the app is starting are reported too. */
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(
                    store = CrashReportStore.forApp(context),
                    infoProvider = AndroidCrashInfoProvider(context),
                    previous = Thread.getDefaultUncaughtExceptionHandler(),
                ),
            )
        }

        private fun killProcess() {
            Process.killProcess(Process.myPid())
            exitProcess(EXIT_CODE)
        }

        private const val EXIT_CODE = 10
    }
}
