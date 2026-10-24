"""ArkTS 工程静态自检。

本机没有 DevEco，无法真正编译，所以这里做四类可自动化的检查：
1. 相对 import 的路径是否存在（.ets/.ts）
2. import 里点名的符号，在目标文件里是否真的 export 了
3. 括号/引号是否配对
4. 已知的 ArkTS 严格模式写法问题（字面量数组给 ForEach、@Builder 链式调用等）
"""
import os
import re
import sys

ROOT = r"D:\毕业设计\hestia-app\entry\src\main\ets"

errors = []
warnings = []


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def collect_files():
    out = []
    for root, _, files in os.walk(ROOT):
        for name in files:
            if name.endswith(".ets"):
                out.append(os.path.join(root, name))
    return out


files = collect_files()
if not files:
    print("no .ets files found")
    sys.exit(1)

# 先建立每个文件的导出符号表
exports = {}
for path in files:
    content = read(path)
    names = set()
    # export interface X / export class X / export struct X / export function x
    for m in re.finditer(r"export\s+(?:default\s+)?(?:interface|class|struct|function|enum|const|let)\s+([A-Za-z_$][\w$]*)", content):
        names.add(m.group(1))
    # export { A, B }
    for m in re.finditer(r"export\s*\{([^}]*)\}", content):
        for part in m.group(1).split(","):
            token = part.strip().split(" as ")[-1].strip()
            if token:
                names.add(token)
    exports[path] = names

# 1 + 2：import 路径与符号
import_re = re.compile(r"import\s*\{([^}]*)\}\s*from\s*'([^']+)'")
for path in files:
    content = read(path)
    for m in import_re.finditer(content):
        symbols = [s.strip() for s in m.group(1).split(",") if s.strip()]
        rel = m.group(2)
        if not rel.startswith("."):
            continue  # 系统 kit，跳过
        target = os.path.normpath(os.path.join(os.path.dirname(path), rel))
        candidate = None
        for ext in (".ets", ".ts", ".d.ts"):
            if os.path.exists(target + ext):
                candidate = target + ext
                break
        if candidate is None:
            errors.append("MISSING FILE  %s -> %s" % (os.path.relpath(path, ROOT), rel))
            continue
        for sym in symbols:
            if sym not in exports.get(candidate, set()):
                errors.append("MISSING SYMBOL  %s : %s not exported by %s"
                              % (os.path.relpath(path, ROOT), sym, rel))

# 3：括号配对（忽略字符串与注释，粗略但够用）
for path in files:
    content = read(path)
    # 必须先抹掉字符串再抹注释，否则 'http://...' 里的 // 会被当成注释
    stripped = re.sub(r"`(?:\\.|[^`\\])*`", "``", content)
    stripped = re.sub(r"'(?:\\.|[^'\\])*'", "''", stripped)
    stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', stripped)
    stripped = re.sub(r"/\*.*?\*/", "", stripped, flags=re.S)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    for open_ch, close_ch in (("{", "}"), ("(", ")"), ("[", "]")):
        if stripped.count(open_ch) != stripped.count(close_ch):
            errors.append("BRACE MISMATCH  %s : %s=%d %s=%d"
                          % (os.path.relpath(path, ROOT), open_ch,
                             stripped.count(open_ch), close_ch, stripped.count(close_ch)))

# 4：已知的 ArkTS 坑
for path in files:
    content = read(path)
    rel = os.path.relpath(path, ROOT)
    if re.search(r"ForEach\(\s*\[", content):
        errors.append("ARKTS  ForEach with array literal  %s" % rel)
    if re.search(r"this\.\w+\([^)]*\)\s*\.\w+\(", content):
        warnings.append("CHECK  builder chained call?  %s" % rel)
    if "any" in content and re.search(r":\s*any\b", content):
        errors.append("ARKTS  explicit any type  %s" % rel)
    if re.search(r"\.\.\.\w", content):
        errors.append("ARKTS  spread operator  %s" % rel)

print("scanned %d .ets files" % len(files))
if errors:
    print("\n=== ERRORS (%d) ===" % len(errors))
    for e in errors:
        print("  " + e)
if warnings:
    print("\n=== WARNINGS (%d) ===" % len(warnings))
    for w in warnings:
        print("  " + w)
if not errors:
    print("\nno blocking issues found")
