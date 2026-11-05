# 荣耀钱包 GPay 快捷按钮 · LSPosed 模块

把 [mipay_gpay_lsp](https://github.com/MasterOfStar/mipay_gpay_lsp) 那套「在刷卡页塞一颗 Google Pay 按钮」的思路搬到**荣耀钱包**：
双击电源键 → 荣耀钱包刷卡页 → 右下角多一颗 GPay 药丸按钮 → 点一下 = NFC 默认支付切到 Google + 拉起 Google Wallet 的 Quickdraw 刷卡页（锁屏下也直接出）。

- 产物：`dist/honor_wallet_gpay_lsp_1.1.0.apk`（37,533 字节 / 36.7 KiB，versionCode 6）— 正式版
- 签名：APK Signature Scheme **v3**（RSA-2048，minSdk 30，所以不需要 v1/v2）
- SHA-256：`6075e96f29c3cdb3a9814cc255f11caf0aab49264c609ac84c0d7f34be8c5a76`
- 目标：荣耀钱包 `com.hihonor.wallet` + 系统框架 `android`；Android 12+（MagicOS 7 以上）

---

## 0.1 更新日志

**1.1.0（2026-11-05）** — 正式版

- 🎚️ **调试提示收进一个总开关 `debug`（默认关）**：
  关着时点按钮**完全静默**（成功不弹任何 toast，只在失败时提示一句）；长按按钮无动作。
  想看诊断就在配置文件写 `debug=1`，点击会弹两步结果、长按可复制诊断。
- ✅ 锁屏路径也实机验证通过：锁屏 → 双击电源键 → 点按钮 → Google Wallet 直接出现，不弹密码。
- 🧾 三个 hook 点（注入 / 切 NFC + 拉起 / 跳过锁屏）全部实机验证。

**1.0.4（2026-11-05）** — 实机全链路打通后收口

- ✅ 实机（荣耀钱包 11.0.2.321）确认：`activity=com.hihonor.wallet.cardmanager.activity.SwipeCardActivity`，
  荣耀 HCE = `com.hihonor.wallet/com.hihonor.wallet.swipecard.NFCOffHostApduService`；
  点击后 `NFC → Google ✓`、`已拉起 Google Wallet ✓` 都通过。
- 🎯 白名单缩到两个：确认过的 `SwipeCardActivity` + 同族 `QuickSwipeCardActivity`（其它三个去掉，避免误注入）。
- 🔐 `honor_nfc_component` 有了实机默认值，读不到当前值时也能正确回切。

**1.0.3（2026-11-05）** — 按实机报告（荣耀钱包 11.0.2.321）收口

- 🎯 **刷卡页不再靠猜**：拿到真机 Activity 清单后，默认钉死到滑动刷卡那一族（见 §6 `DEFAULT_PINNED`），
  向导页 / 设置页不再误注入。`activity=` 支持逗号分隔多个类名。
- 🔐 **修「NFC 默认支付组件读取失败：SecurityException」**：钱包进程没有读 Secure 设置的权限，
  现在读也走模块 uid 的 root 通道（`settings get`），回切目标终于抓得到了；写同理由 root 代写。
- 🧵 读/写 Secure 设置、拉 root 都挪到后台线程，不再占用钱包主线程（进页面不掉帧）。
- 📋 长按按钮的诊断也改成后台取数据 + 主线程写剪贴板。

**1.0.2（2026-11-05）**

- 🔎 **不用 adb 也能看结果**：点按钮后直接弹两行结果 `NFC → Google ✓ / 拉起 Google Wallet ✓`（失败是 ✗），一眼知道断在哪步。
- 📋 **长按按钮 = 复制诊断**：把 `activity= / nfc= / 荣誉组件 / 模块版本` 复制到剪贴板，直接粘出来就能定位类名。

**1.0.1（2026-11-05）**

- 🐛 **修「白药丸」**：1.0.0 里字标取的是**钱包自己的 Resources**，而 `R.drawable.*` 是模块 APK 的资源 id，
  两边资源表对不上 → 取图失败 → 只剩一颗空白药丸（就是实机截图里那颗）。改成用模块自己的
  `createPackageContext()` 取图，并加了「取不到就画文字」的兜底。
- 🎨 按钮重做：白底药丸 + Google Pay 四色字标（深色页面对比度最好）、水波纹、按下 0.96 缩放、注入时 180ms 淡入放大；
  想要黑底写 `button_theme=dark`，或 `auto` 跟随系统深色（字标另有一套浅色版 `ic_gpay_wordmark_dark`）。
- 📐 默认尺寸 104×56 → **108×56 dp**。

**1.0.0**：首版，移植 mipay_gpay_lsp 的三段机制（注入按钮 / 切 NFC + 拉起 Wallet / 跳过锁屏）。

---

## 0. 验证到什么程度

本机没有荣耀设备，但**每一步都在用户实机（荣耀钱包 11.0.2.321 / MagicOS）上验证过了**：

| 项 | 状态 |
|---|---|
| 编译、打包、资源链接、DEX 生成 | ✅ 真跑通了（javac + aapt2 + d8） |
| 清单 / xposed_init / 作用域 / 签名 | ✅ `tools/check_apk.py` 自检通过，`apksigner verify` 报 `Verifies` |
| 真机注入 | ✅ 按钮出现在荣耀钱包刷卡页上 |
| 刷卡页类名 | ✅ 实机确认 `SwipeCardActivity`（257 个 Activity 里筛出来的） |
| 荣耀 HCE 组件 | ✅ `com.hihonor.wallet/com.hihonor.wallet.swipecard.NFCOffHostApduService` |
| 点按钮 → 切 NFC | ✅ 实机 toast `NFC → Google ✓`，长按诊断里 `nfc` 与 `honor` 同步正确 |
| 点按钮 → 拉起 Google Wallet | ✅ 实机 toast `已拉起 Google Wallet ✓` |
| 锁屏下跳过密码（system_server hook） | ✅ 实机验证：锁屏双击电源键 → 点按钮 → Wallet 直接出现，不弹密码 |

剩下唯一没实测的是「锁屏时双击电源键 → 点按钮」这条路径上的 `dismissKeyguard` 抑制。

---

## 1. 效果

![按钮效果](docs/button-preview.png)

_（按真实 pathData 渲染的预览：白底药丸 108×56dp + Google Pay 四色字标，落在荣耀钱包的深色刷卡页上。真机上还有阴影、按压缩放和点击水波纹。）_

```
┌──────────────────────────────┐
│  荣耀钱包 · 刷卡              │
│                              │
│   ┌────────────────────┐     │
│   │   银行卡 / 交通卡   │     │
│   └────────────────────┘     │
│                              │
│                  ┌────────┐  │
│                  │ GPay ▸ │  │ ← 注入的药丸按钮（白底 + 彩色字标）
│                  └────────┘  │
└──────────────────────────────┘
```

---

## 2. 和原版（MiPay）的对应关系

| 点 | 原版 mipay_gpay_lsp | 本版（荣耀钱包） |
|---|---|---|
| 目标应用 | `com.miui.tsmclient` | `com.hihonor.wallet` |
| Hook 目标 | 写死 `com.miui.tsmclient.ui.quick.DoubleClickActivity` | 不写死：hook `android.app.Activity` 的 `onCreate`/`onResume`，**给候选 Activity 打分**判断是不是刷卡页 |
| NFC 回切目标 | 写死 `com.android.nfc/...ESEWalletDummyService` | 第一次进刷卡页时把当前值**抓下来**当回切目标 |
| 写 `Settings.Secure` | 直接写（MiPay 是系统应用，有 `WRITE_SECURE_SETTINGS`） | 先直写；被拒就经模块自己的 root 通道 `settings put` |
| 起 Google Wallet | 先 root `am start`，失败再直连 | 同左 |
| 跳过锁屏 | hook `KeyguardController#dismissKeyguard`（写死签名） | 同思路，但**把目标类里所有带 `IBinder` 参数的 `dismissKeyguard` 全挂上**，运行时再判参数（Android 14/15 重载变了也不会失配） |
| 按钮渲染 | AndroidSVG + SVG 字符串 + Monet 取色 | **VectorDrawable** 资源（深浅两套），零第三方依赖；圆角药丸 + 阴影 + 水波纹 + 按压缩放 + 淡入 |
| 语言 | Kotlin | 纯 Java（这样没有 Gradle/Kotlin 也能直接 javac 编译） |
| 诊断 | 只能看 logcat | 新增**模块信息页**：钱包版本、当前 NFC 组件、候选刷卡页打分、一键复制报告 |
| 构建 | AGP + Kotlin + AndroidSVG（约 2.1 MB） | javac + aapt2 + d8 直接出包（**32.6 KB**） |

---

## 3. 安装

1. 安装 `dist/honor_wallet_gpay_lsp_1.0.0.apk`；
2. LSPosed 里启用本模块，作用域勾选 **荣耀钱包**（`com.hihonor.wallet`）和 **系统框架**（`android`）；
3. 强行停止荣耀钱包（或重启手机）让它重新加载；
4. 首次点按钮时会弹一次 **root 授权**——授权给「荣耀钱包 GPay」这个模块 App 本身（不是给钱包）。root 只用来干两件事：`am start` 拉起 Wallet、`settings put` 写 NFC 默认组件；
5. 双击电源键，看刷卡页右下角有没有按钮。

> 没有 root 也能用：按钮会退化成「直连 startActivity」，代价是可能被后台启动限制拦住、锁屏下也更容易被要求解锁。

---

## 4. 装完先做这两步

**① 打开模块图标 → 信息页**，确认：

- `目标应用 com.hihonor.wallet` 显示版本号（不是「未安装」）；
- `NFC 默认支付组件` 有值（读不到就说明模块还没拿到 root）；
- `调试提示` 一行显示「关」是正常的（正式版默认关）。

**② 双击电源键 → 刷卡页 → 点 G Pay**：成功时**什么都不弹**，Google Wallet 直接出来就是对的；
失败了才会有一句提示（`NFC 未切换：…` 或 `打不开 Google Wallet`）。

要排查才打开调试开关：配置文件写 `debug=1`，然后

| 方式 | 怎么做 |
|---|---|
| **点按钮看 toast** | 需 `debug=1`：点一下会弹两行 `NFC → Google ✓` / `已拉起 Google Wallet ✓`，失败是 ✗。哪行 ✗ 就是哪步断 |
| **长按按钮** | 需 `debug=1`：把 `activity=… / nfc=… / 模块版本` 复制到剪贴板，直接粘出来 |
| **LSPosed 管理器 → 日志** | 底部「日志」页，搜索 `HWG`，模块所有 `XposedBridge.log` 都在里面 |
| **文件** | `/sdcard/Documents/honor_wallet_gpay.log`（文件管理器直接打开；钱包没有存储权限时这个文件不会生成） |

要在电脑上看完整日志（Windows PowerShell，**没有 `grep`，用 `Select-String`**）：

```powershell
adb devices                                  # 先确认手机连上了（USB 调试）
adb logcat -s HWG                            # 只看模块日志
adb logcat -s HWG | Select-String "click|nfc|launch|am start"   # 只挑关键行
```

（`adb logcat -s HWG` 本身已经按 tag 过滤，管道只是二次筛选。）

正常加载会打印：

```
HWG/I: wallet: installing hooks for com.hihonor.wallet (conf=default)
HWG/I: wallet: 257 activities in com.hihonor.wallet
HWG/I: wallet: Activity.onResume com.hihonor.wallet.cardmanager.activity.SwipeCardActivity inject=true
HWG/I: wallet: button injected into com.hihonor.wallet.cardmanager.activity.SwipeCardActivity
```

---

## 5. 页面不对 / 按钮没出现？四种情况，都不用重装

模块支持一个**设备端配置文件**，改完强行停止荣耀钱包重开即可生效：

```sh
# 任选一个路径，能读到就行
/data/local/tmp/honor_wallet_gpay.conf
/sdcard/honor_wallet_gpay.conf
/sdcard/Documents/honor_wallet_gpay.conf
```

**情况 A：按钮出现在不该出现的页面 / 没出现在想要的那页** —— 直接写死类名（多个用逗号）：

```properties
activity=com.hihonor.wallet.cardmanager.activity.QuickSwipeCardActivity
```

默认白名单（实机长按确认，见 `ActivityProbe.DEFAULT_PINNED`）：

```
com.hihonor.wallet.cardmanager.activity.SwipeCardActivity            ← 实机确认的刷卡页
com.hihonor.wallet.cardmanager.activity.QuickSwipeCardActivity       ← 同族「快速刷卡」
```

**情况 B：日志里根本没有任何 `wallet:` 行** —— hook 没进钱包进程：检查作用域是否勾了荣耀钱包、有没有重启钱包、LSPosed 日志里有没有加载记录。

**情况 C：钱包更新后改了类名** —— 打开模块信息页，看新的候选列表，把 `activity=` 换掉；或者临时：

```properties
inject_all=1
```

（`inject_all=1` 时钱包里每一页都会有按钮，属于调试手段，不推荐长期开。）

**情况 D：按钮位置挡内容** —— 调边距：

```properties
button_margin_bottom_dp=160
button_margin_right_dp=16
button_width_dp=108
button_height_dp=56
```

---

## 6. 配置项全表

| key | 默认 | 说明 |
|---|---|---|
| `activity` | 见下 `DEFAULT_PINNED` | 指定要注入的页面，多个用**逗号**分隔；设了就完全覆盖白名单 |
| `activity_keywords` | 空 | 追加关键词（逗号分隔），命中 +100 分 |
| `inject_all` | `0` | `1` = 钱包里所有页面都注入（调试用） |
| `button_margin_bottom_dp` | `120` | 距底部 |
| `button_margin_right_dp` | `16` | 距右边 |
| `button_width_dp` / `button_height_dp` | `108` / `56` | 药丸尺寸 |
| `button_theme` | `light` | `light` 白底 / `dark` 黑底 / `auto` 跟随系统深色 |
| `honor_nfc_component` | 空 | 抓不到「荣耀自己的 NFC 组件」时的兜底值（`包名/服务名`） |
| `wallet_package` | `com.hihonor.wallet` | 目标钱包包名（荣耀以后改名可以改这里） |
| `gateway_package` | `com.google.android.apps.walletnfcrel` | Google Wallet 包名 |
| `use_root` | `1` | `0` = 完全不碰 root，只直连 |
| `debug` | `0` | `1` = 点击弹两步结果、长按复制诊断；`0` = 成功静默、长按无动作 |

**打分规则**（`ActivityProbe`）：类名含 `quickpay`/`doubleclick` +110~120，`quick` +60，`nfc` +40，`swipe` +90；含 `main`/`home`/`setting`/`webview` 之类 −40。≥60 分才注入。

---

## 7. 从源码构建

### 7.1 本机一键（不需要 Android Studio，不需要装 JDK）

```bat
python tools\fetch_toolchain.py    :: 下 JDK17 + build-tools 34 + platform 34 到 _toolchain（约 300 MB）
python tools\build.py              :: javac -> d8 -> aapt2 link -> zipalign -> apksigner
python tools\check_apk.py          :: 结构 / 清单 / xposed 声明 / 签名自检
```

`build.py` 干的事：资源 `aapt2 compile` → `aapt2 link`（顺带生成 `R.java`、注入 `package=`、打包 assets）→ `javac`（`-bootclasspath android.jar`，只依赖 `app/libs/xposed-api.jar`）→ `d8` → 把 `classes.dex` 塞进 APK → `zipalign` → `apksigner sign`。

签名密钥在 `keystore/honor-wallet-gpay.jks`（PKCS12，口令/别名都是 `honorwalletgpay`），**保留它**才能让后续版本覆盖安装；换了签名必须先卸载。

### 7.2 用 Android Studio / Gradle

根目录就是标准 Android 工程（AGP 8.5.2 / compileSdk 34 / JDK 17）：

```bat
gradlew assembleRelease
```

`app/build.gradle.kts` 里已经把 `keystore/honor-wallet-gpay.jks` 配成 release 签名。

> 说明：本机没有 Gradle / Android Studio，这条路径**没有实跑过**（7.1 那条是实跑出包的）；两者产物等价，只是构建器不同。

---

## 8. 目录

```
app/src/main/java/com/honorwallet/gpay/lsp/
  XposedInit.java            入口（assets/xposed_init 指向它）
  MainHook.java              按包名分发：钱包 / system_server
  WalletHooks.java           注入按钮、切 NFC、Activity 白名单
  ActivityProbe.java         刷卡页白名单 + 打分（也是信息页的数据源）
  NfcControl.java            读写默认 NFC 支付组件（直读 / root 代读代写 / 回切目标）
  RootBridge.java            钱包进程 → 模块进程的 root 通道客户端
  WalletLauncher.java        拉起 Google Wallet 的 Quickdraw
  RootCommandProvider.java   模块自己的 root 通道（content://…root）
  KeyguardHooks.java         system_server：跳过锁屏
  GpayButton.java            药丸按钮 View（VectorDrawable 字标 + 水波纹）
  InfoActivity.java          信息 / 诊断页
  Conf.java                  常量 + 配置文件解析
  DevLog.java / Shell.java / BuildInfo.java
app/src/main/res/drawable/ic_gpay_wordmark{,_dark}.xml   Google Pay 字标（深浅两套）
app/src/main/assets/xposed_init                  入口类名
app/src/main/assets/xposed_scope.list            默认作用域
app/libs/xposed-api.jar                          Xposed 编译期 API（compileOnly，不进 APK）
tools/    fetch_toolchain.py / build.py / check_apk.py / render-preview.js
docs/     button-preview.png（README 里的效果图）
keystore/ 签名密钥（**不入库**，本地生成/备份）
dist/     产物（**不入库**，走 GitHub Releases）
.github/workflows/build.yml                      CI：构建 + 自检 + tag 发版
```

---

## 9. 原理（三个 hook 点）

1. **钱包进程**：hook `android.app.Activity#onCreate/onResume` → 判断这一页是不是刷卡页 → 抓当前 NFC 组件当回切目标（`nfc_payment_default_component`）→ 往 `decorView` 加一颗 `GpayButton`。
2. **按钮点击**：`NfcControl` 把默认支付组件写成 `com.google.android.gms/...TpHceService`（直写被拒就走 `su -c settings put …`），然后 `WalletLauncher` 用 `su -c am start -a …QUICKDRAW -n …PresentationModeActivity` 起 Wallet（直连作兜底）。
3. **system_server**：`KeyguardController`/`ActivityTaskManagerService` 的 `dismissKeyguard` 里，如果 token 对应的 ActivityRecord 是 Google Wallet 的 Quickdraw 页 → 直接回调 `onDismissSucceeded` 并吞掉原逻辑，于是锁屏下不弹密码。

root 通道刻意做成「**模块 App 自己拿 root**」而不是钱包拿 root：`RootCommandProvider` 只接受荣耀钱包 uid 的调用，`am start` / `settings put` 在模块进程里执行。

---

## 10. 已知限制 / 风险

- **实测只覆盖一台机 + 一个钱包版本**（荣耀 Magic6 / 荣耀钱包 11.0.2.321）。别的 MagicOS 版本类名可能不同，
  信息页会把候选列出来，改 `activity=` 即可，不用等新版。
- **按钮位置是默认值**（右下、距底 120dp）：如果挡住卡片轮播，调 `button_margin_bottom_dp`。
- **NFC 切换是全局的**：点过按钮后默认支付应用会停在 Google，直到你下次回到荣耀钱包刷卡页（模块会自动切回）。
  这是原版同样的行为，不是 bug。
- **system_server hook 依赖 LSPosed 作用域**：没勾「系统框架」就只能在解锁状态下用。
- **root 只授给模块 App**（不是荣耀钱包），撤销后按钮会退化成直连方式，锁屏下可能又要解锁。
- **`QUERY_ALL_PACKAGES`**：信息页要枚举钱包的 Activity，模块是侧载的，没有应用商店审核问题。
- 本项目与荣耀、Google 均无关联，仅供学习交流；刷卡/支付行为请自行确认可用性。

---

## 11. 发布到 GitHub

仓库结构已经按开源准备好了：`.gitignore` 挡掉构建产物和签名密钥、CI 用同一条 `tools/build.py` 流水线出包、
tag 推送自动发 Release。

### 11.1 推上去

```bash
cd honor-wallet-gpay-lsp
git init
git add .
git status          # 确认没有 keystore/、_toolchain/、build/、dist/ 被加进来
git commit -m "Honor Wallet GPay LSP 1.1.0"
git branch -M main
git remote add origin git@github.com:<你的用户名>/honor-wallet-gpay-lsp.git
git push -u origin main
```

> 没有 git 也行：GitHub 网页「Add file → Upload files」把整个目录拖进去（`_toolchain/`、`build/`、`dist/`、`keystore/` 别拖）。

### 11.2 不要提交的东西

| 路径 | 原因 |
|---|---|
| `keystore/` | 你的签名私钥。泄露 = 别人能以你的名义给同一个包名发包 |
| `_toolchain/` | 300 MB 的 JDK/SDK，CI 会自己下 |
| `build/`、`dist/` | 构建产物；APK 走 Releases |
| `local.properties` | 各机器自己的 SDK 路径 |

**一定要备份 `keystore/honor-wallet-gpay.jks`**（口令 `honorwalletgpay`）。丢了就意味着以后的新版本
没法覆盖安装旧版本，用户必须先卸载。

### 11.3 CI

`.github/workflows/build.yml`：push / PR 自动跑 `fetch_toolchain → build → check_apk`，产物作为 artifact 上传；
推 `v1.1.0` 这种 tag 会自动创建 Release 并附上 APK。

想让 CI 出的包和本地包**同签名**（能互相覆盖安装）：把 keystore 转 base64 存进仓库 Secrets `KEYSTORE_BASE64`。

```bash
base64 -w0 keystore/honor-wallet-gpay.jks      # macOS 用 base64 -i 文件
```

（这是「仓库私有 + 只有自己发布」的前提；公开仓库里放签名密钥等于公开私钥，那就别配这个 secret，
让 CI 每次生成一次性密钥，只当「能编译通过」的验证。）

### 11.4 想被 LSPosed 模块仓库收录

原作者就是这么做的：fork [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo)，
按 `com.honorwallet.gpay.lsp` 建目录并提交本仓库的 `README.md`（含 `xposedmodule` 元数据的 APK 放 Release），
开 PR 审核通过后就有 [modules.lsposed.org](https://modules.lsposed.org) 的条目和「一键安装」了。

### 11.5 一点合规提醒

- 包名 `com.honorwallet.gpay.lsp` 里带 `honorwallet`，属于**描述性使用**；如果介意，可以改成
  `io.github.<你>.honorwalletgpay`（改包名 = 新 App，已装用户要卸载重装）。
- 没有打包任何荣耀/Google 的代码或资源，只做运行时 hook；Google Pay 字标是商标，仅用于标识被跳转的应用。
- README 里写明「与荣耀、Google 无关联」和「自担风险」，这两句别删。

---

## 12. 许可

MIT。按钮的 Google Pay 字标路径沿用原项目；NFC 切换、root 通道、跳过锁屏三段逻辑参考 `mipay_gpay_lsp`（MIT），已按荣耀的包名/组件名重写。
