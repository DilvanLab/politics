import java.io.File

fun minify(root: String, dest: String = root + "_min/") {
    val directories = listFolders(root)
    val files = mutableListOf<String>()

    directories.forEach { dir ->
        File(dir).list().forEach { f ->
            val path = dir + f
            if (File(path).isFile) files.add(path)
        }
    }

    if (!File(dest).isFile)
        File(dest).mkdirs()

    files.filter { it.toString().takeLast(4) == ".txt" }.forEach { csv ->
        File(csv).inputStream().bufferedReader().use { reader ->
            val lines = reader.lineSequence().iterator()
            val name = csv.split("/").last().split(".")
            val extension = name.get(name.lastIndex)
            val copy = dest + name.dropLast(1).joinToString(".") + ".min." + extension
            val mini = File(copy).printWriter()
            var counter = 0
            while (lines.hasNext() && counter < 100) {
                mini.println(lines.next())
                counter++
            }
            mini.close()
        }
    }
}

fun main(args: Array<String>) {
    minify("/home/asdomingues/Documentos/pub/owls/")
}

/* criei esse arquivo para gerar vários mapeamentos pequenos para teste
    há problemas de encoding
 */