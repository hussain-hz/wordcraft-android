package io.github.artcraftandroid.wordcraft.updates

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            var retry = false
            try {
                AppUpdates.check(this@UpdateJobService)
            } catch (_: IOException) { retry = true
            } catch (_: Exception) { /* Malformed API response: try again at the next interval. */ }
            if (isActive) jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
