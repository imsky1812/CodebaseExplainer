package com.codeexplainer;

import com.codeexplainer.engine.CodeParser;
import com.codeexplainer.model.ParsedFile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the code parser engine.
 */
class CodeParserTest {

    private final CodeParser parser = new CodeParser();

    // ── Language detection ─────────────────────────────────────────────

    @Test
    void testDetectPython() {
        assertEquals("python", parser.detectLanguage("main.py"));
        assertEquals("python", parser.detectLanguage("src/utils.py"));
    }

    @Test
    void testDetectJavaScript() {
        assertEquals("javascript", parser.detectLanguage("index.js"));
        assertEquals("javascript", parser.detectLanguage("src/App.jsx"));
    }

    @Test
    void testDetectTypeScript() {
        assertEquals("typescript", parser.detectLanguage("index.ts"));
        assertEquals("typescript", parser.detectLanguage("App.tsx"));
    }

    @Test
    void testDetectJava() {
        assertEquals("java", parser.detectLanguage("Main.java"));
    }

    @Test
    void testDetectUnknown() {
        assertEquals("unknown", parser.detectLanguage("readme.md"));
        assertEquals("unknown", parser.detectLanguage("Dockerfile"));
    }

    // ── Python parsing ────────────────────────────────────────────────

    @Test
    void testPythonImports() {
        String content = """
                import os
                import sys
                from pathlib import Path
                from . import utils
                from ..helpers import format_output
                
                def main():
                    pass
                
                class MyClass:
                    pass
                """;

        ParsedFile result = parser.parseFile("test.py", content);
        assertEquals("python", result.getLanguage());
        assertTrue(result.getImports().contains("os"));
        assertTrue(result.getImports().contains("sys"));
        assertTrue(result.getImports().contains("pathlib"));
        assertTrue(result.getDeclarations().contains("main"));
        assertTrue(result.getDeclarations().contains("MyClass"));
    }

    @Test
    void testPythonEmptyFile() {
        ParsedFile result = parser.parseFile("empty.py", "");
        assertEquals("python", result.getLanguage());
        assertTrue(result.getImports().isEmpty());
        assertTrue(result.getDeclarations().isEmpty());
    }

    @Test
    void testPythonExports() {
        String content = """
                def public_function():
                    pass
                
                def _private_function():
                    pass
                
                class PublicClass:
                    pass
                """;

        ParsedFile result = parser.parseFile("module.py", content);
        assertTrue(result.getExports().contains("public_function"));
        assertTrue(result.getExports().contains("PublicClass"));
        assertFalse(result.getExports().contains("_private_function"));
    }

    // ── JavaScript parsing ────────────────────────────────────────────

    @Test
    void testJavaScriptImports() {
        String content = """
                import React from 'react';
                import { useState, useEffect } from 'react';
                import './styles.css';
                const axios = require('axios');
                
                export function App() {
                    return null;
                }
                """;

        ParsedFile result = parser.parseFile("App.js", content);
        assertEquals("javascript", result.getLanguage());
        assertTrue(result.getImports().contains("react"));
        assertTrue(result.getImports().contains("axios"));
    }

    // ── Java parsing ──────────────────────────────────────────────────

    @Test
    void testJavaImports() {
        String content = """
                import java.util.List;
                import java.io.File;
                
                public class Main {
                    public static void main(String[] args) {
                    }
                }
                """;

        ParsedFile result = parser.parseFile("Main.java", content);
        assertEquals("java", result.getLanguage());
        assertTrue(result.getImports().contains("java.util.List"));
        assertTrue(result.getDeclarations().contains("Main"));
    }

    // ── C parsing ─────────────────────────────────────────────────────

    @Test
    void testCIncludes() {
        String content = """
                #include <stdio.h>
                #include "utils.h"
                """;

        ParsedFile result = parser.parseFile("main.c", content);
        assertEquals("c", result.getLanguage());
        assertTrue(result.getImports().contains("stdio.h"));
        assertTrue(result.getImports().contains("utils.h"));
    }

    // ── Rust parsing ──────────────────────────────────────────────────

    @Test
    void testRustUse() {
        String content = """
                use std::io;
                use crate::utils;
                
                fn main() {
                    println!("Hello");
                }
                """;

        ParsedFile result = parser.parseFile("main.rs", content);
        assertEquals("rust", result.getLanguage());
        assertTrue(result.getImports().contains("std::io"));
        assertTrue(result.getDeclarations().contains("main"));
    }
}
