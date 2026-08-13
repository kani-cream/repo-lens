# Repo Lens — IntelliJ IDEA Plugin 設計書

> 文書バージョン: 0.1 (Draft)  
> 作成日: 2026-08-14  
> 対象: IntelliJ IDEA Plugin  
> リポジトリ: `repo-lens`  
> 基本方針: 実行時AI/API課金なし・ローカル完結

## 0. 文書概要

Repo Lens は IntelliJ IDEA の既存 Inspection を置き換えるものではない。コードレビュー時に確認したい複数の観点を一つの Tool Window に集約し、検出結果から該当コードへ即座に移動し、必要な証拠・コード断片を AI に渡しやすい Markdown としてコピーできる「レビュー操作レイヤー」を提供する。

製品の中心価値は、静的解析そのものよりも次の一連の操作を短縮することにある。

1. レビュー候補を検出する。
2. 一覧で優先度を確認する。
3. 対象ファイル・行・シンボルへジャンプする。
4. 必要な Finding を選択する。
5. ファイルパス、行番号、メソッド名、理由、測定値、コード断片をまとめてコピーする。
6. ChatGPT / Claude / Gemini / 社内AI / ローカルAIなど、任意の外部ツールへ貼り付ける。

プラグイン自身は AI API を呼び出さず、API キーも保持しない。

---

## 1. 背景と目的

IntelliJ IDEA は Inspection、Problems、TODO、依存関係解析、VCS など強力な機能を既に備えている。一方、コードレビュー時には「巨大メソッド」「深いネスト」「TODO/FIXME」「変更量」など複数観点の結果が別々の UI や操作に分散する。

さらに、検出した内容を AI に確認させたい場合、ファイルパス・行番号・シンボル・理由・コード断片を手作業で集める必要がある。

Repo Lens の目的は以下とする。

- レビューで優先的に確認すべき候補を一つの Tool Window に集約する。
- 検出結果から対象コードへ 1 アクションで移動できるようにする。
- 単一または複数の Finding を AI に貼り付けやすい Markdown としてコピーできるようにする。
- 完成プラグインは外部 AI API、サーバー、有料外部サービスを必須としない。
- IntelliJ Platform の PSI / UAST / VCS / Action System を活用し、独自パーサーの再実装を最小化する。

### 1.1 成功指標

| 指標 | MVP目標 |
|---|---|
| レビュー候補確認までの操作 | Tool Window → Scope選択 → Analyze |
| 結果からコード移動 | ダブルクリックまたは Enter で対象位置へ移動 |
| AI向けコピー | 1件または複数選択から1アクションで Markdown 生成 |
| 外部通信 | 0件 |
| 通常編集への影響 | 長時間 PSI read lock で EDT をブロックしない |

---

## 2. 製品コンセプト

Repo Lens は「静的解析ツール」ではなく **Code Review Navigator** と位置付ける。

解析結果を「悪いコード」と断定せず、レビュー優先度を決めるための Evidence として提示する。

### 2.1 設計原則

| 原則 | 内容 |
|---|---|
| Local First | 解析・集約・整形は IDE 内で完結する |
| Leverage IntelliJ | PSI/UAST、参照解決、VCS、Navigation、Action System を再利用する |
| Evidence over Judgment | 行数、ネスト、引数数など観測値を提示し断定を避ける |
| Explicit Export | 外部へ渡す情報はユーザーの Copy 操作時のみ生成する |
| Provider Architecture | 言語非依存解析と言語別解析を分離する |
| Progressive Delivery | MVP を小さくし、高度な解析は後続バージョンへ分離する |

---

## 3. スコープ

### 3.1 MVP

#### Scope

- Current File
- Selected Files
- Module
- Project
- Local Changes

#### Checks

- Large File
- Large Class
- Large Method
- Too Many Parameters
- Deep Nesting
- TODO / FIXME

#### UI / 操作

- Findings 一覧
- Severity / Check / Scope フィルタ
- テキスト検索
- 対象ファイル・行・シンボルへの Navigation
- 単一 / 複数選択
- Copy Result
- Copy with Code
- Copy for AI
- プロジェクト単位の閾値・除外設定

### 3.2 非スコープ

MVP では以下を実装しない。

- LLM/API の直接呼び出し
- AI チャット UI
- API キー管理
- 自動修正
- リファクタリング自動適用
- CI/CD サーバー
- クラウドダッシュボード
- チーム共有
- SAST / 脆弱性スキャナーの代替
- 全言語で同等精度の AST 解析

---

## 4. 技術方針

| 項目 | 設計値 |
|---|---|
| 実装言語 | Kotlin |
| ビルド | Gradle + IntelliJ Platform Gradle Plugin 2.x |
| IDE baseline | IntelliJ IDEA 2026.1 を第一候補 |
| UI | Tool Window + Action System |
| コード解析 | PSI / UAST |
| VCS | IntelliJ Platform VCS API |
| 設定保存 | PersistentStateComponent 等の標準機構 |

2026.2 互換性は Plugin Verifier と実機で検証する。2026.2 固有 API が必須となる場合のみ baseline 引き上げを検討する。

### 4.1 言語サポート階層

| Tier | 対象 | 解析 |
|---|---|---|
| Tier 0 | 全テキスト言語 | Large File、TODO/FIXME、VCS変更量など |
| Tier 1 | Java / Kotlin | UASTによるClass/Method/Parameter/Nesting |
| Tier 2 | JavaScript / TypeScript | 将来 Provider で追加 |
| Tier 2 | Go その他 | 公開 API が安定して利用できる場合に追加 |

言語固有の Analyzer は Provider として分離し、MVP のコアが特定言語プラグインへ強く依存しない構造にする。

---

## 5. ユーザーワークフロー

### 5.1 基本フロー

```text
Project
  ↓
Repo Lens Tool Window
  ↓
Scope を選択
  ↓
Analyze
  ↓
Findings 一覧
  ↓
対象を確認
  ├─ ダブルクリック → Editorへ移動
  ├─ Copy Result
  ├─ Copy with Code
  └─ Copy for AI
          ↓
    ChatGPT / Claude / Gemini / その他
```

### 5.2 主要ユースケース

| ID | ユースケース | 完了条件 |
|---|---|---|
| UC-01 | 現在の変更だけレビュー候補を抽出 | Local Changes の Findings が一覧化される |
| UC-02 | 巨大メソッドからコードへ移動 | Finding 選択で対象メソッドを Editor に表示 |
| UC-03 | 複数 Finding を AI に確認させる | 選択項目が1つの Markdown として Clipboard へ出力 |
| UC-04 | TODO/FIXME を棚卸し | TODO/FIXME だけフィルタ・検索・コピー可能 |
| UC-05 | 閾値を変更 | Settings 変更後の再解析へ反映 |

---

## 6. Finding モデル

Finding は「問題」ではなく「レビュー候補」を表す。

```kotlin
data class Finding(
    val id: String,
    val analyzerId: String,
    val severity: Severity,
    val title: String,
    val message: String,
    val location: SourceLocation,
    val symbol: SymbolInfo?,
    val measuredValue: Double?,
    val threshold: Double?,
    val confidence: Confidence?,
    val metadata: Map<String, String>
)
```

```kotlin
data class SourceLocation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startOffset: Int?,
    val endOffset: Int?
)
```

### 6.1 Finding に保持する情報

- Analyzer ID
- Severity
- Check 名
- File path
- 開始 / 終了行
- 可能な場合 Symbol 名
- measuredValue
- threshold
- 判定理由
- 追加 metadata
- 必要に応じ confidence

同一 Check・同一 File・同一 Range の重複は安定 ID を使って排除する。

---

## 7. MVP Analyzer

| Check ID | 名称 | Tier | 測定値 | 初期閾値 | Severity |
|---|---|---:|---|---:|---|
| RL-F001 | Large File | 0 | 物理行数 | 800 | Warning |
| RL-C001 | Large Class | 1 | Class body行数 | 500 | Warning |
| RL-M001 | Large Method | 1 | Method body行数 | 80 | Warning |
| RL-M002 | Too Many Parameters | 1 | Parameter数 | 7 | Info / Warning |
| RL-M003 | Deep Nesting | 1 | 最大ネスト深度 | 5 | Warning |
| RL-T001 | TODO / FIXME | 0 | Marker存在 | - | Info |

初期閾値は暫定値とし Settings から変更可能にする。

### 7.1 Large File

PSI が利用できない言語でも動作させる。対象 VirtualFile / Document の物理行数を測定する。

### 7.2 Large Class / Large Method

Java / Kotlin は UAST を中心にクラス・メソッドの range を取得し行数を算出する。

行数定義は MVP ではコメント・空行を含む物理行数とする。Logical Lines は将来機能とする。

### 7.3 Too Many Parameters

UAST から引数数を取得する。Constructor / Method を対象とする。生成コード等は除外ルールを優先する。

### 7.4 Deep Nesting

if / when / loop / try / lambda 等を対象候補とする。最終的な構文定義は Java/Kotlin fixture を用いて確定する。

### 7.5 TODO / FIXME

MVP 既定 Marker:

- TODO
- FIXME

将来的に HACK / XXX などを設定から追加可能にする。

---

## 8. Tool Window UI

### 8.1 構成

```text
Repo Lens
┌──────────────────────────────────────────────┐
│ Scope: [Local Changes ▼] [Analyze] [Stop]   │
├──────────────────────────────────────────────┤
│ Search [________________] Severity / Check   │
├──────────────────────────────────────────────┤
│ Total 18 | Warning 6 | Info 12              │
├──────────────────────────────────────────────┤
│ Severity | Check | File | Symbol | Location │
│ Warning  | Method| ...  | ...    | 182-327  │
│ Info     | TODO  | ...  | ...    | 84       │
├──────────────────────────────────────────────┤
│ Detail                                       │
│ File / Symbol / Range / Metric / Reason      │
│ Code preview                                 │
│ [Copy] [Copy with Code] [Copy for AI]        │
└──────────────────────────────────────────────┘
```

### 8.2 一覧列

- Severity
- Check
- File
- Symbol
- Location
- Value / Threshold

### 8.3 操作

| 操作 | 挙動 |
|---|---|
| Single click | Detail Pane 更新 |
| Double click | 対象コードへ Navigation |
| Enter | 対象コードへ Navigation |
| Multi select | 複数 Finding を対象に Copy |
| Analyze | 現在 Scope で再解析 |
| Stop | Cancellation |

結果の選択状態は可能な範囲で保持する。

---

## 9. Copy for AI

Repo Lens の主要差別化機能とする。

AI を組み込まず、AI がレビューしやすい構造化コンテキストを生成する。

### 9.1 単一 Finding

```markdown
## Repo Lens Finding

- Issue: Large Method
- Severity: Warning
- File: `src/payment/PaymentService.kt`
- Symbol: `PaymentService.processPayment()`
- Location: `182-327`
- Method lines: 146
- Threshold: 80

### Reason

This method exceeds the configured method length threshold.

### Code

```kotlin
async fun processPayment(...) {
    // ...
}
```

Please review whether this finding represents a meaningful design or maintainability concern.
```

### 9.2 複数 Finding

```markdown
# Repo Lens Review Context

Project: payment-api
Scope: Local Changes
Findings: 3

## 1. Large Method
...

## 2. Deep Nesting
...

## 3. TODO / FIXME
...
```

### 9.3 Copy 設定

- Context lines
- Max code lines
- Absolute / Project-relative path
- Code block の有無
- measuredValue / threshold の有無
- Copy template

### 9.4 セーフガード

巨大なメソッド・ファイルを丸ごとコピーしない。

`maxCodeLines` を超えた場合はコード断片を truncate し、以下を明示する。

```text
... omitted 84 lines ...
```

外部サービスへの送信は Repo Lens の責務外とする。Clipboard へ出力した後、どこへ貼り付けるかはユーザーが決定する。

---

## 10. アーキテクチャ

```text
UI / Actions
     ↓
Application Services
  - AnalysisOrchestrator
  - FindingNavigator
  - ClipboardFormatter
     ↓
Domain Model
  - Finding
  - SourceLocation
  - AnalysisRequest
  - SettingsSnapshot
     ↓
Platform Adapters
  - PSI / UAST
  - VCS
  - Clipboard
  - IntelliJ Navigation
```

### 10.1 主要コンポーネント

#### AnalysisOrchestrator

- Scope 解決
- Analyzer 選択
- Cancellation 管理
- Finding 集約
- Dedup
- UI への結果通知

#### RepoLensAnalyzer

```kotlin
interface RepoLensAnalyzer {
    val id: String
    fun supports(context: AnalysisContext): Boolean
    suspend fun analyze(context: AnalysisContext): List<Finding>
}
```

Analyzer は UI component に直接依存しない。

#### AnalyzerRegistry

利用可能な Analyzer を登録し、言語・Scope・Indexing 状態に応じて実行対象を決定する。

#### FindingNavigator

Finding の SourceLocation / Symbol 情報から Editor を開き、可能な場合 range を選択する。

#### MarkdownAiFormatter

Finding を外部 AI へ渡しやすい Markdown へ変換する。AI Provider 固有処理は持たない。

---

## 11. Scope 解決

| Scope | 対象 | MVP |
|---|---|---|
| Current File | 現在 Editor で開いているファイル | 必須 |
| Selected Files | Project View 等で選択した対象 | 必須 |
| Module | Module 配下 | 必須 |
| Project | Project content root 配下 | 必須 |
| Local Changes | VCS 変更中ファイル | 必須 |
| Branch Diff | Base branch との差分 | v0.3 |

### 11.1 Local Changes

レビュー用途では Project 全体より Local Changes を重要 Scope とする。

既存の巨大ファイルが毎回表示されるのではなく、「今回の変更によってレビューすべき箇所」を優先できるようにする。

未追跡ファイルを Local Changes に含める方向とし、実装時に IntelliJ VCS API の挙動を確認する。

---

## 12. 除外ルール

既定除外候補:

- `.git`
- `build`
- `out`
- `node_modules`
- Libraries
- SDK
- binary
- generated sources

ユーザー定義 glob / regex をプロジェクト設定で追加可能にする。

解析から除外された理由を確認できるようにし、黙って結果が欠落しているように見せない。

---

## 13. 実行・性能・Indexing

### 13.1 基本原則

- EDT で重い解析を実行しない。
- PSI 参照は IntelliJ Platform の read action 規約に従う。
- 長時間 read lock を保持しない。
- ファイル単位または Analyzer 単位で処理を分割する。
- Cancellation をサポートする。
- Dumb Mode では Index 依存 Analyzer を待機・制限する。

### 13.2 キャッシュ

MVP では過剰なキャッシュを避ける。

必要になった場合は以下を key 候補とする。

- File modification stamp
- Analyzer ID
- Settings hash

Finding 本文やコード断片を恒久キャッシュしない。

### 13.3 UI 更新

可能であれば Analyzer / File 単位で結果を段階表示する。

MVP で複雑になる場合は解析完了単位で更新してよいが、Progress と Cancellation は必須とする。

---

## 14. 設定

### 14.1 設定項目

| Category | 内容 |
|---|---|
| Checks | Analyzer ごとの有効 / 無効 |
| Thresholds | file lines / class lines / method lines / params / nesting |
| Scope | test / generated の扱い |
| Exclude | glob / regex |
| TODO markers | TODO / FIXME / HACK 等 |
| Copy | context lines / max code lines / path形式 / template |

### 14.2 永続化

- プロジェクト固有設定は IntelliJ 標準機構を利用する。
- Finding 結果は原則永続化しない。
- プロジェクト再オープン後は必要に応じ再解析する。
- 将来 ignore/suppress を実装する場合は stable Finding key または Rule + path/symbol pattern として保存する。

---

## 15. エラー処理

| 事象 | 挙動 |
|---|---|
| Indexing中 | 実行可能な Analyzer のみ動作し、待機/skip理由を表示 |
| PSI取得失敗 | 対象ファイルを skip し Analysis Problems へ集約 |
| Unsupported language | Tier 0 のみ実行しエラー扱いしない |
| ファイル変更中 | 安全に再取得または再解析 |
| Git/VCS未設定 | Local Changes を無効化し理由表示 |
| Clipboard失敗 | Notification 表示、Finding は保持 |
| 解析キャンセル | 取得済み結果を Partial として保持可能 |

### 15.1 ログ

通常ログへソースコード本文を出力しない。

記録対象は以下程度に限定する。

- Analyzer ID
- File path
- Elapsed time
- Exception type

---

## 16. セキュリティ / プライバシー

MVP の重要要件:

- ネットワーク通信なし
- テレメトリなし
- APIキーなし
- ソースコードの自動外部送信なし

| 要件 | 仕様 |
|---|---|
| Network | HTTP / Socket 通信を実装しない |
| Telemetry | 収集しない |
| Secrets | API key / token を保存しない |
| Clipboard | 明示的な Copy 操作時のみ出力 |
| Source logging | コード本文をログへ出さない |
| Persistence | Finding本文 / Code snippet を原則ディスク永続化しない |

---

## 17. 将来 Analyzer

### v0.2

#### Unused Candidate

IntelliJ の参照解決・Inspection を可能な範囲で再利用する。

`Unused` と断定せず `Unused Candidate` とする。

Reflection、Dependency Injection、Framework、Serialization、外部 entry point 等による利用を完全には検出できないため、Finding にはその制約を明示する。

#### Circular Dependency

package / module / import graph を構築し、Strongly Connected Components を用いて循環を検出する。

粒度は package / module から開始し、class-level は必要性を見て追加する。

### v0.3

- Branch Diff
- Large Diff
- Git history
- Change frequency
- Author count
- Long-lived TODO
- Hotspot

Hotspot は「変更頻度が高い + 構造的な Finding が多い」ファイルをレビュー優先候補として可視化する方向を検討する。

---

## 18. テスト戦略

### 18.1 Unit / Fixture

| Layer | Test |
|---|---|
| Tier 0 Analyzer | 改行形式、巨大ファイル、TODO marker、exclude |
| UAST Analyzer | Java/Kotlin class/method/params/nesting fixture |
| Formatter | 単一/複数Finding、escape、truncation、line number |
| Scope Resolver | Project/Module/Local Changes |
| Dedup | stable ID / 重複排除 |

### 18.2 Integration

- IntelliJ Platform test framework を使用する。
- PSI / Navigation を fixture で検証する。
- Dumb Mode / Indexing 中の挙動をテストする。
- Plugin Verifier で対象 IDE build との互換性を確認する。
- 大規模 fixture で responsiveness を確認する。

### 18.3 品質ゲート

- Compile / Test 成功
- Plugin Verifier 重大エラーなし
- Tool Window / Analyze / Navigate / Copy の smoke test 成功
- 解析中も Editor typing を阻害しない
- ネットワーク通信が発生しない

---

## 19. リリース計画

| Version | 内容 |
|---|---|
| v0.1 | Tool Window / Scope / 6 checks / Navigation / Multi-select Copy / Settings |
| v0.2 | Unused Candidate / Circular Dependency / ignore/suppress |
| v0.3 | Branch Diff / Large Diff / Hotspot / Git history |
| v0.4 | JS/TS 等 Language Analyzer Provider |
| v1.0 | 安定性、互換性、ドキュメント、Marketplace公開判断 |

### 19.1 v0.1 実装順序

1. Plugin skeleton + Tool Window + Settings skeleton
2. Finding / SourceLocation / Analyzer SPI / Orchestrator
3. Current File + Project Scope
4. Tier 0: Large File + TODO/FIXME
5. UAST: Large Class / Large Method / Parameters / Nesting
6. Findings Table + Detail + Navigation
7. Multi-select + Copy Result / Copy with Code / Copy for AI
8. Local Changes Scope
9. Cancellation / Dumb Mode / performance 調整
10. Plugin Verifier + 実 Repository smoke test

---

## 20. パッケージ構成案

```text
com.repolens.intellij
├─ action/
│  ├─ AnalyzeAction.kt
│  └─ CopyFindingAction.kt
├─ analysis/
│  ├─ AnalysisOrchestrator.kt
│  ├─ AnalyzerRegistry.kt
│  ├─ RepoLensAnalyzer.kt
│  ├─ tier0/
│  │  ├─ LargeFileAnalyzer.kt
│  │  └─ TodoMarkerAnalyzer.kt
│  └─ uast/
│     ├─ LargeClassAnalyzer.kt
│     ├─ LargeMethodAnalyzer.kt
│     ├─ ParameterCountAnalyzer.kt
│     └─ NestingAnalyzer.kt
├─ model/
│  ├─ Finding.kt
│  ├─ SourceLocation.kt
│  └─ AnalysisRequest.kt
├─ scope/
│  ├─ ScopeProvider.kt
│  ├─ ProjectScopeProvider.kt
│  └─ LocalChangesScopeProvider.kt
├─ ui/
│  ├─ RepoLensToolWindowFactory.kt
│  ├─ RepoLensPanel.kt
│  └─ FindingTableModel.kt
├─ navigation/
│  └─ FindingNavigator.kt
├─ clipboard/
│  ├─ ClipboardFormatter.kt
│  └─ MarkdownAiFormatter.kt
├─ vcs/
│  └─ VcsFacade.kt
└─ settings/
   ├─ RepoLensSettings.kt
   └─ RepoLensConfigurable.kt
```

依存方向:

```text
UI / Actions
    ↓
Application Services
    ↓
Domain Model
    ↓
Platform Adapters
```

禁止事項:

- Analyzer → UI component への直接依存
- Domain Model → IntelliJ Swing UI への依存

---

## 21. Definition of Done

- [ ] IntelliJ IDEA 上で Repo Lens Tool Window を開ける
- [ ] Current File / Project / Local Changes が動作する
- [ ] 6つの MVP Check が設定閾値に従って Finding を生成する
- [ ] Finding に file path / line / symbol / reason / metric が含まれる
- [ ] Finding から対象コードへ Navigation できる
- [ ] 複数 Finding を 1つの Markdown として Copy for AI できる
- [ ] Code snippet の上限 / truncation が反映される
- [ ] Indexing 中の制限が明示される
- [ ] EDT を長時間ブロックしない
- [ ] プラグイン自身によるネットワーク通信が存在しない
- [ ] 設定がプロジェクトごとに保持される
- [ ] Plugin Verifier と主要テストが通過する

---

## 22. 未決事項

| ID | 論点 | 暫定判断 |
|---|---|---|
| OD-01 | Large Class/Method の行数定義 | コメント/空行を含む物理行数で開始 |
| OD-02 | Deep Nesting 対象構文 | if/when/loop/try/lambda 等。fixtureで確定 |
| OD-03 | Test sources の既定扱い | 解析対象。将来別閾値を検討 |
| OD-04 | Local Changes の未追跡ファイル | 含める方向 |
| OD-05 | 2026.1 baseline の2026.2互換 | Plugin Verifier 結果で判断 |
| OD-06 | Plugin名 | **Repo Lens で確定**。Marketplace公開時に表示名重複のみ確認 |

---

## 23. 最初の実装マイルストーン

最初から v0.1 全体を実装しない。

Claude Code 等へ最初に渡す実装範囲は次に限定する。

1. IntelliJ Plugin skeleton
2. Repo Lens Tool Window
3. Finding / SourceLocation
4. Analyzer SPI / AnalysisOrchestrator の最小構造
5. Project Scope
6. Large File Analyzer
7. TODO/FIXME Analyzer
8. Findings 一覧
9. ダブルクリック Navigation
10. Copy for AI

この時点で以下の縦のワークフローを完成させる。

```text
Tool Window
  ↓
Project Scope を Analyze
  ↓
Large File / TODO を検出
  ↓
Finding 一覧
  ↓
対象コードへ Navigation
  ↓
Copy for AI
```

縦のワークフローが実 Repository で実用になることを確認してから UAST 系 Analyzer を追加する。

---

## 24. 参考資料

実装時は IntelliJ Platform の Public API を優先し、Experimental / Internal / Scheduled for Removal API の採用は必要性を明示して判断する。

- [Developing a Plugin | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
- [Tool Window | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/tool-window.html)
- [Code Inspections | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/code-inspections.html)
- [PSI Elements | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/psi-elements.html)
- [UAST | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/uast.html)
- [Indexing and PSI Stubs | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html)
- [Threading Model | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
- [Action System | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/action-system.html)
- [Incompatible Changes in IntelliJ Platform and Plugins API 2026.*](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)
