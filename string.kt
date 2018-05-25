import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Extension function to strings to enable call shell commands in a more straightforward way. It runs the content of the
 * string object whose method was called in a given directory with optional parameters to put a timeout at the process
 * and returns the value returned by the mentioned command to the shell.
 * @param workingDir directory in which the command should be run
 * @param timeOut time at which the process should be terminated (defaults to 60)
 * @param timeUnit TimeUnit for the previous parameter (defaults to TimeUnit.MINUTES)
 * @return the exit value of the called process to run the command
 */
fun String.runCommand(workingDir: File, timeOut: Long = 60, timeUnit: TimeUnit = TimeUnit.MINUTES): Int {
    val p = ProcessBuilder(*split(" ").toTypedArray()).directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    p.waitFor(timeOut, timeUnit)
    return p.exitValue()
}

/**
 * Extension function to string iterators to enable skipping while a condition is NOT satisfied.
 * @param r higher-order function over a String which returns FALSE if the iterator should keep skipping
 * @return the first String to which r returns true or null if none of those exists in the iterator
 */
inline fun Iterator<String>.skipWhile(r: (String) -> Boolean): String? {
    var str: String? = null
    var match = false
    while(this.hasNext() && !match) {
        str = this.next()
        match = r(str)
    }

    return if(match) str else null
}
