import java.io.File // para abrir cada um dos arquivos
import java.io.FileWriter // para escrever no log
import java.io.FileReader // para ler do CSV original
import java.lang.Math.ceil
import java.lang.Math.floor
import kotlin.system.measureTimeMillis

// lê até encontrar um determinado delimitador em um arquivo
fun FileReader.readUntil(delim: String): String {
    var line = ""
    var read = this.read().toChar()
    while (read !in delim) {
        line += read
        read = this.read().toChar()
    }

    return line
}

// extensão a iteradores para escrever um número de strings em um arquivo
fun Iterator<String>.writeToFile (name: String, total: Int = Int.MAX_VALUE): Int {
    val folder = name.split("/").dropLast(1).joinToString("/")
    File(folder).mkdirs()
    val output = FileWriter(name)
    var i = 0
    while (i < total && this.hasNext()) {
        output.appendln(this.next())
        i += 1
    }
    output.close()

    return i
}

// pula os termos de um iterador enquanto uma condição é satisfeita
inline fun Iterator<String>.skipWhile(
        r: (String) -> Boolean
): String? {
    var str: String? = null
    var match = false
    while(this.hasNext() && !match) {
        str = this.next()
        match = r(str)
    }

    return if(match) str else null
}

// determina o modo de operação do logger
fun openLog(root: String, log: String = "mapper.log"): String {
    return if (File(root + log).isFile) {
        println("Já existe um registro de mapeamento ($log).")
        println("Digite 0 para continuar de onde estava ou 1 para subtituí-lo:")

        val read = readLine()!!.split(" ")[0].toInt()
        when (read) {
            0 -> "resume"
            1 -> "truncate"
            else -> "unknown"
        }
    } else {
        "new"
    }
}

// cria uma lista de pastas dada uma raiz
fun listFolders(root: String): MutableList<String> {
    val folders = mutableListOf<String>()
    folders.add(root)
    File(root).list().forEach {
        val name = root + it
        if (File(name).isDirectory) folders.add(name.plus('/'))
    }

    return folders
}

// função para iniciar um log (novo ou truncado)
fun startLog(root: String, mode: String = "new", log: String = "mapper.log") {
    if (mode == "new") {
        File(root + log).printWriter().use {
            out -> out.println("Just started...")
        }
    } else {
        File(root + log).printWriter().use {
            out -> out.println("This log had been overwritten!")
        }
    }

    FileWriter(root + log, true).use {
        out -> out.appendln("Root file for mapping: $root")
    }
}

// encontra a última operação bem sucedida no log
fun getLastFromLog(log: String): List<String?> {
    var lastFolder: String? = null
    var lastMap: String? = null
    var lastFile: String? = null
    var lastLine: String? = null
    var line: String?

    // descobrindo a última pasta e o último CSV
    val reader = File(log).inputStream().bufferedReader()
    val iterator = reader.lineSequence().iterator()
    while (iterator.hasNext()) {
        line = iterator.next()
        when {
            Regex("\\(ENTERED [a-zA-Z0-9/]+\\)").matches(line) -> {
                lastFolder = line
                lastMap = null
                lastFile = null
                lastLine = null
            }
            Regex("\\(USING [a-zA-Z0-9/]+\\)").matches(line) -> {
                lastMap = line
                lastFile = null
                lastLine = null
            }
            Regex("\\(MAPPING [a-zA-Z0-9/]+\\)").matches(line) -> {
                lastFile = line
                lastLine = null
            }
            Regex("\\([0-9]+ lines done").matches(line) -> lastLine = line
        }
    }
    reader.close()

    return listOf(lastFolder, lastMap, lastFile, lastLine)
}

// função para mapear os CSVs
fun mapFiles (
        root: String, files: List<String>, base: String, folder: String,
        log: String = "mapper.log",
        startSize: Int = 1000, timeLowerBound: Int = 1000*30, timeUpperBound: Int = 1000*60
) {
    for(csv in files) {
        FileWriter(root + log, true).use {
            out -> out.appendln("(MAPPING $csv)")
        }

        val reader = File(folder + csv).inputStream().bufferedReader()
        val data = reader.lineSequence().iterator()
        while(data.hasNext()) {
            var csize = startSize
            var cnum = 0
            var written = 0
            val r = File(folder + csv).inputStream().bufferedReader()
            val it = r.lineSequence().iterator()
            while(it.hasNext()) {
                val timeElapsed = measureTimeMillis {
                    written += it.writeToFile(root + "owls/" + base + "%04d".format(cnum), csize)
                    FileWriter(root + log, true).use { out ->
                        out.appendln("$written lines done")
                    }
                    cnum++
                }
                if (timeElapsed > timeUpperBound) csize = ceil(csize * 1.1).toInt()
                else if (timeElapsed < timeLowerBound) csize = floor(csize * 0.9).toInt()
            }
        }
    }
}

// FIXME: ver se esta função está funcionando direitinho
// recuperar do último log, fazer esta pasta separada
fun resumeLastFolder(
        root: String, log: String = "mapper.log", chunkStartSize: Int = 150
): List<String?> {
    val (folder, map, file, line) = getLastFromLog(root + log)
    val folders = listFolders(root)

    // terminando a última pasta que o script estava fazendo quando interrompido
    folders.dropWhile { it.split("/").last() != folder }
    folders.drop(0)

    val bio = FileReader(map)
    val base = bio.readUntil("\n").split("\"")[1].split(".")[0]
    bio.close()

    val csvs = File(folder).list().filter { it.toString().takeLast(4) == ".csv" }
    csvs.dropWhile { it.split("/").last() != file }
    csvs.drop(0)

    val csize = chunkStartSize
    var cnum = 0
    val reader = File(file).inputStream().bufferedReader()
    val it = reader.lineSequence().iterator()
    it.skipWhile { !Regex(line!!).matches(it) }

    var written = line!!.split(" ")[0]
    while(it.hasNext()) {
        written += it.writeToFile(root + "owls/" + base + "%04d".format(cnum), csize)

        FileWriter(root + log, true).use {
            out -> out.appendln("$written lines done")
        }
        cnum++
    }

    mapFiles(root, csvs, base, folder!!)
    FileWriter(log, true).use { out -> out.appendln("(DONE $folder)") }

    return folders
}

fun main(args: Array<String>) {
    // anotando a pasta raiz
    println("Insira o endereço raiz para o mapeamento:")
    val location = readLine()!!
    val root = if(location.last() != '/') location.plus('/') else location

    // abrindo o log (novo ou já iniciado)
    var mode = "unknown"
    while (mode == "unknown"){ mode = openLog(root) }

    // descobrindo quais são as pastas
    val folders = if (mode == "resume") {
        resumeLastFolder(root)
    } else {
        startLog(root, mode)
        listFolders(root)
    }

    // executando mapeamento por pasta
    for(f in folders) {
        val log = "mapper.log"
        FileWriter(root + log, true).use { out -> out.appendln("(ENTERED $f)")}

        val csvs = File(f).list().filter { it.toString().takeLast(4) == ".csv" }
        val bios = File(f).list().filter { it.toString().takeLast(4) == ".bio" }

        // checando se o mapeamento é possível e não ambíguo
        when(bios.size) {
            0 -> println("Mapeamento não encontrado em $f")
            1 -> {
                // descobrindo o nome do arquivo a mapear (a BioDSL usa o nome do csv)
                val bio = FileReader(f + bios[0])
                val outputbase = bio.readUntil("\n").split("\"")[1].split(".")[0]
                bio.close()
                println("Os arquivos de saída são $outputbase[X].owl onde [X] é um número")

                FileWriter(root + log, true).use {
                    out -> out.appendln("(USING ${f + bios[0]})")
                }
                mapFiles(root, csvs, outputbase, f!!)
                FileWriter(log, true).use { out -> out.appendln("(DONE $f)") }
            }
            else -> {
                println("Há múltiplos mapeamentos na pasta $f")

                for (i in bios.indices) { println("\t $i ${bios[i]}") }
                println("Por favor, escolha um dos mapeamentos acima digitando seu número:")
                val opt = readLine()!!.split(" ")[0].toInt()
                println("Usando ${bios[opt]}")

                val bio = FileReader(f + bios[opt])
                val outputbase = bio.readUntil("\n").split("\"")[1].split(".")[0]
                bio.close()
                println("Os arquivos de saída são $outputbase[X].owl onde [X] é um número")

                FileWriter(root + log, true).use {
                    out -> out.appendln("(USING ${bios[opt]})")
                }
                mapFiles(root, csvs, outputbase, f!!)
                FileWriter(log, true).use { out -> out.appendln("(DONE $f)") }
            }
        }

        FileWriter(log, true).use { out -> out.appendln("(DONE $f)") }
    }
}

/* formato do log (plain text)
 * (ENTERED [directory]) ao entrar em um diretório
 * (USING [bio]) para declarar o nome do mapeamento usado
 * (RUNNING for [file]) ao começar a mapear um arquivo
 * (FETCHING [file]) ao começar a juntar as ontologias daquele arquivo
 * (DONE [directory]) ao terminar de rodar o mapeamento para todos os arquivos de um diretório
 *
 * as owls temporárias serão salvas em um diretório chamado tmpowls,
 * elas receberão o nome do arquivo de saída da bioDSL com uma numeração,
 * por fim a pasta temporária é eliminada
 *
 * a execução será feita por blocos nos dados,
 * haverá um print indicando de X em X linhas qual arquivo foi executado
 */

/* TODO:
    fazer as modificações necessárias para trabalhar com a nova biodsl
    modificar o script para detectar os arquivos de entrada de acordo com o nome no mapeamento
 */
