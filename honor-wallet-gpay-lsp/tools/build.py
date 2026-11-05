"""用「javac + aapt2 + d8 + apksigner」直接出 APK，不需要 Android Studio / Gradle。

    python tools/build.py            # 出包到 dist/
    python tools/build.py --clean    # 先清掉 build/

依赖 _toolchain/ 里的 JDK + build-tools + platform（由 tools/fetch_toolchain.py 下载）。
Windows / Linux / macOS 都能跑；CI 里同样用这条流水线。
"""

import glob
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")
BUILD = os.path.join(ROOT, "build")
DIST = os.path.join(ROOT, "dist")
TC = os.path.join(ROOT, "_toolchain")

IS_WIN = os.name == "nt"
EXE = ".exe" if IS_WIN else ""

PKG = "com.honorwallet.gpay.lsp"
MIN_SDK = "30"
TARGET_SDK = "34"

KS = os.path.join(ROOT, "keystore", "honor-wallet-gpay.jks")
KS_PASS = "honorwalletgpay"
KS_ALIAS = "honorwalletgpay"


def read_version():
    """版本号只有一个来源：BuildInfo.java。"""
    src = os.path.join(APP, "java", "com", "honorwallet", "gpay", "lsp", "BuildInfo.java")
    text = open(src, encoding="utf-8").read()
    name = re.search(r'VERSION_NAME\s*=\s*"([^"]+)"', text).group(1)
    code = re.search(r"VERSION_CODE\s*=\s*(\d+)", text).group(1)
    return name, code


VERSION_NAME, VERSION_CODE = read_version()


def find_file(root, name, depth=3):
    hits = glob.glob(os.path.join(root, "**", name), recursive=True)
    if not hits:
        return None
    hits.sort(key=lambda p: (p.count(os.sep), len(p)))
    return hits[0]


def toolchain():
    jdk = os.path.join(TC, "jdk")
    bt = os.path.join(TC, "build-tools")
    pf = os.path.join(TC, "platform")
    tools = {
        "javac": find_file(jdk, "javac" + EXE),
        "java": find_file(jdk, "java" + EXE),
        "keytool": find_file(jdk, "keytool" + EXE),
        "aapt2": find_file(bt, "aapt2" + EXE),
        "d8": find_file(bt, "d8" + EXE) if not IS_WIN else find_file(bt, "d8.bat"),
        "zipalign": find_file(bt, "zipalign" + EXE),
        "apksigner": find_file(bt, "apksigner" + EXE) if not IS_WIN else find_file(bt, "apksigner.bat"),
        "android_jar": find_file(pf, "android.jar"),
    }
    missing = [k for k, v in tools.items() if not v]
    if missing:
        sys.exit("工具链缺失: %s\n先跑 python tools/fetch_toolchain.py" % ", ".join(missing))
    return tools


def run(cmd, **kw):
    shown = " ".join(str(c) for c in cmd)
    print("$", shown if len(shown) < 220 else shown[:220] + " …", flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    out = (r.stdout or "") + (r.stderr or "")
    if out.strip():
        print(out.strip()[-4000:], flush=True)
    if r.returncode != 0:
        sys.exit("失败 (exit=%d): %s" % (r.returncode, shown))
    return out


def prepare():
    if "--clean" in sys.argv and os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    for d in (BUILD, DIST):
        os.makedirs(d, exist_ok=True)
    for d in ("gen", "classes", "dex"):
        p = os.path.join(BUILD, d)
        if os.path.isdir(p):
            shutil.rmtree(p)
        os.makedirs(p)


def manifest_with_package():
    src = os.path.join(APP, "AndroidManifest.xml")
    text = open(src, encoding="utf-8").read()
    if "package=" in text.split(">", 1)[0]:
        return src
    injected = re.sub(r"<manifest\b", '<manifest package="%s"' % PKG, text, count=1)
    out = os.path.join(BUILD, "AndroidManifest.xml")
    with open(out, "w", encoding="utf-8") as f:
        f.write(injected)
    return out


def ensure_keystore(tc):
    """本地没有签名密钥就生成一个（不入库）。换机器/换密钥后必须卸载重装。"""
    if os.path.exists(KS):
        return
    os.makedirs(os.path.dirname(KS), exist_ok=True)
    run([tc["keytool"], "-genkeypair", "-keystore", KS, "-storetype", "PKCS12",
         "-alias", KS_ALIAS, "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
         "-storepass", KS_PASS, "-keypass", KS_PASS,
         "-dname", "CN=Honor Wallet GPay LSP, O=local, C=CN"])


def main():
    tc = toolchain()
    env = dict(os.environ)
    env["JAVA_HOME"] = os.path.dirname(os.path.dirname(tc["java"]))
    prepare()

    # 1) 资源 -> 扁平包
    res_zip = os.path.join(BUILD, "res.zip")
    run([tc["aapt2"], "compile", "--dir", os.path.join(APP, "res"), "-o", res_zip])

    # 2) 资源链接 + 生成 R.java + 打包 manifest/assets
    base_apk = os.path.join(BUILD, "base.apk")
    run([tc["aapt2"], "link", "-o", base_apk,
         "-I", tc["android_jar"],
         "--manifest", manifest_with_package(),
         "-R", res_zip,
         "--java", os.path.join(BUILD, "gen"),
         "-A", os.path.join(APP, "assets"),
         "--min-sdk-version", MIN_SDK,
         "--target-sdk-version", TARGET_SDK,
         "--version-code", VERSION_CODE,
         "--version-name", VERSION_NAME,
         "--auto-add-overlay"])

    # 3) javac（bootclasspath 用 android.jar，xposed api 只参与编译）
    sources = glob.glob(os.path.join(APP, "java", "**", "*.java"), recursive=True)
    sources += glob.glob(os.path.join(BUILD, "gen", "**", "*.java"), recursive=True)
    run([tc["javac"], "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-Xlint:-options",
         "-bootclasspath", tc["android_jar"],
         "-classpath", os.path.join(ROOT, "app", "libs", "xposed-api.jar"),
         "-d", os.path.join(BUILD, "classes")] + sources)

    # 4) d8 -> classes.dex
    classes = glob.glob(os.path.join(BUILD, "classes", "**", "*.class"), recursive=True)
    run([tc["d8"], "--release", "--min-api", MIN_SDK,
         "--lib", tc["android_jar"],
         "--output", os.path.join(BUILD, "dex")] + classes, env=env)
    dex = os.path.join(BUILD, "dex", "classes.dex")
    if not os.path.exists(dex):
        sys.exit("d8 没产出 classes.dex")

    # 5) 把 dex 塞进 aapt2 的 apk
    unsigned = os.path.join(BUILD, "unsigned.apk")
    shutil.copyfile(base_apk, unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, "classes.dex")

    # 6) 对齐 + 签名
    aligned = os.path.join(BUILD, "aligned.apk")
    run([tc["zipalign"], "-f", "-p", "4", unsigned, aligned])
    out_apk = os.path.join(DIST, "honor_wallet_gpay_lsp_%s.apk" % VERSION_NAME)
    if os.environ.get("UNSIGNED") == "1":
        shutil.copyfile(aligned, out_apk)
        print("\nUNSIGNED APK: %s (%.1f KiB)" % (out_apk, os.path.getsize(out_apk) / 1024.0))
        return
    ensure_keystore(tc)
    run([tc["apksigner"], "sign",
         "--ks", KS, "--ks-pass", "pass:" + KS_PASS,
         "--ks-key-alias", KS_ALIAS, "--key-pass", "pass:" + KS_PASS,
         "--min-sdk-version", MIN_SDK,
         "--v1-signing-enabled", "false",
         "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true",
         "--out", out_apk, aligned], env=env)
    run([tc["apksigner"], "verify", "--print-certs", out_apk], env=env)

    print("\nAPK: %s  (%.1f KiB)" % (out_apk, os.path.getsize(out_apk) / 1024.0), flush=True)


if __name__ == "__main__":
    main()
