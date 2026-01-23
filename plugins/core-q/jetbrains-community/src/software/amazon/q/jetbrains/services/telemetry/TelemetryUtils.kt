// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.services.telemetry

val ALLOWED_CODE_EXTENSIONS = setOf(
    "abap",
    "ada",
    "adb",
    "ads",
    "apl",
    "asm",
    "awk",
    "b",
    "bas",
    "bash",
    "bat",
    "boo",
    "bms",
    "c",
    "cbl",
    "cc",
    "cfc",
    "cfg",
    "cfm",
    "cjs",
    "clj",
    "cljc",
    "cljs",
    "cls",
    "cmake",
    "cmd",
    "cob",
    "cobra",
    "coffee",
    "config",
    "cpp",
    "cpy",
    "cr",
    "cs",
    "css",
    "csx",
    "cxx",
    "d",
    "dart",
    "dfm",
    "dockerfile",
    "dpr",
    "e",
    "el",
    "elm",
    "env",
    "erl",
    "ex",
    "exs",
    "f",
    "f03",
    "f08",
    "f77",
    "f90",
    "f95",
    "flow",
    "for",
    "fs",
    "fsi",
    "fsx",
    "gd",
    "gitignore",
    "go",
    "gql",
    "gradle",
    "graphql",
    "groovy",
    "gs",
    "gsp",
    "gst",
    "gsx",
    "gvy",
    "h",
    "hack",
    "hh",
    "hpp",
    "hrl",
    "hs",
    "htm",
    "html",
    "hy",
    "idl",
    "ini",
    "io",
    "java",
    "jl",
    "js",
    "json",
    "jsx",
    "kt",
    "kts",
    "lean",
    "lgt",
    "lhs",
    "lisp",
    "lock",
    "logtalk",
    "lsp",
    "lua",
    "m",
    "ma",
    "mak",
    "makefile",
    "md",
    "mjs",
    "ml",
    "mli",
    "mpl",
    "ms",
    "mu",
    "mv",
    "n",
    "nb",
    "nim",
    "nix",
    "oot",
    "oz",
    "pas",
    "pasm",
    "perl",
    "php",
    "phtml",
    "pike",
    "pir",
    "pl",
    "pli",
    "pm",
    "pmod",
    "pp",
    "pro",
    "prolog",
    "properties",
    "ps1",
    "psd1",
    "psm1",
    "purs",
    "py",
    "pyw",
    "qs",
    "r",
    "raku",
    "rakumod",
    "rakutest",
    "rb",
    "rbw",
    "rdata",
    "re",
    "red",
    "reds",
    "res",
    "rex",
    "rexx",
    "ring",
    "rkt",
    "rktl",
    "rlib",
    "rm",
    "rmd",
    "roff",
    "ron",
    "rs",
    "ruby",
    "s",
    "sas",
    "sb",
    "sb2",
    "sb3",
    "sc",
    "scala",
    "scd",
    "scm",
    "scss",
    "sass",
    "sh",
    "shen",
    "sig",
    "sml",
    "sol",
    "sql",
    "ss",
    "st",
    "sv",
    "svg",
    "swift",
    "t",
    "tcl",
    "tf",
    "toml",
    "trigger",
    "ts",
    "tsx",
    "tu",
    "txt",
    "v",
    "vala",
    "vapi",
    "vb",
    "vba",
    "vbx",
    "vhd",
    "vhdl",
    "vue",
    "x",
    "xc",
    "xi",
    "xml",
    "yaml",
    "yml",
    "zig",
)

fun scrubNames(messageToBeScrubbed: String, username: String? = getSystemUserName()): String {
    var scrubbedMessage = ""
    var processedMessage = messageToBeScrubbed
    if (!username.isNullOrEmpty() && username.length > 2) {
        processedMessage = processedMessage.replace(username, "x")
    }

    // Replace contiguous whitespace with 1 space.
    processedMessage = processedMessage.replace(Regex("""\s+"""), " ")

    // 1. split on whitespace.
    // 2. scrub words that match username or look like filepaths.
    val words = processedMessage.split(Regex("""\s+"""))
    for (word in words) {
        val pathSegments = word.split(Regex("""[/\\]""")).filter { it != "" }
        if (pathSegments.size < 2) {
            // Not a filepath.
            scrubbedMessage += " $word"
            continue
        }

        // Replace all (non-allowlisted) ASCII filepath segments with "x".
        // "/foo/bar/aws/sso/" => "/x/x/aws/sso/"
        var scrubbed = ""
        // Get the frontmatter ("/", "../", "~/", or "./").
        val start = slashdot.find(word.trimStart())?.value.orEmpty()
        val firstVal = pathSegments[0].trimStart().replace(slashdot, "")

        val ps = pathSegments.filterIndexed { i, _ -> i != 0 }.toMutableList()
        ps.add(0, firstVal)

        for (seg in ps) {
            when {
                driveLetterRegex.matches(seg) -> scrubbed += seg
                commonFilePathPatterns.contains(seg) -> scrubbed += "/$seg"
                else -> {
                    // Save the first non-ASCII (unicode) char, if any.
                    val nonAscii = Regex("""[^\p{ASCII}]""").find(seg)?.value.orEmpty()
                    // Replace all chars (except [^…]) with "x" .
                    val ascii = seg.replace(Regex("""[^$\[\](){}:;'" ]+"""), "x")
                    scrubbed += "/${ascii}$nonAscii"
                }
            }
        }

        // includes leading '.', eg: '.json'
        val fileExt = fileExtRegex.find(pathSegments.last())?.value.orEmpty()
        val newString = " ${start.replace(Regex("""\\"""), "/")}${scrubbed.removePrefix("//").removePrefix("/").removePrefix("\\")}$fileExt"
        scrubbedMessage += newString
    }

    return scrubbedMessage.trim()
}

val fileExtRegex = Regex("""\.[^./]+$""")
val slashdot = Regex("""^[~.]*[/\\]*""")

/** Allowlisted filepath segments. */
val commonFilePathPatterns = setOf(
    "~", ".", "..", ".aws", "aws", "sso", "cache", "credentials", "config",
    "Users", "users", "home", "tmp", "aws-toolkit-jetbrains"
)
val driveLetterRegex = Regex("""^[a-zA-Z]:""")

fun getSystemUserName(): String? = System.getProperty("user.name") ?: null
