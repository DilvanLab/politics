import java.io.File // para abrir cada um dos arquivos
import java.io.FileWriter // para escrever no log
import java.lang.Math.ceil
import java.lang.Math.floor
import kotlin.system.measureTimeMillis

/**
 * Finds out at which mode the log should be opened. If there is NOT any log file yet, it tells a new log should be
 * created, if there is a log it asks the user if the former log must be resumed or not. For the mentioned situations
 * the function returns respectively "new", "resume" and "truncate". If something went wrong it returns "unknown".
 * @param log a description of the log file
 * @return a string with the mode at which the log will be opened
 */
fun checkLog(log: Dossier): String {
    return if (File(log.fullPath()).isFile) {
        println("There is already a log called ${log.fullName()} at (${log.path})")
        println("Enter 0 to resume it or 1 to start a new log (and mapping):")

        val read = readLine()!!.split(" \t")[0].toInt()
        when (read) {
            0 -> "resume"
            1 -> "truncate"
            else -> "unknown"
        }
    } else {
        "new"
    }
}

/**
 * Creates a list of folders in a directory in an easy to manage format (Dossier objects).
 * @param root path to the root file for listing
 * @return a list of folders that might be empty
 */
fun listFolders(root: String): List<Dossier> {
    var folders = listOf<Dossier>()
    folders += Dossier(root)
    File(root).list().forEach {
        val path = Dossier(root).fullPath() + it
        if (File(path).isDirectory) folders += Dossier("$path/")
    }

    return folders
}

/**
 * Creates a list of files matching some optional conditions at a given folder.
 * @param folder the folder's path
 * @param strEval a function to filter file names, keeps those which evaluates to true
 * @param first the first file to be kept (at the order the system lists them)
 * @return a list of remaining files
 */
fun listFiles(
        folder: String, extensions: MutableList<String>? = null,
        strEval: ((String) -> Boolean)? = null, first: String? = null
): List<Dossier> {
    var files = listOf<String>()
    File(folder).list().forEach { files += folder + it }

    if (extensions != null) files = files.filter { ('.' + it.split('.').last()) in extensions }

    if (strEval != null) files = files.filter { strEval(it.split("/").last()) }

    if (first != null) files = files.dropWhile { it.split("/").last() != first }

    var dossiers = listOf<Dossier>()
    if (files.isNotEmpty()) files.forEach { dossiers += Dossier(it) }

    return dossiers
}

/**
 * Writes the beginning of the log file, indicating if it was written at once or resumed at some point. This is the only
 * function that writes to the log without appending, it writes "Just started..." or "This log had been overwritten!"
 * and the root folder for mapping.
 * @param root root folder for mapping
 * @param mode "new" or "resume" ("new" by default and other options not treated)
 * @param logPath log's absolute path
 */
fun startLog(root: String, mode: String = "new", logPath: String) {
    if (mode == "new") {
        File(logPath).printWriter().use {
            it.println("Just started...")
        }
    } else {
        File(logPath).printWriter().use {
            it.println("This log had been overwritten!")
        }
    }

    FileWriter(logPath, true).use {
        it.appendln("Root file for mapping: $root")
    }
}

/**
 * Maps data files with a command tailored by the arguments given to this function. This function must receive at least
 * some basic parameters in order for it to call BioDSL. Each call is made over a chunk of a data file (which might but
 * NOT always cover the complete file), the chunks' size might be different between different calls and are changed by
 * measures over time taken to finish a call.
 * @param jar the path to the BioDSL jar
 * @param mapScript mapScript file info (mainly the path)
 * @param input a list of files o ve mapped
 * @param document a string that is common to all the others files that determines the output names (this + a number)
 * @param output the output folder path to the call
 * @param opt options to be passed to BioDSL (without first and last line, '-b' and '-e' respectively)
 * @param startSize size to be used for the first chunk (defaults to 1000)
 * @param timeLowerBound if a call takes less time in microseconds than this, the chunk size is decreased
 * @param timeUpperBound if a call takes more time in microseconds than this, the chunk size is increased
 * @param firstLine line to start only at the first file
 */
fun mapFiles (
        jar: String, mapScript: Dossier, input: List<Dossier>, document: String, output: String,
        opt: String = "", log: Dossier,
        startSize: Int = 1000, timeLowerBound: Int = 500, timeUpperBound: Int = 1000, firstLine: Int = 0
) {
    var i =
            if (File(output).isDirectory) {
                File(output).list().count()
            } else {
                File(output).mkdirs()
                0
            }

    val call = "$jar ${mapScript.fullPath()}"
    var lastLine = firstLine

    for(f in input) {
        val reader = File(f.fullPath()).inputStream().bufferedReader()
        var chunkSize = startSize
        val linesTotal = reader.lines().count()

        if (lastLine < linesTotal) {
            FileWriter(log.fullPath(), true).use {
                it.appendln("(MAPPING ${f.fullPath()})")
            }
        }

        while(lastLine < linesTotal) {
            var command = "$call -i ${f.fullPath()} -b $lastLine "
            if (linesTotal - lastLine > chunkSize) lastLine += chunkSize else lastLine = linesTotal.toInt()
            command += "-e $lastLine -o $output/$document-$i.ttl  $opt"
            try{
                var status = 0
                val timeElapsed = measureTimeMillis { status = command.runCommand(File(output)) }
                if (status == 0) {
                    FileWriter(log.fullPath(), true).use {
                        it.appendln("$lastLine lines done")
                    }
                }
                else {
                    FileWriter(log.fullPath(), true).use {
                        it.appendln("There was some error, look at the stderr to find out what happened!")
                    }
                    break
                }

                chunkSize =
                        when {
                            timeElapsed > timeUpperBound -> ceil(chunkSize * 1.1).toInt()
                            timeElapsed < timeLowerBound -> floor(chunkSize * 0.9).toInt()
                            else -> chunkSize
                        }
                i += 1
            } catch (e: Exception) {
                FileWriter(log.fullPath(), true).use {
                    it.append(e.message)
                }
                break
            }
        }

        lastLine = 0
    }
}

/** Recovers the state at which the log stopped the last time the logger worked.
 * @param logPath path to the log to be used
 * @return a list of String with the last folder, map script, input file and line done (at this order, at the log format)
 * null if none were done
 */
fun lastFromLog(logPath: String): List<String?> {
    var lastFolder: String? = null
    var lastMap: String? = null
    var lastFile: String? = null
    var lastLine: String? = null
    var line: String?

    val reader = File(logPath).inputStream().bufferedReader()
    val iterator = reader.lineSequence().iterator()
    while (iterator.hasNext()) {
        line = iterator.next()
        when {
            Regex("\\(ENTERED .+\\)").matches(line) -> {
                lastFolder = line.substring(9,line.length - 1)
                lastMap = null
                lastFile = null
                lastLine = null
            }
            Regex("\\(USING .+\\)").matches(line) -> {
                lastMap = line.substring(7, line.length - 1)
                lastFile = null
                lastLine = null
            }
            Regex("\\(MAPPING .+\\)").matches(line) -> {
                lastFile = line.substring(9, line.length - 1)
                lastLine = null
            }
            Regex("[0-9]+ lines done").matches(line) -> {
                lastLine = line.split(" ").first()
            }
        }
    }
    reader.close()

    return listOf(lastFolder, lastMap, lastFile, lastLine)
}

/** Resumes a log from the point it stopped at the last execution for the last folder and then returns the remaining
 * folders to main continue as if it has NOT stopped.
 * @param root path to the file at which the data files are
 * @param logPath path to the log being used
 * @return folders that were NOT started yet, in order for main to handle them
 */
fun resumeLastFolder(root: String, logPath: String): List<Dossier> {
    val (folder, map, file, line) = lastFromLog(logPath)
    var folders = listFolders(root)
    var bios = File(root + "bios/").list().filter {it.toString().takeLast(4) == ".bio" }.map{ root + "bios/" + it }

    if (folder == null) return folders

    folders = folders.dropWhile { it.fullPath() != folder }
    if (folders.isNotEmpty()) folders = folders.drop(1)

    val f = Dossier(folder)

    when(bios.size) {
        0 -> println("There are no mappings at ${f.name}")
        else -> {
            val command = "java -jar $root/map-dsl-0.1-standalone.jar"
            val opt = "-f turtle"
            val extensions = mutableListOf(".txt", ".csv")

            if (map != null) bios = bios.dropWhile { Dossier(folder + it).fullPath() != map }

            var lastLine = if (line == null) 0 else line.toInt() + 1
            for (b in bios) {
                val mapScript = Dossier(b)
                val base = b.split("/").last().split(".").dropLast(1).joinToString(".")
                val output = root + base + "_out/"

                var files = listFiles(f.fullPath(), extensions,
                        strEval={ s -> Regex("$base[0-9a-zA-Z_.]*").matches(s) })
                if (files.isNotEmpty()) {
                    println("The mapped files are named $base[X].ttl with X being a number at the folder $output")
                    if (bios.indexOf(b) > 0 || map == null) {
                        FileWriter(logPath, true).use {
                            it.appendln("(USING ${mapScript.fullPath()})")
                        }
                    } else if (file != null) {
                        files = files.dropWhile { it.fullPath() != file }
                    }

                    mapFiles(command, mapScript, files, base, output, opt, Dossier(logPath), firstLine=lastLine)
                }
                lastLine = 0
            }
        }
    }

    return folders
}

fun main(args: Array<String>) {
    println("Insert the root path for mapping:")
    val location = readLine()!!
    val root = if(location.last() != '/') Dossier(location.plus('/')) else Dossier(location)
    val bios = File(root.fullPath() + "bios/").list()
            .filter {it.toString().takeLast(4) == ".bio" }.map{ root.fullPath() + "bios/" + it }
    val log = Dossier(root.fullPath() + "mapper.log")

    var mode = "unknown"
    while (mode == "unknown") mode = checkLog(log)

    val folders =
            if (mode == "resume") {
                resumeLastFolder(root.fullPath(), log.fullPath())
            } else {
                startLog(root.fullPath(), mode, log.fullPath())
                listFolders(root.fullPath())
            }

    for(f in folders) {
        FileWriter(log.fullPath(), true).use {
            it.appendln("(ENTERED ${f.fullPath()})")
        }

        when(bios.size) {
            0 -> println("There are no mappings at ${f.name}")
            else -> {
                val command = "java -jar $location/map-dsl-0.1-standalone.jar"
                val opt = "-f turtle"
                val extensions = mutableListOf(".txt", ".csv")

                for (b in bios) {
                    val mapScript = Dossier(b)
                    val base = b.split("/").last().split(".").dropLast(1).joinToString(".")
                    val output = root.fullPath() + "out/$base/"

                    val files = listFiles(f.fullPath(), extensions,
                            strEval={ s -> Regex("$base[0-9a-zA-Z_.]*").matches(s) })
                    if (files.isNotEmpty()) {
                        println("The mapped files are named $base[X].ttl with X being a number at the folder $output")
                        FileWriter(log.fullPath(), true).use {
                            it.appendln("(USING ${mapScript.fullPath()})")
                        }

                        mapFiles(command, mapScript, files, base, output, opt, log)
                    }
                }
            }
        }
    }
}

/* TODO
 * comment code working
 * tailor the program by command line arguments
 * remove hardcoded paths
 * set graddle to this project
 */