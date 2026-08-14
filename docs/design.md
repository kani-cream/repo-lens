# Repo Lens — IntelliJ IDEA Plugin 設計書

> 文書バージョン: 0.3 (Draft)  
> 作成日: 2026-08-14  
> 更新日: 2026-08-14  
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
- Repo Lens 本体には単一の「対応言語」制限を設けず、Analyzer ごとの capability と Language Provider によって対応範囲を決める。

### 1.1 成功指標

| 指標 | MVP目標 |
|---|---|
| レビュー候補確認までの操作 | Tool Window → Scope選択 → Analyze |
| 結果からコード移動 | ダブルクリックまたは Enter で対象位置へ移動 |
| AI向けコピー | 1件または複数選択から1アクションで Markdown 生成 |
| 外部通信 | 0件 |
| 通常編集への影響 | 長時間 PSI read lock で EDT をブロックしない |
| 言語非依存性 | Provider がない言語でも Universal Analyzer が利用できる |

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
| Graceful Degradation | 利用できない Analyzer があっても Repo Lens 全体を停止しない |
| Capability over Supported Language | 製品単位ではなく Analyzer 単位で利用可否を示す |
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
| Plugin ID | `com.kanicream.repolens` |
| Kotlin package root | `com.kanicream.repolens` |
| IDE baseline | **IntelliJ IDEA 2026.1 に固定** |
| 互換性確認 | IntelliJ IDEA 2026.2 を Plugin Verifier + 実機で確認 |
| UI | Tool Window + Action System |
| コード解析 | PSI / UAST / Language Provider |
| VCS | IntelliJ Platform VCS API |
| 設定保存 | PersistentStateComponent 等の標準機構 |

2026.1 を初期 baseline とし、v0.1 開発中に 2026.2 固有 API へ安易に引き上げない。2026.2 は互換性確認対象とし、2026.1 を維持できない技術的理由が発生した場合のみ baseline 変更を設計判断として明示する。

### 4.1 言語サポートの基本方針

Repo Lens 自体に「この言語だけが対応」という固定リストを持たせない。

対応可否は **Analyzer capability** として扱う。言語固有 AST を必要としない Analyzer は広く動作させ、構造解析は対象言語の Language Provider が利用可能な場合のみ追加する。

したがって、次の二層構造とする。

#### Universal Analyzer

IntelliJ がテキストファイルとして扱えるプロジェクトで、可能な限り言語非依存に動作する。

主な対象:

- Large File
- TODO / FIXME
- Local Changes
- Branch Diff
- Large Diff
- Git history metrics
- Hotspot の言語非依存部分

#### Language Provider Analyzer

クラス、関数、メソッド、引数、ネスト、参照、依存関係などの構造情報を扱う。

対象言語の PSI / UAST / language API と Repo Lens 側の Provider の両方が利用可能な場合に実行する。

Language Provider がない、または必要な language plugin が利用できない場合でも、Repo Lens 本体は起動・解析可能とする。利用可能な Analyzer のみ実行し、利用不可の Analyzer は理由付きで表示する。

### 4.2 言語サポート階層

| Tier | 対象 | 解析 | 予定 |
|---|---|---|---|
| Tier 0 | IntelliJ が扱えるテキスト言語 | Large File、TODO/FIXME、Git系 | v0.1〜 |
| Tier 1 | Java / Kotlin | UAST による Class / Method / Parameter / Nesting | v0.1 |
| Tier 2 | Go | Go Provider による Function / Method / Parameter / Nesting | v0.4 第一優先 |
| Tier 2 | JavaScript / TypeScript | JS/TS Provider による Class / Function / Method / Parameter / Nesting | v0.4 |
| Tier 3 | Python / Scala / Groovy / その他 | Provider または利用可能 API に応じて追加 | v1.x候補 |

### 4.3 Capability Matrix

製品 UI / ドキュメントでは「対応言語一覧」よりも capability matrix を優先して表示する。

例:

| Language | Universal | Structure | Reference | Dependency |
|---|---|---|---|---|
| Java | Yes | Yes | Yes / phased | Yes / phased |
| Kotlin | Yes | Yes | Yes / phased | Yes / phased |
| Go | Yes | v0.4 | Phased | Phased |
| JavaScript | Yes | v0.4 | Limited / phased | Limited / phased |
| TypeScript | Yes | v0.4 | Limited / phased | Limited / phased |
| Other text language | Yes | Provider dependent | Provider dependent | Provider dependent |

この表の `No` / `Unavailable` は Repo Lens 全体が利用できないことを意味しない。該当 Analyzer のみ非対応であることを示す。

### 4.4 Go 対応方針

Go は v0.4 の最初の Language Provider 実装対象とする。

最低限の構造解析対象:

- Large Function
- Large Method
- Too Many Parameters
- Deep Nesting

Go には Java の class と同一概念を前提としない。`Function` / receiver `Method` / package など Go の構造に合わせた Finding を生成する。

追加候補:

- import / package dependency evidence
- Unused Candidate の Go 対応
- Circular Dependency の package 情報連携

追加候補は v0.2 Analyzer の再利用性と利用可能 API の安定性を確認して段階的に実装する。

Go Provider の重要な役割は、Java/Kotlin 向け UAST 実装に依存しすぎず、Language Provider SPI が本当に他言語へ拡張可能かを検証することである。そのため **Go Provider を JS/TS Provider より先に実装・検証する**。

### 4.5 Language Provider 設計原則

- 言語固有 Analyzer は Provider として分離する。
- Core / UI は特定言語 plugin の型へ直接依存しない。
- Provider の `availability` と Analyzer の `supports()` を分離して扱えるようにする。
- Provider がない環境でも class loading error 等で Repo Lens 本体が停止しない構造にする。
- 言語差を無理に共通 AST へ押し込まない。
- Provider 固有情報は `Finding.metadata` で拡張する。
- Navigation / Copy / Filter は共通 Finding モデルを通して統一する。

### 4.6 Language Provider の Optional Dependency

Go / JavaScript / TypeScript などの言語プラグインは **Repo Lens Core の必須依存にしない**。

言語固有 API を利用する Provider は IntelliJ Platform の optional dependency 機構を利用し、依存言語プラグインが存在するときだけ Provider を有効化する。

設計要件:

- Repo Lens Core は Go / JS / TS プラグインが未導入でもロードできる。
- 言語固有クラスへの参照は optional descriptor / adapter 側へ隔離する。
- Provider の class loading が Core 起動経路へ漏れない。
- Provider unavailable は正常状態として扱う。
- Provider の有無は `LanguageCapabilityService` から確認できる。

概念構造:

```text
Repo Lens Core
├─ Universal Analyzers
├─ Java/Kotlin UAST
├─ optional: Go Provider
│    └─ Go language plugin
└─ optional: JS/TS Provider
     └─ JavaScript / TypeScript language plugin
```

### 4.7 対応 IDE と対応言語の分離

「Repo Lens が動作する IDE」と「特定 Language Provider が利用できる IDE」を同一概念として扱わない。

- **Core compatibility**: Repo Lens Core をロード・実行できる IntelliJ Platform build。
- **Provider compatibility**: 対象 IDE で必要な language plugin と Public API が利用可能であり、その Provider をロードできる状態。

ドキュメントや UI では、単純な「対応言語一覧」だけでなく、IDE / Provider / Analyzer capability の組み合わせを示す。

Provider 非対応 IDE でも Universal Analyzer が利用可能であれば、Repo Lens 自体は利用可能とする。

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
AnalyzerRegistry
  ├─ 利用可能 Analyzer を実行
  └─ 利用不可 Analyzer は理由を保持
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
| UC-06 | Provider 未対応言語を解析 | Universal Analyzer の結果を取得し、構造解析不可理由を確認できる |
| UC-07 | Go を解析 | Go Provider の Finding と Universal Finding を同一 UI で確認できる |

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

### 8.4 Capability 表示

v0.4 以降は、Settings または Tool Window から現在の環境で利用可能な Analyzer を確認できるようにする。

例:

```text
Language Capabilities

Java        Universal ✓  Structure ✓
Kotlin      Universal ✓  Structure ✓
Go          Universal ✓  Structure ✓
Python      Universal ✓  Structure — Provider unavailable
```

利用不可をエラーとして赤表示するのではなく、通常の capability 情報として扱う。

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
fun processPayment(...) {
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
  - LanguageCapabilityService
     ↓
Domain Model
  - Finding
  - SourceLocation
  - AnalysisRequest
  - SettingsSnapshot
     ↓
Analyzer / Provider Layer
  - Universal Analyzers
  - UAST Provider
  - Go Provider
  - JS/TS Provider
     ↓
Platform Adapters
  - PSI / UAST
  - Language APIs
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

Analyzer は **Finding を生成することだけを責務**とする。UI component、Clipboard、Navigation、Notification、Tool Window の状態へ直接依存してはならない。

Analyzer が必要とする IntelliJ API は `AnalysisContext` または adapter を通して受け取り、UI 操作を副作用として実行しない。

#### LanguageProvider

v0.4 で明確な SPI として定義する。

概念例:

```kotlin
interface LanguageProvider {
    val id: String
    fun availability(project: Project): ProviderAvailability
    fun analyzers(): List<RepoLensAnalyzer>
    fun capabilities(): Set<LanguageCapability>
}
```

実際の API 形状は実装時に調整してよいが、Provider の availability と Analyzer の supports 判定を core から扱えることを必須とする。

#### AnalyzerRegistry

利用可能な Analyzer を登録し、言語・Scope・Indexing 状態・Provider availability に応じて実行対象を決定する。

#### LanguageCapabilityService

現在の IDE / project で利用可能な Provider と Analyzer capability を集約する。

#### FindingNavigator

Finding の SourceLocation / Symbol 情報から Editor を開き、可能な場合 range を選択する。

#### MarkdownAiFormatter

Finding を外部 AI へ渡しやすい Markdown へ変換する。AI Provider 固有処理は持たない。

### 10.2 Analyzer の責務境界

各層の責務を次のように固定する。

| 層 | 責務 | 禁止事項 |
|---|---|---|
| Analyzer | コードを解析し `Finding` を返す | UI更新、Clipboard書き込み、Editor移動 |
| Orchestrator | Scope / Analyzer実行 / Cancellation / 集約 | 言語固有UIの実装 |
| Navigator | FindingからEditorへ移動 | Finding判定ロジック |
| Formatter | FindingをMarkdown等へ整形 | コード解析、外部送信 |
| UI | 結果表示・ユーザー操作 | PSI解析ロジックの直接実装 |
| Provider | 言語固有APIをAnalyzerへ接続 | Core/UIへの言語固有型の漏洩 |

この境界は v0.1 から守る。後続バージョンで利便性のために Analyzer から UI や Clipboard を直接操作する実装を追加しない。

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
- 一つの Language Provider の失敗で他 Provider / Universal Analyzer を停止しない。

### 13.2 キャッシュ

MVP では過剰なキャッシュを避ける。

必要になった場合は以下を key 候補とする。

- File modification stamp
- Analyzer ID
- Settings hash
- Provider version / capability hash

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
| Language Providers | availability / capability の表示、Provider 単位設定 |

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
| Provider unavailable | Universal Analyzer は実行し、対象構造 Analyzer は理由付きで skip |
| Unsupported syntax | 該当 Analyzer のみ安全に skip |
| Language plugin 未導入 | Repo Lens 本体は起動し、該当 Provider を unavailable と表示 |
| ファイル変更中 | 安全に再取得または再解析 |
| Git/VCS未設定 | Local Changes を無効化し理由表示 |
| Clipboard失敗 | Notification 表示、Finding は保持 |
| 解析キャンセル | 取得済み結果を Partial として保持可能 |

### 15.1 ログ

通常ログへソースコード本文を出力しない。

記録対象は以下程度に限定する。

- Analyzer ID
- Provider ID
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

Language Provider を追加してもこの原則を変更しない。

---

## 17. 将来 Analyzer / Provider

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

### v0.4

Language Provider architecture を確立する。

実装優先順:

1. **Go Provider**
2. JavaScript / TypeScript Provider

Go Provider では最低限 Large Function / Method、Too Many Parameters、Deep Nesting を提供する。

JS/TS Provider でも同等の構造チェックを可能な範囲で提供する。

Python 等は v0.4 完了後の候補とする。

---

## 18. テスト戦略

### 18.1 Unit / Fixture

| Layer | Test |
|---|---|
| Tier 0 Analyzer | 改行形式、巨大ファイル、TODO marker、exclude |
| UAST Analyzer | Java/Kotlin class/method/params/nesting fixture |
| Go Provider | function/method/params/nesting fixture、Provider有無 |
| JS/TS Provider | function/class/params/nesting、JSX/TSX、Provider有無 |
| Capability | Provider availability、Analyzer supports、fallback |
| Formatter | 単一/複数Finding、escape、truncation、line number |
| Scope Resolver | Project/Module/Local Changes |
| Dedup | stable ID / 重複排除 |

### 18.2 Integration

- IntelliJ Platform test framework を使用する。
- PSI / Navigation を fixture で検証する。
- Dumb Mode / Indexing 中の挙動をテストする。
- Plugin Verifier で対象 IDE build との互換性を確認する。
- 大規模 fixture で responsiveness を確認する。
- Provider 未導入環境で Repo Lens 本体が起動することを確認する。
- Java/Kotlin/Go/JS/TS 混在 project で Findings 統合を確認する。

### 18.3 実 Repository Smoke Test

fixture だけでリリース完了としない。各主要マイルストーンで実 Repository を使った smoke test を必須とする。

#### v0.1

中規模以上の実 Repository を最低1つ使用し、次の縦フローを確認する。

```text
Projectを開く
  ↓
Analyze
  ↓
Findingを確認
  ↓
対象コードへNavigation
  ↓
複数Findingを選択
  ↓
Copy for AI
```

確認項目:

- Finding のノイズ量が実用範囲か
- Project / Local Changes の解析時間
- Navigation の正確性
- Copy for AI の情報量
- Editor 操作への影響

#### v0.4

Go の実 Repository を最低1つ使用し、Go Provider を追加しても Core / UI の変更が局所的であることを確認する。

Go Provider checkpoint:

- Universal Analyzer と Go Analyzer が同一 UI に統合される。
- Go Provider がない環境で Core が起動する。
- Go Provider の有無で Core API を分岐実装していない。
- Navigation / Copy for AI が Java/Kotlin と同じ操作で使える。

JS/TS Provider 追加時も同じ観点で混在 Repository を確認する。

### 18.4 品質ゲート

- Compile / Test 成功
- Plugin Verifier 重大エラーなし
- Tool Window / Analyze / Navigate / Copy の smoke test 成功
- 解析中も Editor typing を阻害しない
- ネットワーク通信が発生しない
- Provider 不在が Repo Lens 全体の起動失敗につながらない
- 対象マイルストーンの実 Repository smoke test が完了している

---

## 19. リリース計画

| Version | 内容 |
|---|---|
| v0.1 | Tool Window / Scope / 6 checks / Navigation / Multi-select Copy / Settings |
| v0.2 | Unused Candidate / Circular Dependency / ignore/suppress |
| v0.3 | Branch Diff / Large Diff / Hotspot / Git history |
| v0.4 | **Go を第一優先とする Language Provider、続いて JS/TS Provider、capability UI** |
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

### 19.2 v0.4 実装順序

1. Language Provider SPI / Capability Model / Optional Dependency skeleton
2. Go Provider skeleton
3. Go Function / Method / Parameter / Nesting Analyzer
4. Go Navigation / Copy / mixed result integration
5. Go 実 Repository smoke test / Provider architecture checkpoint
6. JS/TS Provider
7. JS/TS structure Analyzer
8. mixed-language smoke test

---

## 20. パッケージ構成案

```text
com.kanicream.repolens
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
├─ language/
│  ├─ LanguageProvider.kt
│  ├─ LanguageCapabilityService.kt
│  ├─ go/
│  │  ├─ GoLanguageProvider.kt
│  │  └─ analyzer/
│  └─ jsts/
│     ├─ JsTsLanguageProvider.kt
│     └─ analyzer/
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

`language/` 配下は v0.4 で導入する。v0.1 時点では UAST Analyzer を既存 `analysis/uast/` に置いてよいが、Provider SPI 導入時に必要に応じて整理する。

依存方向:

```text
UI / Actions
    ↓
Application Services
    ↓
Domain Model / Analyzer SPI
    ↓
Language Providers / Platform Adapters
```

禁止事項:

- Analyzer → UI component への直接依存
- Analyzer → Clipboard / Navigation への直接依存
- Domain Model → IntelliJ Swing UI への依存
- Core → Go / JS / TS 固有 PSI 型への直接依存
- Provider 不在時に class loading error を発生させる強制依存

---

## 21. Definition of Done

### v0.1

- [ ] IntelliJ IDEA 上で Repo Lens Tool Window を開ける
- [ ] Plugin ID / package root が `com.kanicream.repolens` で統一されている
- [ ] IntelliJ IDEA 2026.1 baseline で build / test が通る
- [ ] IntelliJ IDEA 2026.2 互換性を Plugin Verifier + smoke test で確認する
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
- [ ] Analyzer が UI / Clipboard / Navigation に直接依存していない
- [ ] Plugin Verifier と主要テストが通過する
- [ ] 実 Repository smoke test を完了する

### Language architecture

- [ ] Repo Lens 本体に単一の対応言語制限を設けない
- [ ] Universal Analyzer は Provider 非対応言語でも動作可能とする
- [ ] Analyzer ごとに capability を判定できる
- [ ] Provider unavailable を正常状態として扱える
- [ ] 言語固有 Provider は optional dependency として分離できる

### v0.4追加条件

- [ ] Go Provider が optional dependency として実装される
- [ ] Go の Function / Method / Parameter / Nesting を解析できる
- [ ] Go Provider を追加しても Core / UI の大規模変更を必要としない
- [ ] Go の実 Repository smoke test を完了する
- [ ] JS/TS Provider が optional dependency として実装される
- [ ] Capability をユーザーが確認できる
- [ ] Provider がない環境でも Repo Lens 本体が起動する

---

## 22. 未決事項

| ID | 論点 | 暫定判断 |
|---|---|---|
| OD-01 | Large Class/Method の行数定義 | コメント/空行を含む物理行数で開始 |
| OD-02 | Deep Nesting 対象構文 | if/when/loop/try/lambda 等。fixtureで確定 |
| OD-03 | Test sources の既定扱い | 解析対象。将来別閾値を検討 |
| OD-04 | Local Changes の未追跡ファイル | 含める方向 |
| OD-06 | Plugin名 | **Repo Lens で確定**。Marketplace公開時に表示名重複のみ確認 |
| OD-07 | Go の reference/dependency capability | v0.4実装時に利用可能 API とノイズ量を確認して確定 |

Plugin ID / package namespace、IDE baseline、Language Provider の optional dependency 方針、Analyzer の責務境界、実 Repository smoke test は本書 v0.3 で決定済みとし、未決事項として扱わない。

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

Language Provider は v0.1 の縦ワークフローが安定した後に v0.4 で導入する。ただし `RepoLensAnalyzer.supports()` や AnalyzerRegistry は、将来の Provider 追加を妨げない形で v0.1 から設計する。

---

## 24. 実装前に固定する設計判断

実装開始後に変更すると構造変更の影響が大きい事項として、以下6点を事前決定とする。

### 24.1 Language Provider は Optional Dependency とする

Go / JS / TS などの Language Provider は Core の必須依存にしない。対象 language plugin が存在しない IDE でも Repo Lens Core と Universal Analyzer は起動・利用可能であることを必須とする。

### 24.2 対応 IDE と対応言語を分離する

Core compatibility と Provider compatibility を別々に管理する。「IntelliJ が言語を扱える = Repo Lens の全 Analyzer が利用できる」とは定義しない。利用可否は Analyzer capability として表示する。

### 24.3 Plugin ID / package namespace を固定する

- Plugin display name: `Repo Lens`
- Repository: `repo-lens`
- Plugin ID: `com.kanicream.repolens`
- Kotlin package root: `com.kanicream.repolens`

実装開始後は互換性上の明確な理由がない限り変更しない。

### 24.4 IDE baseline を固定する

初期 baseline は **IntelliJ IDEA 2026.1** とする。2026.2 は compatibility target とし、Plugin Verifier と実機 smoke test で確認する。

### 24.5 Analyzer の責務境界を固定する

Analyzer は `Finding` を返す解析ロジックに限定する。UI、Clipboard、Navigation、Notification を直接操作しない。これらは Application Service / Adapter / UI 層へ分離する。

### 24.6 実 Repository Smoke Test を必須化する

fixture / unit test のみでマイルストーン完了としない。

- v0.1: 中規模以上の実 Repository で Analyze → Finding → Navigation → Copy for AI を確認する。
- v0.4 Go: Go の実 Repository で Provider architecture と同一UXを確認する。
- JS/TS Provider: 混在 Repository で Universal / Language Finding の統合を確認する。

これら6点は実装前の基盤仕様とし、機能閾値や Severity の細部より優先して維持する。

---

## 25. 参考資料

実装時は IntelliJ Platform の Public API を優先し、Experimental / Internal / Scheduled for Removal API の採用は必要性を明示して判断する。

- [Developing a Plugin | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
- [Plugin Dependencies | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)
- [Tool Window | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/tool-window.html)
- [Code Inspections | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/code-inspections.html)
- [PSI Elements | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/psi-elements.html)
- [UAST | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/uast.html)
- [Indexing and PSI Stubs | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html)
- [Threading Model | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
- [Action System | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/action-system.html)
- [Incompatible Changes in IntelliJ Platform and Plugins API 2026.*](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)
