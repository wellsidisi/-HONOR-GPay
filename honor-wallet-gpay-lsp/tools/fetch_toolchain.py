"""下载最小可用的 Android 打包工具链：JDK 17 + build-tools 34 + platform 34。

只为了在没有 Android Studio / Gradle 的机器上直接出 APK（Windows / Linux / macOS）。

    python tools/fetch_toolchain.py

每个包都配了镜像（国内源优先），失败自动换下一个；已下载就直接跳过。
装完约 1 GB 解压空间，全部落在 _toolchain/（已在 .gitignore 里）。
"""

import os
import ssl
import sys
import tarfile
import time
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.path.join(ROOT, "_toolchain")

GOOGLE = "https://dl.google.com/android/repository/"
TENCENT = "https://mirrors.cloud.tencent.com/AndroidSDK/"

BUILD_TOOLS = {
    "win32": ("build-tools_r34-windows.zip", 58253258),
    "linux": ("build-tools_r34-linux.zip", 61224257),
    "darwin": ("build-tools_r34-macosx.zip", 76559180),
}

JDK_URLS = {
    "win32": [
        "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip",
        "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk",
    ],
    "linux": [
        "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk",
        "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_linux-x64_bin.tar.gz",
    ],
    "darwin": [
        "https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jdk/hotspot/normal/eclipse?project=jdk",
        "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_macos-x64_bin.tar.gz",
    ],
}

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


def platform_key():
    if sys.platform.startswith("win"):
        return "win32"
    if sys.platform == "darwin":
        return "darwin"
    return "linux"


def items():
    key = platform_key()
    bt_name, bt_size = BUILD_TOOLS[key]
    # 国内机器腾讯镜像快，CI（海外）走 Google 更稳
    bt_urls = ([TENCENT + bt_name, GOOGLE + bt_name] if key == "win32"
               else [GOOGLE + bt_name, TENCENT + bt_name])
    pf_urls = ([TENCENT + "platform-34-ext7_r03.zip", GOOGLE + "platform-34-ext7_r03.zip"]
               if key == "win32" else
               [GOOGLE + "platform-34-ext7_r03.zip", TENCENT + "platform-34-ext7_r03.zip"])
    return [
        {"dir": "build-tools", "size": bt_size, "urls": bt_urls},
        {"dir": "platform", "size": 63180081, "urls": pf_urls},
        {"dir": "jdk", "size": None, "urls": JDK_URLS[key]},
    ]


def download(item):
    """下载到 _toolchain/，返回本地文件路径；已有就复用。"""
    name = os.path.basename(item["dir"])
    for suffix in (".zip", ".tar.gz"):
        existing = os.path.join(TC, name + suffix)
        if os.path.exists(existing):
            print("[skip] %s" % os.path.basename(existing), flush=True)
            return existing
    for url in item["urls"]:
        suffix = ".tar.gz" if url.endswith(".tar.gz") else ".zip"
        tmp = os.path.join(TC, name + suffix + ".part")
        dest = os.path.join(TC, name + suffix)
        t0 = time.time()
        got = 0
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=60, context=ctx) as r, open(tmp, "wb") as f:
                total = int(r.headers.get("Content-Length") or 0)
                print("[get ] %s <- %s (%.1f MB)"
                      % (os.path.basename(dest), url, total / 1048576), flush=True)
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    f.write(chunk)
                    got += len(chunk)
                    if got % (16 << 20) < (1 << 20):
                        print("       %.0f/%.0f MB  %.1f MB/s"
                              % (got / 1048576, total / 1048576,
                                 got / 1048576 / max(time.time() - t0, .1)), flush=True)
            size = os.path.getsize(tmp)
            if item["size"] is not None and size != item["size"]:
                print("[warn] 大小不对: %d != %d" % (size, item["size"]), flush=True)
                raise IOError("size mismatch")
            os.replace(tmp, dest)
            print("[ok  ] %s %.1f MB in %.0fs"
                  % (os.path.basename(dest), size / 1048576, time.time() - t0), flush=True)
            return dest
        except Exception as e:  # noqa: BLE001
            print("[warn] 失败: %s: %s" % (type(e).__name__, e), flush=True)
            if os.path.exists(tmp):
                os.remove(tmp)
    return None


def extract(archive, outdir):
    if os.path.isdir(outdir) and os.listdir(outdir):
        print("[skip] extract %s" % os.path.basename(archive), flush=True)
        return
    os.makedirs(outdir, exist_ok=True)
    if archive.endswith(".tar.gz"):
        with tarfile.open(archive) as t:
            t.extractall(outdir)
    else:
        with zipfile.ZipFile(archive) as z:
            z.extractall(outdir)
    print("[ok  ] extract %s -> %s" % (os.path.basename(archive), outdir), flush=True)


def main():
    os.makedirs(TC, exist_ok=True)
    for item in items():
        archive = download(item)
        if archive is None:
            sys.exit("下载失败: " + item["dir"])
        extract(archive, os.path.join(TC, item["dir"]))
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
