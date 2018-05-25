import java.io.File

class Dossier(path: String) {
    private val type = if (File(path).isFile) "file" else "folder"

    val path = (if (path.split('/').first() !in listOf(".", "~", "..")) "/" else "") +
            path.split("/").filter { it != "" }.dropLast(1).joinToString("/") + '/'
    val name =
            if (this.type == "file") {
                path.split("/").last().split(".").dropLast(1).joinToString(".")
            } else {
                path.split("/").last { it != "" }
            }

    private val extension = if (this.type == "folder") "/" else '.' + path.split(".").last()

    fun fullName(): String {
        return this.name + this.extension
    }

    fun fullPath(): String {
        return this.path + this.name + this.extension
    }
}

/*
fun main(args: Array<String>) {
    var f = Dossier("/home/asdomingues/Documentos/pub/")
    println(f.fullPath())

    f = Dossier("/home/asdomingues/Documentos/pub")
    println(f.fullPath())

    f = Dossier("/home/asdomingues/Documentos/pub/politics.owl")
    println(f.fullPath())

    f = Dossier("/home/asdomingues/Documentos/politics.owl")
    println(f.fullPath())
}
*/