from engine.code_parser import CodeParser

parser = CodeParser()

# ── Language detection ─────────────────────────────────────────────

def test_detect_python():
    assert parser.detect_language("main.py") == "python"
    assert parser.detect_language("src/utils.py") == "python"

def test_detect_javascript():
    assert parser.detect_language("index.js") == "javascript"
    assert parser.detect_language("src/App.jsx") == "javascript"

def test_detect_typescript():
    assert parser.detect_language("index.ts") == "typescript"
    assert parser.detect_language("App.tsx") == "typescript"

def test_detect_java():
    assert parser.detect_language("Main.java") == "java"

def test_detect_unknown():
    assert parser.detect_language("readme.md") == "unknown"
    assert parser.detect_language("Dockerfile") == "unknown"

# ── Python parsing ────────────────────────────────────────────────

def test_python_imports():
    content = """import os
import sys
from pathlib import Path
from . import utils
from ..helpers import format_output

def main():
    pass

class MyClass:
    pass
"""

    result = parser.parse_file("test.py", content)
    assert result.language == "python"
    assert "os" in result.imports
    assert "sys" in result.imports
    assert "pathlib" in result.imports
    assert "main" in result.declarations
    assert "MyClass" in result.declarations

def test_python_empty_file():
    result = parser.parse_file("empty.py", "")
    assert result.language == "python"
    assert len(result.imports) == 0
    assert len(result.declarations) == 0

def test_python_exports():
    content = """def public_function():
    pass

def _private_function():
    pass

class PublicClass:
    pass
"""

    result = parser.parse_file("module.py", content)
    assert "public_function" in result.exports
    assert "PublicClass" in result.exports
    assert "_private_function" not in result.exports

# ── JavaScript parsing ────────────────────────────────────────────

def test_javascript_imports():
    content = """import React from 'react';
import { useState, useEffect } from 'react';
import './styles.css';
const axios = require('axios');

export function App() {
    return null;
}
"""

    result = parser.parse_file("App.js", content)
    assert result.language == "javascript"
    assert "react" in result.imports
    assert "axios" in result.imports

# ── Java parsing ──────────────────────────────────────────────────

def test_java_imports():
    content = """import java.util.List;
import java.io.File;

public class Main {
    public static void main(String[] args) {
    }
}
"""

    result = parser.parse_file("Main.java", content)
    assert result.language == "java"
    assert "java.util.List" in result.imports
    assert "Main" in result.declarations

# ── C parsing ─────────────────────────────────────────────────────

def test_c_includes():
    content = """#include <stdio.h>
#include "utils.h"
"""

    result = parser.parse_file("main.c", content)
    assert result.language == "c"
    assert "stdio.h" in result.imports
    assert "utils.h" in result.imports

# ── Rust parsing ──────────────────────────────────────────────────

def test_rust_use():
    content = """use std::io;
use crate::utils;

fn main() {
    println!("Hello");
}
"""

    result = parser.parse_file("main.rs", content)
    assert result.language == "rust"
    assert "std::io" in result.imports
    assert "main" in result.declarations
