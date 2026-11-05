# 荣耀钱包 GPay 快捷按钮 · LSPosed 模块
AI赋能把 [mipay_gpay_lsp](https://github.com/MasterOfStar/mipay_gpay_lsp) 那套「在刷卡页塞一颗 Google Pay 按钮」的思路搬到**荣耀钱包**：
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



