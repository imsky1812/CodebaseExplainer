package com.codeexplainer.model;

import java.util.List;

/**
 * Represents a parsed source file with extracted imports, exports, and declarations.
 */
public class ParsedFile {
    private String filename;
    private String language;
    private List<String> imports;
    private List<String> exports;
    private List<String> declarations;

    public ParsedFile() {}

    public ParsedFile(String filename, String language, List<String> imports,
                      List<String> exports, List<String> declarations) {
        this.filename = filename;
        this.language = language;
        this.imports = imports;
        this.exports = exports;
        this.declarations = declarations;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public List<String> getImports() { return imports; }
    public void setImports(List<String> imports) { this.imports = imports; }

    public List<String> getExports() { return exports; }
    public void setExports(List<String> exports) { this.exports = exports; }

    public List<String> getDeclarations() { return declarations; }
    public void setDeclarations(List<String> declarations) { this.declarations = declarations; }
}
