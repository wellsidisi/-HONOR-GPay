"""出厂自检：APK 结构、清单、xposed 声明、签名。

    python tools/check_apk.py [dist/xxx.apk]
"""

import glob
import os
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.path.join(ROOT, "_toolchain")

EXE = ".exe" if os.name == "nt" else ""
AAPT2 = "aapt2" + EXE
APKSIGNER = "apksigner.bat" if os.name == "nt" else "apksigner"
JAVAC = "javac" + EXE

REQUIRED = [
    "AndroidManifest.xml",
    "classes.dex",
    "resources.arsc",
    "assets/xposed_init",
    "assets/xposed_scope.list",
]

MANIFEST_EXPECT = [
    "xposedmodule",
    "xposedmodname",
    "xposeddescription",
    "xposedminversion",
    "com.honorwallet.gpay.lsp.root",
    "com.hihonor.wallet",
    "com.honorwallet.gpay.lsp.InfoActivity",
    "com.honorwallet.gpay.lsp.RootCommandProvider",
]


def find(pattern):
    hits = glob.glob(os.path.join(TC, "**", pattern), recursive=True)
    return sorted(hits, key=len)[0] if hits else None


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else None
    if not apk:
        cands = sorted(glob.glob(os.path.join(ROOT, "dist", "*.apk")), key=os.path.getmtime)
        if not cands:
            sys.exit("dist/ 里没有 APK")
        apk = cands[-1]
    print("APK:", apk)
    print("大小: %.1f KiB" % (os.path.getsize(apk) / 1024.0))

    fail = []

    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        for r in REQUIRED:
            if r not in names:
                fail.append("缺少条目 " + r)
        for n in names:
            if n.startswith("META-INF/") and n.count("/") == 1:
                print("签名条目:", n)
        print("条目数:", len(names))
        print("xposed_init ->", z.read("assets/xposed_init").decode().strip())
        print("xposed_scope ->", z.read("assets/xposed_scope.list").decode().split())

    aapt2 = find(AAPT2)
    if aapt2:
        out = subprocess.run([aapt2, "dump", "badging", apk],
                             capture_output=True, text=True, encoding="utf-8", errors="replace")
        text = (out.stdout or "") + (out.stderr or "")
        print("\n--- aapt2 dump badging ---")
        print("\n".join(text.splitlines()[:12]))
        xml = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                             capture_output=True, text=True, encoding="utf-8", errors="replace")
        dump = (xml.stdout or "") + (xml.stderr or "")
        for key in MANIFEST_EXPECT:
            if key not in dump:
                fail.append("清单里没有 " + key)
        for kw in ("package=", "minSdkVersion", "targetSdkVersion"):
            if kw not in dump and kw not in text:
                fail.append("清单里没有 " + kw)

    apksigner = find(APKSIGNER)
    java_home = find(JAVAC)
    if apksigner and java_home:
        env = dict(os.environ)
        env["JAVA_HOME"] = os.path.dirname(os.path.dirname(java_home))
        out = subprocess.run([apksigner, "verify", "--verbose", "--print-certs", apk],
                             capture_output=True, text=True, env=env)
        print("\n--- apksigner verify ---")
        print((out.stdout or "") + (out.stderr or ""))
        if out.returncode != 0:
            fail.append("签名校验失败")
        if "Verifies" not in (out.stdout or ""):
            fail.append("apksigner 未报告 Verifies")

    if fail:
        print("\nFAIL:")
        for f in fail:
            print(" -", f)
        sys.exit(1)
    print("\nOK: 结构与签名自检通过")


if __name__ == "__main__":
    main()
