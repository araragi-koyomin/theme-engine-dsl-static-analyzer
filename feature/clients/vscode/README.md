# DSL Analyzer 鈥?VS Code 瀹㈡埛绔?
> 妯″潡璺緞锛歚feature/clients/vscode`
> 瀵瑰簲 server 妯″潡锛歚:feature:lsp`锛坒at jar `dsl-analyzer-lsp.jar`锛?
鏈枃妗ｈ鏄庡浣曟瀯寤恒€佸畨瑁呫€侀厤缃?VS Code 鎵╁睍锛屼娇鍏堕€氳繃 LSP 鎺ュ叆 Theme Engine DSL 闈欐€佸垎鏋?server銆傛墿灞曞熀浜?[`vscode-languageclient`](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide) 瀹炵幇锛屽惎鍔?`java -jar dsl-analyzer-lsp.jar --stdio` 骞舵妸璇婃柇/琛ュ叏/hover/codeAction/璇箟楂樹寒妗ユ帴鍒?VS Code銆?
---

## 1. 鍓嶇疆瑕佹眰

- **Node.js 鈮?18** 涓?**npm**锛堢敤浜庣紪璇?鎵撳寘鎵╁睍锛涙瀯寤?host 闇€鍦?PATH 涓彲鎵ц `npm`锛夈€?- **JDK 17+**锛堣繍琛?server jar锛涙墿灞曢粯璁ょ敤 `java`锛屽彲鍦ㄨ缃腑瑕嗙洊锛夈€?- **Gradle 8.2**锛堥」鐩瀯寤猴紝瑙佹牴 `AGENTS.md`锛夈€?
---

## 2. 涓€閿瀯寤猴紙鎺ㄨ崘锛?
鏋勫缓浼氾細鈶?缂栬瘧 LSP server fat jar锛涒憽 鎶?jar 澶嶅埗鍒版墿灞曠殑 `server/` 鐩綍锛涒憿 `npm ci` 瀹夎渚濊禆锛涒懀 `tsc` + `esbuild` + `vsce package` 浜у嚭 `.vsix`銆?
```bash
gradle :feature:lsp:buildVscodeExtension
```

浜х墿锛?
```
feature/clients/vscode/dsl-analyzer-lsp-<version>.vsix
```

`.vsix` 鑷寘鍚?server jar锛坄server/dsl-analyzer-lsp.jar`锛夛紝瀹夎鍚庢棤闇€鍐嶉厤缃?`server.path` 鍗冲彲浣跨敤銆?
> 璇ヤ换鍔?*涓?*鎺ュ叆榛樿 `build`锛堥伩鍏嶆瘡娆℃瀯寤洪兘瑕佹眰 Node/npm锛夈€傞渶瑕佹椂鏄惧紡鎵ц銆?
### 浠呯紪璇戯紙涓嶆墦鍖?.vsix锛?
寮€鍙戞湡澧為噺缂栬瘧 + watch锛?
```bash
cd feature/clients/vscode
npm install
npm run compile     # 涓€娆℃€э細tsc 绫诲瀷妫€鏌?+ esbuild 鎵撳寘鍒?out/extension.js
npm run watch       # esbuild watch
```

鎸?F5 鍦?`clients/vscode` 鐩綍鎵撳紑鐨?VS Code 瀹炰緥閲岃皟璇曪紙闇€ `.vscode/launch.json`锛屽彲鎸?vscode-languageclient 鏂囨。鑷娣诲姞锛夈€?
---

## 3. 瀹夎 .vsix

**鍛戒护琛屽畨瑁咃細**

```bash
code --install-extension feature/clients/vscode/dsl-analyzer-lsp-<version>.vsix
```

**GUI 瀹夎锛?* VS Code 鈫?鎵╁睍闈㈡澘 鈫?鈰?鈫?"浠?VSIX 瀹夎鈥? 鈫?閫夋嫨 `.vsix`銆?
瀹夎鍚?reload 绐楀彛锛坄Ctrl+Shift+P` 鈫?`Developer: Reload Window`锛夈€?
---

## 4. 閰嶇疆

鎵撳紑 `settings.json`锛坄Ctrl+Shift+P` 鈫?`Preferences: Open Settings (JSON)`锛夈€?
### 4.1 server 璺緞

`.vsix` 宸插唴缃?server jar锛?*榛樿鏃犻渶閰嶇疆**銆傚闇€鎸囧悜鑷鏋勫缓/鐗堟湰鐨?jar锛岃鐩栵細

```json
{
  "dsl-analyzer-lsp.server.path": "C:/path/to/dsl-analyzer-lsp.jar",
  "dsl-analyzer-lsp.server.javaPath": "java"
}
```

| 璁剧疆 | 榛樿 | 璇存槑 |
|---|---|---|
| `dsl-analyzer-lsp.server.path` | `""` | server jar 缁濆璺緞銆傜暀绌烘椂鍥為€€鍒版墿灞曞唴缃?`server/dsl-analyzer-lsp.jar`锛涢兘涓嶅瓨鍦ㄥ垯鎵╁睍涓嶆縺娲诲苟鍛婅銆?|
| `dsl-analyzer-lsp.server.javaPath` | `"java"` | 鍚姩 server 鐢ㄧ殑 Java 17+ 鍙墽琛屾枃浠躲€?|

### 4.2 鏂囦欢鍖归厤

鎵╁睍鍙负 DSL 鏂囦欢婵€娲伙紙涓?IntelliJ 鎻掍欢鏂囦欢绫诲瀷涓€鑷达級锛?
- `**/script.xml`
- `**/script_*.xml`

涓?`language` 涓?`xml`锛圴S Code 鍐呯疆 XML TextMate 璇硶鎻愪緵缁撴瀯楂樹寒锛泂erver 鍐嶅彔鍔?`textDocument/semanticTokens` 楂樹寒宓屽叆琛ㄨ揪寮忥紝瑙?`feature/lsp/docs/IMPLEMENTATION.md`锛夈€?
### 4.3 妫€鏌ラ厤缃紙InspectionConfig锛?
`dsl-analyzer.config` 瀵硅薄鍦?`initialize` 鏃朵綔涓?`initializationOptions` 浼犵粰 server锛屽苟鍦?`workspace/didChangeConfiguration` 鏃剁儹閲嶈浇锛坰erver 绔嬪嵆閲嶅寘瑁呰鍒欏簱骞堕噸鍒嗘瀽鎵€鏈夋墦寮€鐨勬枃妗ｏ級銆?
```json
{
  "dsl-analyzer.config": {
    "rootElementNames": ["Lockscreen", "Widget"],
    "enabledRuleIds": ["SEM-TYPE-001"],
    "disabledRuleIds": ["SYN-003"],
    "severityOverrides": { "SEM-REQ-001": "warning" }
  }
}
```

| 瀛楁 | 璇存槑 |
|---|---|
| `rootElementNames` | 瑕嗙洊琚涓?DSL 鏍瑰厓绱犵殑鏍囩闆嗗悎锛堝奖鍝嶆枃浠惰瘑鍒級銆?|
| `enabledRuleIds` | 闈炵┖鏃朵粎杩欎簺瑙勫垯鐢熸晥銆備笌 `disabledRuleIds` 浜掓枼銆?|
| `disabledRuleIds` | 鎶戝埗杩欎簺瑙勫垯銆?|
| `severityOverrides` | `ruleId -> "error" \| "warning" \| "info"`锛岃鐩栬鍒欓粯璁や弗閲嶇骇銆?|

淇敼鍚庝繚瀛?`settings.json` 鍗宠Е鍙戠儹閲嶈浇锛屾棤闇€閲嶅惎銆?
> 涔熷彲鐢?`--config <path>` 鏂囦欢鏂瑰紡鍚姩 server锛圕LI 鍦烘櫙锛夛紝JSON 褰㈢姸鐩稿悓锛岃 `feature/lsp/README.md`銆?
---

## 5. 鎻愪緵鐨勮瑷€鐗规€?
| LSP 鏂规硶 | VS Code 琛ㄧ幇 |
|---|---|
| `textDocument/publishDiagnostics` | "闂"闈㈡澘涓庣紪杈戝櫒鍐呮尝娴嚎銆?|
| `textDocument/completion` | 琛ュ叏鍒楄〃锛氬厓绱犲悕锛坄Class` 鍥炬爣锛宒etail=category锛夈€佸睘鎬у悕锛坄Field`/`Property`锛岄€変腑鎻掑叆 `attr=""` 骞舵妸鍏夋爣鏀惧紩鍙峰唴锛夈€乪num 灞炴€у€硷紙`EnumMember`锛夈€傝ˉ鍏ㄩ」鎼哄甫 `documentation`锛坢arkdown锛夛紝閫変腑鏃跺彸渚ф枃妗ｉ潰鏉挎樉绀恒€?|
| `textDocument/hover` | hover 鍏冪礌鍚?灞炴€у悕/灞炴€у€兼椂鏄剧ず markdown 鏂囨。锛坈ategory / required / optional / allowed parents / inherits锛屾垨灞炴€?type / default / enum / aliases / expression锛夈€?|
| `textDocument/codeAction` | 鍏夋爣鍦ㄨ瘖鏂笂鏃舵彁渚?QuickFix锛堥渶 server 绔敞鍐?`FixActionGenerator`锛岃 `feature/lsp/docs/IMPLEMENTATION.md` 搂codeAction锛夈€?|
| `textDocument/semanticTokens` | 鍏ㄦ枃妗ｈ涔夐珮浜細鏍囩鍚?灞炴€у悕/娉ㄩ噴/澹版槑 + 宓屽叆琛ㄨ揪寮忓彉閲?鍑芥暟/瀛楅潰閲忋€傛爣鍑?token 绫诲瀷锛孷S Code 鑷姩鏄犲皠涓婚鑹层€?|

> server 绔兘鍔涘０鏄庤 `DslLanguageServer.initialize`锛坄feature/lsp/src/main/java/.../DslLanguageServer.java`锛夈€?
---

## 6. 鎺掗敊

- **鎵╁睍鏈縺娲?/ 鎻愮ず "no bundled server jar"**锛氱敤 `gradle :feature:lsp:buildVscodeExtension` 閲嶆柊鏋勫缓锛堝惈鍐呯疆 jar锛夛紝鎴栬缃?`dsl-analyzer-lsp.server.path` 鎸囧悜鏈夋晥 jar銆?- **璇婃柇涓嶅嚭鐜?*锛氱‘璁ゆ枃浠跺悕涓?`script.xml`/`script_*.xml` 涓旀牴鏍囩鏄?DSL 鏍瑰厓绱狅紙`Lockscreen`/`Widget`/`Wallpaper`/`LongTake`/`ChargingSkin`锛夈€俿erver 瀵归潪 DSL 鏂囦欢娓呯┖璇婃柇銆?- **hover/琛ュ叏鏂囨。鏃犳牸寮?*锛歴erver 浜у嚭 markdown锛沄S Code 鍘熺敓娓叉煋銆傝嫢鏄剧ず鍘熷 `###`/`**`锛岀‘璁?server jar 鏄渶鏂版瀯寤猴紙`buildLspFatJar` / `buildVscodeExtension` 宸查噸璺戯級銆?- **server 鏃ュ織**锛氭墿灞曠敤 `java -jar ... --stdio` 鍚姩 server 瀛愯繘绋嬶紱server stderr 涓嶇洿鎺ュ彲瑙併€傚彲鍦?`DslLspServerService`锛圛ntelliJ 绔級绛変环璺緞鍔犳棩蹇楋紝鎴栦复鏃剁敤 `--inspect` 璋冭瘯銆?- **杈撳嚭闈㈡澘**锛歏S Code "杈撳嚭" 鈫?閫?"DSL Analyzer" 鏌ョ湅瀹㈡埛绔晶鏃ュ織锛堝鏈厤缃彲鎸?`vscode-languageclient` 鏂囨。寮€鍚級銆?
---

## 7. 鐩綍缁撴瀯

```
feature/clients/vscode/
鈹溾攢鈹€ .gitignore              # 蹇界暐 node_modules/ out/ server/ *.vsix
鈹溾攢鈹€ .vscodeignore           # vsce 鎵撳寘鎺掗櫎椤癸紙src/銆?.ts銆乻ourcemap 绛夛級
鈹溾攢鈹€ package.json            # 鎵╁睍娓呭崟銆佷緷璧栥€乶pm scripts銆乧ontributes.configuration
鈹溾攢鈹€ package-lock.json       # 閿佸畾渚濊禆
鈹溾攢鈹€ tsconfig.json
鈹溾攢鈹€ src/extension.ts        # 瀹㈡埛绔細鍚姩 server銆佽浆鍙戦厤缃€乨ocumentSelector
鈹斺攢鈹€ server/                 # 锛堟瀯寤轰骇鐗╋紝gitignored锛塨uildVscodeExtension 澶嶅埗鐨?fat jar
```

鏋勫缓浜х墿锛?
- `out/extension.js` 鈥?esbuild 鎵撳寘鐨勫鎴风鍏ュ彛銆?- `dsl-analyzer-lsp-<version>.vsix` 鈥?鍙畨瑁呯殑鎵╁睍鍖呫€?
---

## 8. 涓庡叾瀹冪紪杈戝櫒

server 鏄€氱敤 LSP锛屽叾瀹冪紪杈戝櫒閰嶇疆瑙?`feature/lsp/README.md`锛圢eovim / coc.nvim / Helix锛夈€?